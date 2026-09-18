package io.github.oatelauser.springplus.web.error.engine;

import io.github.oatelauser.springplus.web.error.ServiceException;
import io.github.oatelauser.springplus.web.response.ServerStatus;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * 异常日志静态分类表（v3.0，设计文档 5.3）。
 * <p>
 * 把「这个异常该打什么日志级别、要不要堆栈」从每个 {@link ExceptionHandler} 方法里手写，
 * 收口成一张启动期硬编码的表，运行期 O(1) 查表。原则（设计 5.3）：
 * <ul>
 *   <li>一眼看得出原因的异常（参数错、业务错、唯一键冲突）→ WARN，不打堆栈。</li>
 *   <li>难以排查的异常（数据库系统错误、序列化失败、兜底）→ ERROR，打完整堆栈。</li>
 * </ul>
 *
 * <h3>查表算法</h3>
 * <ol>
 *   <li>沿 {@code ex.getClass()} 的父类链逐级精确查 {@link #TABLE}（具体类优先，如
 *       {@link DuplicateKeyException} 命中 WARN，而非走到父类 {@link DataAccessException} 的 ERROR）。</li>
 *   <li>父类链未命中：若异常实现了 {@link ServerStatus}（业务异常体系），归为 WARN / 不打堆栈。</li>
 *   <li>仍未命中：返回 {@link #DEFAULT}（ERROR + 打堆栈）——对未知异常必须留堆栈排障。</li>
 * </ol>
 *
 * <h3>与日志决策优先级的关系（v3.0 线性链）</h3>
 * <p>
 * 本表结论是<b>最低优先级</b>的兜底（设计 5.3），仅第 3 级的全局 {@code printStackForUnknown}
 * 能覆盖其中的 {@link #DEFAULT} 条目：
 * <pre>
 *   1. 注解 / Mapper 显式 logPolicy（ALWAYS / NEVER） &gt; 2. ServiceException.withStack()
 *      &gt; 3. printStackForUnknown 覆盖表兜底条目 &gt; 4. 本表
 * </pre>
 * {@code DefaultExceptionLogger} 调用 {@link #lookup(Throwable)} 后按上述线性顺序取最终值。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
final class LogPolicyTable {

    /**
     * 日志级别（只区分业务/客户端用的 WARN 和系统用的 ERROR，不引入 DEBUG/INFO 噪音）。
     */
    enum LogLevel {
        WARN, ERROR
    }

    /**
     * 一条分类策略：日志级别 + 是否打印堆栈。
     */
    record LogPolicy(LogLevel level, boolean printStack) {
        /**
         * WARN 且不打堆栈（参数 / 业务异常的典型策略）。
         */
        static LogPolicy warn() {
            return new LogPolicy(LogLevel.WARN, false);
        }

        /**
         * ERROR 且打堆栈（系统错误 / 兜底的典型策略）。
         */
        static LogPolicy error() {
            return new LogPolicy(LogLevel.ERROR, true);
        }
    }

    /**
     * 兜底策略：未知异常一律 ERROR + 完整堆栈。
     */
    static final LogPolicy DEFAULT = LogPolicy.error();

    /**
     * 静态分类表：异常类 → 策略。父类链精确查表，具体类优先于抽象父类。
     */
    private static final Map<Class<?>, LogPolicy> TABLE = new ConcurrentHashMap<>();

    static {
        // ── 参数 / 请求格式：WARN，不打堆栈 ──
        register(MethodArgumentNotValidException.class, LogPolicy.warn());
        register(WebExchangeBindException.class, LogPolicy.warn());
        register(org.springframework.validation.BindException.class, LogPolicy.warn());
        register(jakarta.validation.ConstraintViolationException.class, LogPolicy.warn());
        // Spring 6.1+ 非 body 参数约束校验（@RequestParam @Min(...) 等）：客户端错误，WARN 不打堆栈
        register(HandlerMethodValidationException.class, LogPolicy.warn());
        register(MissingServletRequestParameterException.class, LogPolicy.warn());
        register(MissingPathVariableException.class, LogPolicy.warn());
        register(MissingRequestHeaderException.class, LogPolicy.warn());
        register(MissingServletRequestPartException.class, LogPolicy.warn());
        register(HttpMessageNotReadableException.class, LogPolicy.warn());
        register(MethodArgumentTypeMismatchException.class, LogPolicy.warn());
        register(org.springframework.beans.TypeMismatchException.class, LogPolicy.warn());
        register(ConversionNotSupportedException.class, LogPolicy.warn());

        // ── HTTP 协商：WARN，不打堆栈 ──
        register(org.springframework.web.HttpRequestMethodNotSupportedException.class, LogPolicy.warn());
        register(org.springframework.web.HttpMediaTypeNotSupportedException.class, LogPolicy.warn());
        register(org.springframework.web.HttpMediaTypeNotAcceptableException.class, LogPolicy.warn());

        // ── 路由 / 资源：WARN，不打堆栈 ──
        register(NoHandlerFoundException.class, LogPolicy.warn());
        register(NoResourceFoundException.class, LogPolicy.warn());
        register(org.springframework.web.multipart.MaxUploadSizeExceededException.class, LogPolicy.warn());

        // ── 业务异常：WARN，不打堆栈 ──
        register(ServiceException.class, LogPolicy.warn());

        // ── 数据库可解释错误：WARN，不打堆栈 ──
        register(DuplicateKeyException.class, LogPolicy.warn());
        register(DataIntegrityViolationException.class, LogPolicy.warn());

        // ── 数据库系统错误：ERROR，打堆栈（父类，兜住其它 DataAccessException 子类）──
        register(DataAccessException.class, LogPolicy.error());
        // SQLException 是 java.sql 的具体类（其它 SQL 异常的父类）
        register(java.sql.SQLException.class, LogPolicy.error());

        // ── 响应序列化失败：ERROR，打堆栈 ──
        register(HttpMessageNotWritableException.class, LogPolicy.error());

        // ── 超时：ERROR，打堆栈 ──
        register(TimeoutException.class, LogPolicy.error());
        register(AsyncRequestTimeoutException.class, LogPolicy.error());
    }

    private LogPolicyTable() {
    }

    /**
     * 查异常对应的日志策略。
     *
     * @param throwable 异常
     * @return 日志策略（永不为 null）
     */
    static LogPolicy lookup(Throwable throwable) {
        // 1. 父类链精确查表：具体类优先（DuplicateKeyException 命中 WARN，而非 DataAccessException 的 ERROR）
        Class<?> current = throwable.getClass();
        while (current != null) {
            LogPolicy policy = TABLE.get(current);
            if (policy != null) {
                return policy;
            }
            current = current.getSuperclass();
        }
        // 2. 业务异常体系（实现 ServerStatus 但非 ServiceException 的自定义异常）：WARN，不打堆栈
        if (throwable instanceof ServerStatus) {
            return LogPolicy.warn();
        }
        // 3. 兜底：未知异常 ERROR + 堆栈
        return DEFAULT;
    }

    private static void register(Class<?> exceptionType, LogPolicy policy) {
        TABLE.put(exceptionType, policy);
    }
}
