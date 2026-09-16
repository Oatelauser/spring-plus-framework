package io.github.oatelauser.springplus.web.error.engine;

import io.github.oatelauser.springplus.web.error.annotation.ExceptionResponse;
import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;
import io.github.oatelauser.springplus.web.trace.HandlerMethodProcessor;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 协议探测缓存（v3.0，设计文档第 7 章）。
 * <p>
 * 把「这个接口是 JSON / SSE / NDJSON 哪种」的判定整体下沉到<b>启动期</b>：每个 {@link HandlerMethod}
 * 在注册时（{@link HandlerMethodProcessor} 回调）预解析一次协议，缓存进 {@link #protocolByMethod}；
 * 运行期异常处理时 {@link #resolve} 只做一次 {@code Map.get}，O(1)（设计 7.1 / 1.2）。
 *
 * <h3>启动期探测规则（按优先级，命中即停，设计 7.2）</h3>
 * <ol>
 *   <li><b>方法级派生注解协议提示唯一</b>：方法上贴的派生注解（家族反查，含业务自定义派生）
 *       锁定的协议一致 → 直接采用；锁出多个不同协议视为冲突，{@code log.warn} 后跳过本规则。
 *       （类级 / 异常类上的派生注解<b>不</b>参与协议提示，设计 7.2。）</li>
 *   <li><b>produces 含 {@code text/event-stream}</b>：从 {@link RequestMappingInfo#getProducesCondition()} 读。</li>
 *   <li><b>produces 含 {@code application/x-ndjson}</b>：同上（v2.1 新增，NDJSON 逐行流式接口）。</li>
 *   <li><b>返回值是 {@link SseEmitter} / {@code ResponseEntity<SseEmitter>}</b>：反射返回类型判定。</li>
 *   <li><b>兜底默认</b>：JSON。</li>
 * </ol>
 *
 * <h3>v3.0 改造：规则 1 家族泛化（Q7a）</h3>
 * <p>
 * v2.x 规则 1 用 {@code hasJson / hasSse / hasNdjson} 三个布尔硬编码枚举派生注解类型。
 * v3.0 改为 {@link ExceptionAnnotationUtils} 反查家族 + {@link ExceptionAnnotationUtils#boundProtocol}
 * 读锁死协议：业务自定义派生注解同样能提示协议，新协议派生注解零改动接入。
 *
 * <h3>运行期兜底（{@code HandlerMethod == null}）</h3>
 * <p>
 * Filter / HandlerMapping 阶段抛异常时拿不到 {@link HandlerMethod}，此时用 {@code Accept} 头判定，
 * 且必须<b>精确匹配</b> {@code text/event-stream} / {@code application/x-ndjson}（不能用
 * {@code isCompatibleWith}，否则 {@code *}/* 会误命中流式协议，设计 7.3）。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public class OutputProtocolResolver implements HandlerMethodProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(OutputProtocolResolver.class);

    /**
     * 启动期一次性算好，运行期 O(1) 查表。
     */
    private final Map<Method, OutputProtocol> protocolByMethod = new ConcurrentHashMap<>();

    @Override
    public boolean supports(Set<String> urls, HandlerMethod handlerMethod) {
        return true;
    }

    @Override
    public void handleMethod(Set<String> urls, RequestMappingInfo requestMappingInfo, HandlerMethod handlerMethod) {
        OutputProtocol protocol = this.detectAtStartup(handlerMethod, requestMappingInfo);
        this.protocolByMethod.put(handlerMethod.getMethod(), protocol);
    }

    /**
     * 运行期解析协议。
     *
     * @param handlerMethod 命中的 Controller 方法；可能为 null
     * @param request       当前请求（仅 hm==null 时用于 Accept 头兜底）
     * @return 探测出的协议（永不为 null）
     */
    public OutputProtocol resolve(@Nullable HandlerMethod handlerMethod, @Nullable HttpServletRequest request) {
        if (handlerMethod != null) {
            OutputProtocol cached = this.protocolByMethod.get(handlerMethod.getMethod());
            if (cached != null) {
                return cached;
            }
        }
        // hm == null（Filter / HandlerMapping 阶段）：用 Accept 头精确匹配兜底。
        return this.acceptFallback(request);
    }

    /**
     * 启动期探测一个 {@link HandlerMethod} 的协议。
     */
    private OutputProtocol detectAtStartup(HandlerMethod handlerMethod, @Nullable RequestMappingInfo info) {
        Method method = handlerMethod.getMethod();

        // 规则 1：方法级派生注解协议提示。
        OutputProtocol hint = this.derivedAnnotationHint(method);
        if (hint != null) {
            return hint;
        }

        // 规则 2：produces 含 text/event-stream → SSE；含 application/x-ndjson → NDJSON。
        if (info != null) {
            Set<MediaType> producible = info.getProducesCondition().getProducibleMediaTypes();
            if (producible.contains(MediaType.TEXT_EVENT_STREAM)) {
                return OutputProtocol.HTTP_SSE;
            }
            if (producible.contains(MediaType.APPLICATION_NDJSON)) {
                return OutputProtocol.NDJSON;
            }
        }

        // 规则 3：返回值是 SseEmitter / ResponseEntity<SseEmitter>。
        if (this.returnsSse(method)) {
            return OutputProtocol.HTTP_SSE;
        }

        // 规则 4：兜底 JSON。
        return OutputProtocol.HTTP_JSON;
    }

    /**
     * 规则 1：方法级派生注解协议提示（家族泛化）。
     * <p>
     * 遍历方法上的家族注解，普通 {@link ExceptionResponse}（protocols 空）不参与提示；
     * 派生注解经 {@link ExceptionAnnotationUtils#boundProtocol} 读锁死协议。多个派生注解锁出
     * <b>同一</b>协议（如两条 @SseExceptionResponse 对不同异常）不冲突；锁出<b>不同</b>协议视为
     * 冲突，警告并返回 null（落到后续规则）。
     */
    @Nullable
    private OutputProtocol derivedAnnotationHint(Method method) {
        OutputProtocol hinted = null;
        for (Annotation annotation : ExceptionAnnotationUtils.findFamilyAnnotations(method)) {
            if (annotation instanceof ExceptionResponse) {
                continue;
            }
            OutputProtocol bound = ExceptionAnnotationUtils.boundProtocol(annotation);
            if (bound == null) {
                continue;
            }
            if (hinted != null && hinted != bound) {
                LOG.warn("方法 {} 同时声明多个不同协议的派生注解，协议提示冲突，"
                        + "回退到 produces / 返回值类型自动探测", method);
                return null;
            }
            hinted = bound;
        }
        return hinted;
    }

    /**
     * 规则 3：返回值是否为 {@link SseEmitter} 或 {@code ResponseEntity<SseEmitter>}。
     */
    private boolean returnsSse(Method method) {
        ResolvableType returnType = ResolvableType.forMethodReturnType(method);
        Class<?> raw = returnType.resolve(Object.class);
        // 直接返回 SseEmitter。
        if (SseEmitter.class.isAssignableFrom(raw)) {
            return true;
        }
        // ResponseEntity<SseEmitter>：取第一个泛型参数判定。
        if (ResponseEntity.class.isAssignableFrom(raw)) {
            Class<?> generic = returnType.getGeneric(0).resolve(Object.class);
            return SseEmitter.class.isAssignableFrom(generic);
        }
        return false;
    }

    /**
     * 运行期兜底：Accept 头是否<b>精确</b>包含 {@code text/event-stream} / {@code application/x-ndjson}（设计 7.3）。
     * <p>
     * 不能用 {@code isCompatibleWith}——否则 {@code *}/* 会误命中流式协议。SSE 优先于 NDJSON 判定
     * （两者同时出现属调用方矛盾声明，取更特化的 SSE）。
     */
    private OutputProtocol acceptFallback(@Nullable HttpServletRequest request) {
        if (request == null) {
            return OutputProtocol.HTTP_JSON;
        }
        String accept = request.getHeader("Accept");
        if (accept == null) {
            return OutputProtocol.HTTP_JSON;
        }
        if (accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return OutputProtocol.HTTP_SSE;
        }
        if (accept.contains(MediaType.APPLICATION_NDJSON_VALUE)) {
            return OutputProtocol.NDJSON;
        }
        return OutputProtocol.HTTP_JSON;
    }

}
