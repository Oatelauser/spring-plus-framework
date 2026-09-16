package io.github.oatelauser.springplus.governor.idempotent;

import io.github.oatelauser.springplus.web.utils.JsonUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

/**
 * 兜底策略：{@code 主体 + 方法签名 + spel 选中参数} 指纹。
 * <p>
 * 无 spel 时退化为对全部参数做 JSON 序列化后拼接（向后兼容旧行为）。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-08-31
 * @since 1.2
 */
@SuppressWarnings("SpellCheckingInspection")
public class FingerprintKeyStrategy implements IdempotentKeyStrategy {

    @Override
    public String extract(String spelRaw, String spelValue, MethodInvocation invocation) {
        HttpServletRequest request = currentRequest();
        String subject = request == null ? "anonymous" : subject(request);
        String argsDigest = spelRaw == null || spelRaw.isBlank()
                ? stringify(invocation.getArguments())
                : (spelValue == null ? "" : spelValue);
        return subject + '|' + invocation.getMethod().toGenericString() + '|' + argsDigest;
    }

    @Override
    public String strategyName() {
        return "fingerprint";
    }

    private static HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }

    private static String subject(HttpServletRequest request) {
        if (request.getUserPrincipal() != null) {
            return request.getUserPrincipal().getName();
        }
        String sessionId = request.getRequestedSessionId();
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId;
        }
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "anonymous" : remoteAddress;
    }

    private static String stringify(Object[] arguments) {
        try {
            return JsonUtils.writeValueAsString(arguments);
        } catch (RuntimeException ignored) {
            return Arrays.deepToString(arguments);
        }
    }

}
