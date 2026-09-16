package io.github.oatelauser.springplus.web.error.engine;

import io.github.oatelauser.springplus.web.autoconfigure.GlobalExceptionProperties;
import io.github.oatelauser.springplus.web.error.ServiceException;
import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import io.github.oatelauser.springplus.web.error.descriptor.LogStackPolicy;
import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;

/**
 * {@link ExceptionLogger} 的默认实现（v3.0，设计文档 5.3）。
 * <p>
 * 日志级别直接取 {@link LogPolicyTable}（客户端/业务 → WARN，系统 → ERROR）；
 * 「是否打堆栈」按<b>线性四级链</b>决策（v3.0 简化，先命中先停）：
 * <pre>
 *   1. descriptor.logPolicy 显式 ALWAYS / NEVER  → 直接拍板
 *   2. ServiceException.withStack()              → 强制打堆栈（业务方显式要求）
 *   3. 表兜底条目（未知异常）且 printStackForUnknown=false → 不打堆栈
 *   4. 按静态分类表（LogPolicyTable）结论
 * </pre>
 *
 * <h3>v3.0 简化：删掉「假线性」的两处覆写（Q17a）</h3>
 * <p>
 * v2.x 的决策链号称四级实为五输入的非对称覆写，两处被删：
 * <ul>
 *   <li><b>{@code isClientError(code.startsWith("A"))} 前缀判断</b>：让「错误码前缀」这个展示层
 *       约定反向入侵日志策略——典型故障形态：业务把 {@code TimeoutException} 映射成 A 码后，
 *       该异常在 ERROR 级别下<b>静默丢堆栈</b>，排查现场只剩一行文案。错误码前缀只该决定
 *       「给用户看什么」，不该决定「留不留证据」。</li>
 *   <li><b>{@code printStackForClient} 全局配置</b>：该配置只服务于上面那条前缀判断，判断删了
 *       配置自然蒸发（同步从 {@code GlobalExceptionProperties} 移除）。真想关某类异常的堆栈，
 *       正道是注解上写 {@code logPolicy = NEVER}——作用域精确到接口，而非按前缀一刀切。</li>
 * </ul>
 * 简化后每个输入只出现一次、每个开关方向唯一，决策链可以从上往下读通。
 *
 * <h3>为什么用 {@code ConditionalOnMissingBean}</h3>
 * <p>
 * 业务方注册自己的 {@code @Bean ExceptionLogger} 即可整体替换日志行为（如接入统一日志平台、
 * 脱敏、采样），框架默认实现自动让位。
 *
 * <h3>日志格式</h3>
 * <pre>
 *   [GET /api/user] UserController#create{} | protocol=HTTP_JSON | code=A0106 | msg=参数校验失败
 * </pre>
 * 打堆栈时异常对象作为最后一个参数传入（SLF4J 约定），确保完整栈落盘。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public class DefaultExceptionLogger implements ExceptionLogger {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultExceptionLogger.class);

    /**
     * 日志模板：[请求] Handler | protocol=... | code=... | msg=...（异常对象作为可选末尾参数）。
     */
    private static final String PATTERN = "[{}] {}{} | code={} | msg={}";

    private final GlobalExceptionProperties properties;

    public DefaultExceptionLogger(GlobalExceptionProperties properties) {
        this.properties = properties;
    }

    @Override
    public void log(ErrorDescriptor descriptor, @Nullable HttpServletRequest request,
            @Nullable HandlerMethod handlerMethod, OutputProtocol protocol) {
        Throwable ex = descriptor.getError();
        LogPolicyTable.LogPolicy policy = LogPolicyTable.lookup(ex);
        boolean printStack = this.resolvePrintStack(descriptor, ex, policy);

        String requestInfo = formatRequest(request);
        String handlerInfo = formatHandler(handlerMethod);
        String protocolTag = this.properties.getLog().getProtocolTag() ? " | protocol=" + protocol : "";

        if (policy.level() == LogPolicyTable.LogLevel.ERROR) {
            // 异常对象作为最后一个参数传入 → SLF4J 打印完整堆栈（设计规约：堆栈保留）。
            if (printStack) {
                LOG.error(PATTERN, requestInfo, handlerInfo, protocolTag, descriptor.getCode(), descriptor.getMessage(), ex);
            } else {
                LOG.error(PATTERN, requestInfo, handlerInfo, protocolTag, descriptor.getCode(), descriptor.getMessage());
            }
        } else {
            if (printStack) {
                LOG.warn(PATTERN, requestInfo, handlerInfo, protocolTag, descriptor.getCode(), descriptor.getMessage(), ex);
            } else {
                LOG.warn(PATTERN, requestInfo, handlerInfo, protocolTag, descriptor.getCode(), descriptor.getMessage());
            }
        }
    }

    /**
     * 线性四级链解析「是否打堆栈」（v3.0）：每级先命中先停，无回写、无跨级覆写。
     */
    private boolean resolvePrintStack(ErrorDescriptor descriptor, Throwable ex,
            LogPolicyTable.LogPolicy policy) {
        // 1. 注解 / Mapper 显式声明，最高优先级，直接拍板。
        LogStackPolicy explicit = descriptor.getLogPolicy();
        if (explicit == LogStackPolicy.ALWAYS) {
            return true;
        }
        if (explicit == LogStackPolicy.NEVER) {
            return false;
        }

        // 2. ServiceException 业务方显式要求打堆栈（withStack()），只能强制开。
        if (ex instanceof ServiceException serviceException && serviceException.shouldRecordStack()) {
            return true;
        }

        // 3. 表兜底条目（未知异常）被全局 printStackForUnknown 覆盖：
        //    未知异常默认 ERROR + 完整堆栈；运维确认噪音过多时可全局关掉兜底堆栈。
        if (policy == LogPolicyTable.DEFAULT && !this.properties.getLog().getPrintStackForUnknown()) {
            return false;
        }

        // 4. 按静态分类表结论（参数错/业务错 → false；数据库系统错误/序列化失败 → true）。
        return policy.printStack();
    }

    private static String formatRequest(@Nullable HttpServletRequest request) {
        return request == null ? "N/A" : request.getMethod() + " " + request.getRequestURI();
    }

    private static String formatHandler(@Nullable HandlerMethod handlerMethod) {
        return handlerMethod == null ? "Handler: N/A"
                : handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName();
    }
}
