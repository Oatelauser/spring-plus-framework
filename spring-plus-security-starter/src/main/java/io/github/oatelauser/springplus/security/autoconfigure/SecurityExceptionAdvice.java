package io.github.oatelauser.springplus.security.autoconfigure;

import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import io.github.oatelauser.springplus.web.error.engine.ExceptionOutputEngine;
import io.github.oatelauser.springplus.web.response.ClientStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * security 模块级异常 advice（多 advice 共存机制的第一个落地用例）。
 * <p>
 * {@code @Order(100)}：在全局兜底 {@code GlobalExceptionAdvice}
 * （{@code Ordered.LOWEST_PRECEDENCE}）之前被咨询——security 域的异常由本模块
 * 自己处理，web 侧不再按类名字符串匹配透传（原 {@code SECURITY_DENIED_CLASSES} 已删除）。
 *
 * <h3>两类语义</h3>
 * <ul>
 *   <li><b>denied 透传</b>：{@link AccessDeniedException}（含子类
 *       {@code AuthorizationDeniedException}）从 handler 原样抛出——403 语义归
 *       Security 的 {@code ExceptionTranslationFilter} / 方法级 denied handler 翻译，
 *       被渲染成 JSON 错误体反而会吞掉 HTTP 语义；</li>
 *   <li><b>认证异常翻译</b>：{@link AuthenticationException} 家族按子类型映射到
 *       web 的 {@link ClientStatus} 错误码（见 {@link #mapToStatus}），经
 *       {@link ExceptionOutputEngine} 统一渲染（JSON/SSE/NDJSON 协议探测、日志策略、
 *       已提交补写全部保留），HTTP 状态一律 401。</li>
 * </ul>
 * <p>
 * 渲染契约：模块 advice 只构造 {@link ErrorDescriptor} 后委托引擎，不自己写响应体；
 * 透传（rethrow）是唯一例外，仅限本安全语义。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-18
 * @since 1.1
 */
@Order(100)
@RestControllerAdvice
public class SecurityExceptionAdvice {

    private final ExceptionOutputEngine engine;

    public SecurityExceptionAdvice(ExceptionOutputEngine engine) {
        this.engine = engine;
    }

    /**
     * denied 家族透传：403 语义交还 Security 过滤器链。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void rethrowDenied(AccessDeniedException ex) {
        throw ex;
    }

    /**
     * 认证异常家族统一翻译：advice 内部按最精确子类型命中本方法后查表映射。
     */
    @ExceptionHandler(AuthenticationException.class)
    public Object handleAuthentication(AuthenticationException ex, HttpServletRequest request,
            HttpServletResponse response) {
        ClientStatus status = mapToStatus(ex);
        ErrorDescriptor descriptor = ErrorDescriptor.of(status.getCode(), status.getMessage(), ex)
                .statusIntent(HttpStatus.UNAUTHORIZED)
                .build();
        return engine.dispatch(ex, descriptor, request, response);
    }

    /**
     * 子类型 → web 错误码映射表：
     * BadCredentials → A0210；Locked → A0202；Disabled → A0203；
     * CredentialsExpired → A0212；AccountExpired → A0213；
     * SessionAuthentication → A0230；其余（含 InsufficientAuthentication）→ A0301。
     */
    static ClientStatus mapToStatus(AuthenticationException ex) {
        if (ex instanceof BadCredentialsException) {
            return ClientStatus.PASSWORD_ERROR;
        }
        if (ex instanceof LockedException) {
            return ClientStatus.ACCOUNT_FROZEN;
        }
        if (ex instanceof DisabledException) {
            return ClientStatus.ACCOUNT_DISABLED;
        }
        if (ex instanceof CredentialsExpiredException) {
            return ClientStatus.PASSWORD_EXPIRED;
        }
        if (ex instanceof AccountExpiredException) {
            return ClientStatus.ACCOUNT_EXPIRED;
        }
        if (ex instanceof SessionAuthenticationException) {
            return ClientStatus.LOGIN_EXPIRED;
        }
        // InsufficientAuthenticationException 及未列家族：未认证语义
        return ClientStatus.UNAUTHORIZED;
    }

}
