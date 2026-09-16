package io.github.oatelauser.springplus.web.error.engine;

import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import io.github.oatelauser.springplus.web.error.descriptor.ExceptionContext;
import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;
import io.github.oatelauser.springplus.web.error.mapper.ExceptionMapperChain;
import io.github.oatelauser.springplus.web.error.mapper.ExceptionMapperContext;
import io.github.oatelauser.springplus.web.error.output.ExceptionOutputProcessor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.github.oatelauser.springplus.web.response.SystemStatus.INTERNAL_ERROR;

/**
 * 异常渲染主引擎（v3.0，设计文档 2.1、5.1、4.4）。
 * <p>
 * 这是统一错误处理的中枢——所有异常分类、协议探测、日志、渲染都在这里收口。{@code @ExceptionHandler}
 * 方法不再自己打日志、不再自己包 {@code ResponseEntity}，全部 {@code engine.dispatch(...)} 一行搞定。
 * （v3.0 更名自 {@code ExceptionOutputFactory}：它是引擎不是工厂——工厂造对象，这里做调度。）
 *
 * <h3>{@link #dispatch} 主流程</h3>
 * <pre>
 *   1. 协议探测：OutputProtocolResolver.resolve(hm, req)  ← O(1) 查表
 *   2. ErrorDescriptor 解析（按优先级）：
 *        P0  方法/类级 @ExceptionResponse 家族注解（HandlerExceptionAnnotationProcessor）
 *        P1  调用方传入的 defaultDescriptor（@ExceptionHandler 自己构造的）
 *        P2  ExceptionMapper 链（异常类注解 / ServerStatus / 兜底）
 *        P3  硬兜底 INTERNAL_ERROR（理论上不会走到，DefaultExceptionMapper 已兜底）
 *   3. 日志：ExceptionLogger.log(...)
 *   4. 已提交检查：response.isCommitted() 时查渲染器 handlesCommitted() 能力位——
 *        支持（NDJSON）→ 仍派发，在已发出的流上补写一行错误记录；
 *        不支持（JSON / SSE）→ 只 ERROR 日志不渲染（SSE 流内异常由 B/C 档处理）
 *   5. 取渲染器：requireProcessor(protocol)——未注册的协议降级 JSON 渲染并 ERROR 落盘
 *   6. 渲染：processor[protocol].handle(descriptor, ctx)
 * </pre>
 *
 * <h3>两套入口</h3>
 * <ul>
 *   <li>{@link #dispatch}：具体 {@code @ExceptionHandler} 命中时调用，传入自己构造的 defaultDescriptor（P1）。</li>
 *   <li>{@link #dispatchFallback}：兜底 {@code @ExceptionHandler(Exception.class)} 调用，无 defaultDescriptor。</li>
 * </ul>
 *
 * <h3>SSE B/C 档支持</h3>
 * <p>
 * {@link #resolve} / {@link #log} / {@link #buildContext} 是给 {@code SseExceptionEmitter}（B/C 档）
 * 复用的：在异步线程里解析描述 + 打日志，再用 {@code SseExceptionProcessor.sendErrorEvent} 写到已有 emitter。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public class ExceptionOutputEngine {

    private static final Logger LOG = LoggerFactory.getLogger(ExceptionOutputEngine.class);

    private final OutputProtocolResolver protocolResolver;
    private final HandlerExceptionAnnotationProcessor ruleScanner;
    private final ExceptionMapperChain mapperChain;
    private final ExceptionLogger logger;
    /**
     * 协议 → 渲染器，启动期一次性装好（每个协议一个实现）。
     */
    private final Map<OutputProtocol, ExceptionOutputProcessor> outputs;

    public ExceptionOutputEngine(OutputProtocolResolver protocolResolver,
            HandlerExceptionAnnotationProcessor ruleScanner,
            ExceptionMapperChain mapperChain, ExceptionLogger logger,
            List<ExceptionOutputProcessor> processorList) {
        this.logger = logger;
        this.mapperChain = mapperChain;
        this.protocolResolver = protocolResolver;
        this.ruleScanner = ruleScanner;
        this.outputs = processorList.stream().collect(Collectors
                .toUnmodifiableMap(ExceptionOutputProcessor::protocol, Function.identity()));
    }

    /**
     * 主入口：具体 {@code @ExceptionHandler} 命中后调用。
     *
     * @param ex                触发的异常
     * @param defaultDescriptor handler 自己构造的描述（作为 P1 兜底）；可为 null
     * @param request           当前请求
     * @param response          当前响应（用于已提交检查）
     * @return 渲染产物（交给 Spring MVC 写出）；已提交时返回 null
     */
    public Object dispatch(Throwable ex, @Nullable ErrorDescriptor defaultDescriptor,
            HttpServletRequest request, HttpServletResponse response) {
        HandlerMethod handlerMethod = resolveHandlerMethod(request);
        OutputProtocol protocol = this.protocolResolver.resolve(handlerMethod, request);

        ErrorDescriptor descriptor = this.resolve(ex, defaultDescriptor, request, handlerMethod, protocol);
        this.log(descriptor, request, handlerMethod, protocol);

        if (isCommitted(response)) {
            // 已提交 ≠ 全部放弃：支持「已提交补写」的流式协议（NDJSON）仍交给渲染器追加协议内错误记录，
            // 客户端逐行解析时能感知失败；不支持的协议（JSON/SSE）维持只记日志不渲染。
            ExceptionOutputProcessor committedCapable = this.outputs.get(protocol);
            if (committedCapable != null && committedCapable.handlesCommitted()) {
                ExceptionContext committedCtx = new ExceptionContext(descriptor, request, response, handlerMethod, protocol);
                return committedCapable.handle(descriptor, committedCtx);
            }
            LOG.error("响应已提交，无法渲染错误（SSE 流内异常请用 SseExceptionEmitter/SseConnection 处理）", ex);
            return null;
        }
        // 把 response 传入 ctx：SSE 渲染器必须直接写 servlet 输出流（绕开 Spring 返回值处理器）。
        ExceptionContext ctx = new ExceptionContext(descriptor, request, response, handlerMethod, protocol);
        return this.requireProcessor(protocol).handle(descriptor, ctx);
    }

    /**
     * 兜底入口：{@code @ExceptionHandler(Exception.class)} 调用，无 defaultDescriptor（跳过 P1）。
     */
    public Object dispatchFallback(Throwable ex, HttpServletRequest request, HttpServletResponse response) {
        return this.dispatch(ex, null, request, response);
    }

    /**
     * 解析 {@link ErrorDescriptor}（SSE B/C 档复用，不打日志、不渲染）。
     * <p>
     * 按优先级 P0 → P1 → P2 → P3（硬兜底 INTERNAL_ERROR）。
     *
     * @param protocol 已知协议（B/C 档固定 HTTP_SSE），传 null 则按 hm/req 探测
     */
    public ErrorDescriptor resolve(Throwable ex, @Nullable ErrorDescriptor defaultDescriptor,
            @Nullable HttpServletRequest request, @Nullable HandlerMethod handlerMethod, @Nullable OutputProtocol protocol) {
        OutputProtocol resolvedProtocol = protocol != null ? protocol : this.protocolResolver.resolve(handlerMethod, request);

        // P0：方法/类级注解（接口级业务覆盖，最高优先级）。
        ErrorDescriptor descriptor = this.ruleScanner.resolve(ex, handlerMethod, resolvedProtocol);
        // P1：调用方传入的 defaultDescriptor（@ExceptionHandler 自己构造的）。
        if (descriptor == null && defaultDescriptor != null) {
            descriptor = defaultDescriptor;
        }
        // P2：Mapper 链（异常类注解 / ServerStatus / 兜底）。
        if (descriptor == null) {
            ExceptionMapperContext mapperCtx = new ExceptionMapperContext(request, handlerMethod, resolvedProtocol);
            descriptor = this.mapperChain.map(ex, mapperCtx);
        }
        // P3：硬兜底（理论上 DefaultExceptionMapper 已保证非空，这里防御性兜底）。
        if (descriptor == null) {
            descriptor = ErrorDescriptor.of(INTERNAL_ERROR, ex).build();
        }
        return descriptor;
    }

    /**
     * 打日志（SSE B/C 档在异步线程里调用，确保流内异常也落盘）。
     */
    public void log(ErrorDescriptor descriptor, @Nullable HttpServletRequest request,
            @Nullable HandlerMethod handlerMethod, OutputProtocol protocol) {
        this.logger.log(descriptor, request, handlerMethod, protocol);
    }

    /**
     * 构造渲染上下文（SSE B/C 档调用 sendErrorEvent 时需要）。
     */
    public ExceptionContext buildContext(ErrorDescriptor descriptor, @Nullable HttpServletRequest request,
            @Nullable HandlerMethod handlerMethod, OutputProtocol protocol) {
        // SSE B/C 档（流内异步）拿不到 response，传 null；它们走 sendErrorEvent 写入业务自有 emitter。
        return new ExceptionContext(descriptor, request, null, handlerMethod, protocol);
    }

    /**
     * 取协议渲染器：未注册时 ERROR 落盘并降级 JSON 渲染（v2.1 边界加固）。
     * <p>
     * 正常装配下每个协议必有渲染器；走到降级说明「枚举新增了协议但渲染器没跟上」——
     * 宁可降级 JSON 保住统一错误格式，也不 NPE 抛回 Spring（那会变成无统一格式的 500 白板）。
     * 连 JSON 渲染器都缺失属于装配残缺，启动即失败更合适，这里快速失败。
     */
    private ExceptionOutputProcessor requireProcessor(OutputProtocol protocol) {
        ExceptionOutputProcessor processor = this.outputs.get(protocol);
        if (processor != null) {
            return processor;
        }
        LOG.error("协议 {} 未注册渲染器，降级为 JSON 渲染", protocol);
        ExceptionOutputProcessor fallback = this.outputs.get(OutputProtocol.HTTP_JSON);
        if (fallback == null) {
            throw new IllegalStateException("HTTP_JSON 渲染器缺失：统一异常处理装配不完整");
        }
        return fallback;
    }

    /**
     * 从请求属性取命中的 {@link HandlerMethod}（Spring MVC 在分发时存入
     * {@link HandlerMapping#BEST_MATCHING_HANDLER_ATTRIBUTE}）。取不到返回 null。
     */
    @Nullable
    public static HandlerMethod resolveHandlerMethod(HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        return handler instanceof HandlerMethod handlerMethod ? handlerMethod : null;
    }

    private static boolean isCommitted(@Nullable HttpServletResponse response) {
        return response != null && response.isCommitted();
    }

}
