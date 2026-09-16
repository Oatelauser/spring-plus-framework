package io.github.oatelauser.springplus.governor.idempotent;

import io.github.oatelauser.springplus.web.error.ServiceException;
import io.github.oatelauser.springplus.web.response.ClientStatus;
import org.aopalliance.intercept.MethodInvocation;

/**
 * Token 策略：spel 提取唯一字符串（如页面下发的 token）。
 * <p>
 * spel 求值结果须为非空字符串，否则按 {@link ClientStatus#PARAMETER_MISSING} 处理。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-08-31
 * @since 1.2
 */
public class TokenKeyStrategy implements IdempotentKeyStrategy {

    @Override
    public String extract(String spelRaw, String spelValue, MethodInvocation invocation) {
        if (spelRaw == null || spelRaw.isBlank()) {
            throw new ServiceException(ClientStatus.PARAMETER_MISSING.withArgs("@Idempotent/@RepeatSubmit.spel"));
        }
        if (spelValue == null || spelValue.isBlank()) {
            throw new ServiceException(ClientStatus.PARAMETER_MISSING.withArgs(spelRaw));
        }
        return spelValue;
    }

    @Override
    public String strategyName() {
        return "token";
    }

}
