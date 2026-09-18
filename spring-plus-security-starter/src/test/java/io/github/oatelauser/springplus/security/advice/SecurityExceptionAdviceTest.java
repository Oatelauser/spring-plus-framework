package io.github.oatelauser.springplus.security.advice;

import io.github.oatelauser.springplus.web.response.ClientStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * security 模块 advice 行为回归：认证异常映射表 / denied 透传 / order 契约
 * （须小于全局兜底的 {@code LOWEST_PRECEDENCE}）。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-18
 * @since 1.1
 */
class SecurityExceptionAdviceTest {

    private final SecurityExceptionAdvice advice = new SecurityExceptionAdvice(null);

    @Test
    void mapsAuthenticationExceptionFamilyToClientStatus() {
        assertEquals(ClientStatus.PASSWORD_ERROR, SecurityExceptionAdvice
                .mapToStatus(new BadCredentialsException("bad")));
        assertEquals(ClientStatus.ACCOUNT_FROZEN, SecurityExceptionAdvice
                .mapToStatus(new LockedException("locked")));
        assertEquals(ClientStatus.ACCOUNT_DISABLED, SecurityExceptionAdvice
                .mapToStatus(new DisabledException("disabled")));
        assertEquals(ClientStatus.PASSWORD_EXPIRED, SecurityExceptionAdvice
                .mapToStatus(new CredentialsExpiredException("expired")));
        assertEquals(ClientStatus.ACCOUNT_EXPIRED, SecurityExceptionAdvice
                .mapToStatus(new AccountExpiredException("expired")));
        assertEquals(ClientStatus.LOGIN_EXPIRED, SecurityExceptionAdvice
                .mapToStatus(new SessionAuthenticationException("session")));
        assertEquals(ClientStatus.UNAUTHORIZED, SecurityExceptionAdvice
                .mapToStatus(new InsufficientAuthenticationException("full")));
        // 未列家族兜底：同样按未认证语义处理，而不是漏回全局系统错误
        assertEquals(ClientStatus.UNAUTHORIZED, SecurityExceptionAdvice
                .mapToStatus(new AuthenticationException("anonymous subtype") {
                }));
    }

    @Test
    void deniedExceptionRethrownAsIs() {
        AccessDeniedException ex = new AccessDeniedException("denied");
        AccessDeniedException rethrown = assertThrows(AccessDeniedException.class,
                () -> advice.rethrowDenied(ex));
        assertEquals(ex, rethrown);
    }

    @Test
    void adviceOrderPrecedesGlobalFallback() {
        Integer order = OrderUtils.getOrder(SecurityExceptionAdvice.class);
        assertEquals(100, order);
        assertTrue(order < Ordered.LOWEST_PRECEDENCE,
                "模块 advice 必须先于全局兜底（LOWEST_PRECEDENCE）被咨询");
    }

}
