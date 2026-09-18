package io.github.oatelauser.springplus.web.error.descriptor;

import io.github.oatelauser.springplus.web.error.output.ErrorHint;
import io.github.oatelauser.springplus.web.error.output.ExceptionBodyCustomizer;
import io.github.oatelauser.springplus.web.response.ServerStatus;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 协议无关 + 响应模型无关的「错误中间对象」（v3.0）。
 * <p>
 * 这是统一错误处理的核心抽象（设计文档第 4 章）。所有异常分类路径——
 * <ul>
 *   <li>方法/类/异常类级 {@code @ExceptionResponse} 家族注解（{@code HandlerExceptionAnnotationProcessor}）</li>
 *   <li>框架级 {@code @ExceptionHandler}（构造后作为 {@code defaultDescriptor} 传入引擎）</li>
 *   <li>可插拔 {@code ExceptionMapper} 链（含异常类注解 / ServerStatus / 兜底）</li>
 * </ul>
 * ——都产出本类型；由 {@code ExceptionOutputProcessor} 在最后一刻把它翻译为协议特定输出
 * （JSON 走 {@code ResponseEntity<SimpleResponse>}，SSE 走 {@code SseEmitter} 事件，
 * NDJSON 走单行错误记录）。
 *
 * <h3>v3.0：状态码升维为协议无关字段</h3>
 * <p>
 * v2.x 的真实 HTTP 状态码走 {@code JsonErrorHint.httpStatus}（协议元数据），导致 NDJSON
 * 复用 JSON hint、SSE 单设 {@code handshakeStatus} 的别扭局面。v3.0 认清「错误码想表达的
 * HTTP 状态」是<b>协议无关的意图</b>（对齐 Spring {@code ProblemDetail.status} 的设计），
 * 升为独立字段 {@link #statusIntent}；hint 密封家族只保留真正协议专属的渲染参数
 * （当前仅 SSE 的 event/retry → {@code SseErrorHint}）。
 *
 * <h3>使用方式（业务方）</h3>
 * <pre>{@code
 * // 在 @ExceptionHandler 内构造 descriptor，交给引擎 dispatch：
 * ErrorDescriptor descriptor = ErrorDescriptor.of(PARAMETER_VALIDATION_FAILED, ex)
 *         .message(message)
 *         .details(details)
 *         .build();
 * return engine.dispatch(ex, descriptor, request, response);
 * }</pre>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>不可变</b>：所有字段 {@code final}，通过 Lombok 自动生成 getter（标准 {@code getXxx} 风格）。</li>
 *   <li><b>归一化构造</b>：{@link ErrorDescriptorBuilder#build()} 在最终构造前做两项规范化：<br>
 *       1) {@code details} 为 null → 空 Map；非 null → 防御性拷贝 + 不可变包装；<br>
 *       2) {@code logPolicy} 为 null → 兜底 {@link LogStackPolicy#DEFAULT}。</li>
 *   <li><b>{@code toBuilder()}</b>：以当前对象为蓝本生成 Builder，便于「在已有描述基础上微调一个字段」
 *       的场景（如引擎兜底后强制覆盖 {@code logPolicy}）。</li>
 * </ul>
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @see ErrorHint 协议元数据 sealed family
 * @since 2.0
 */
@Getter
@Builder(toBuilder = true)
public class ErrorDescriptor {

    /**
     * 错误码（业务码体系：A0xxx 客户端 / B0xxx 业务 / C0xxx 系统）。
     */
    private final String code;

    /**
     * 用户可见错误文案（已做占位符替换 / showException 处理）。
     */
    private final String message;

    /**
     * 触发本次错误的异常对象（用于日志、bodyCustomizer 读取等）。
     */
    private final Throwable error;

    /**
     * 结构化补充信息（{@code @Valid} 字段错误清单、限流剩余配额等）。
     * <p>
     * 由 {@link ErrorDescriptorBuilder#build()} 做防御性拷贝 + 不可变包装；
     * 调用方拿到的 Map 是只读的，外部修改原始 Map 不影响本字段。
     */
    private final Map<String, Object> details;

    /**
     * 日志堆栈策略；null 输入会被归一为 {@link LogStackPolicy#DEFAULT}。
     */
    private final LogStackPolicy logPolicy;

    /**
     * 真实 HTTP 状态码意图（v3.0 新增，协议无关）。
     * <p>
     * 来源（按优先级）：
     * <ul>
     *   <li>{@code @ExceptionResponse#httpStatus()} / 派生注解同名桥接属性——启动期
     *       {@code AnnotationToTemplateConverter} 归一化（OK → null）固化进模板；</li>
     *   <li>{@code ServiceException.getResponseStatus()}——advice 层构造 descriptor 时携带；</li>
     * </ul>
     * 消费：各协议处理器在<b>允许的时机</b>读取——JSON 全程有效；NDJSON / SSE 仅
     * 「流未开始」有效（流已提交后状态码已发出）。各协议缺省 200（业务码承载错误语义）。
     * <p>
     * {@code null} 表示未指定，处理器按协议缺省处理——比用 {@link HttpStatus#OK} 哨兵更直白，
     * 「未指定」与「显式要求 200」在语义上有别（尽管渲染结果相同）。
     */
    @Nullable
    private final HttpStatus statusIntent;

    /**
     * 协议专属渲染参数（sealed family，v3.0 收窄为仅 SSE）。
     * <p>
     * 启动期由 {@code AnnotationToTemplateConverter} 的内置 hint 工厂表
     * （{@code HINT_FACTORIES}）从派生注解提取，固化进 {@code ErrorDescriptorTemplate}；
     * 运行期 {@code XxxExceptionProcessor} 用 {@code instanceof} 下钻取值。
     * <p>
     * {@code null} 表示业务方未指定协议专属参数（如普通 {@code @ExceptionResponse} 通吃所有协议），
     * 此时由各 processor 用本协议缺省兜底。
     */
    @Nullable
    private final ErrorHint hint;

    /**
     * 自定义响应体定制器实例（启动期已 getBean，v3.0 更名自 {@code outputBase}）。
     * <p>
     * null 表示走默认 {@code SimpleResponse} 响应体。
     */
    @Nullable
    private final ExceptionBodyCustomizer bodyCustomizer;

    // ─────────────────────────────────────────────────────────────
    // 便捷工厂：绝大多数场景只关心 code/message/exception 三个核心字段，
    // 其余渲染/日志元数据走 Builder 链式追加。
    // ─────────────────────────────────────────────────────────────

    /**
     * 以 (code, message, exception) 起手，返回 Builder 供链式补充 details/statusIntent 等。
     *
     * @param code    错误码（业务码体系）
     * @param message 用户可见错误文案
     * @param ex      触发本次错误的异常对象
     * @return 已填好 code/message/exception 的 Builder
     */
    public static ErrorDescriptorBuilder of(String code, String message, Throwable ex) {
        return builder().code(code).message(message).error(ex);
    }

    /**
     * 从 {@link ServerStatus} 取 code/message 起手。
     * <p>
     * 用于把现有的 {@code ClientStatus}/{@code BusinessStatus}/{@code SystemStatus} 枚举
     * 直接转成 {@code ErrorDescriptor}，是 {@code ServerStatusMapper} 的核心调用。
     *
     * @param status 服务状态码（自带 code + message）
     * @param ex     触发本次错误的异常对象
     * @return 已填好 code/message/exception 的 Builder
     */
    public static ErrorDescriptorBuilder of(ServerStatus status, Throwable ex) {
        return builder().code(status.getCode()).message(status.getMessage()).error(ex);
    }

    /**
     * 自定义 Builder，扩展 Lombok 生成的同名类。在 {@link #build()} 前做字段归一化。
     */
    public static class ErrorDescriptorBuilder {

        /**
         * 单条追加 details，便于 {@code @ExceptionHandler} 逐步塞字段错误清单。
         * <p>
         * 内部仍写到 Lombok 生成的 {@code details} 字段（这里用同名变量隐式访问），
         * 首次调用时若为空则懒分配一个 {@link LinkedHashMap}（保留顺序，前端展示稳定）。
         */
        public ErrorDescriptorBuilder detail(String key, Object value) {
            if (this.details == null) {
                this.details = new LinkedHashMap<>();
            }
            this.details.put(key, value);
            return this;
        }

        /**
         * 归一化构造：覆盖 Lombok 生成的 build()，在调用全字段构造器前规范化两个字段。
         */
        public ErrorDescriptor build() {
            // 1. details 归一：null → 空 Map；非 null → 防御性拷贝 + 不可变包装。
            //    用 LinkedHashMap 保留业务方放入顺序（如字段错误清单的展示顺序）。
            Map<String, Object> normalizedDetails = (this.details == null) ? Map.of()
                    : Map.copyOf(new LinkedHashMap<>(this.details));

            // 2. logPolicy 兜底：null → DEFAULT（跟随静态分类表）。
            LogStackPolicy normalizedPolicy = (this.logPolicy == null) ? LogStackPolicy.DEFAULT : this.logPolicy;

            // statusIntent / hint / bodyCustomizer 保持 null 语义（未指定），不归一化。
            return new ErrorDescriptor(this.code, this.message, this.error,
                    normalizedDetails, normalizedPolicy, this.statusIntent, this.hint, this.bodyCustomizer);
        }
    }
}
