package io.github.oatelauser.springplus.web.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static io.github.oatelauser.springplus.web.response.ServerStatus.SERVER_INTERNAL_CODE;
import static io.github.oatelauser.springplus.web.response.ServerStatus.SERVER_INTERNAL_MSG;

/**
 * 默认的统一异常配置类（v2.0 改造，设计文档 11）。
 * <p>
 * v2.0 把原来散落的两个布尔开关（{@code logClientStackTrace} / {@code logBusinessStackTrace}）
 * 收口成结构化的 {@link Log} 配置，并新增 {@link Sse} 配置。日志决策由
 * {@code DefaultExceptionLogger} 统一读取。
 *
 * <pre>
 * spring:
 *   web:
 *     error-response:
 *       code: C0100
 *       msg: 系统内部错误
 *       show-error: true
 *       log:
 *         print-stack-for-unknown: true   # 兜底 Exception 是否打堆栈
 *         protocol-tag: true              # 日志是否带 protocol=HTTP_SSE 标签
 *       sse:
 *         default-event-name: app-error   # SSE 默认错误事件名
 * </pre>
 *
 * @author <a href="mailto:545896770@qq.com">DearYang</a>
 * @date 2023-04-07
 * @since 1.0
 */
@Data
@ConfigurationProperties(prefix = "spring-plus.web.error-response")
public class GlobalExceptionProperties {

    /**
     * 默认错误码
     */
    private String code = SERVER_INTERNAL_CODE;

    /**
     * 默认错误信息
     */
    private String msg = SERVER_INTERNAL_MSG;

    /**
     * 是否显示详细错误信息（生产环境建议关闭）
     */
    private boolean showError = true;

    /**
     * 日志相关配置（v2.0 新增）。
     */
    private Log log = new Log();

    /**
     * SSE 协议相关配置（v2.0 新增）。
     */
    private Sse sse = new Sse();

    /**
     * 日志策略配置（设计文档 11）。
     * <p>
     * 这些是 {@code DefaultExceptionLogger} 在静态分类表之上应用的<b>全局开关</b>，
     * 优先级低于 {@code ErrorDescriptor.logPolicy} 和 {@code ServiceException.shouldRecordStack()}。
     */
    @Data
    public static class Log {

        /**
         * 兜底 {@link Exception} 是否打堆栈，默认 true。
         * <p>
         * 未知异常必须留堆栈排障，除非显式关闭。
         */
        private boolean printStackForUnknown = true;

        /**
         * 日志中是否带协议标签（如 {@code protocol=HTTP_SSE}），默认 true。
         * <p>
         * 便于在多协议接口混合时快速筛出 SSE 接口的异常。
         */
        private boolean protocolTag = true;
    }

    /**
     * SSE 协议配置（设计文档 11）。
     */
    @Data
    public static class Sse {

        /**
         * SSE 默认错误事件名，默认 {@code app-error}。
         * <p>
         * 当 {@code SseErrorHint} 的 event 为 null 时由 {@code SseExceptionProcessor} 采用本值。
         * 强烈建议不要叫 {@code error}（见 {@code @SseExceptionResponse} Javadoc）。
         */
        private String defaultEventName = "app-error";
    }

}
