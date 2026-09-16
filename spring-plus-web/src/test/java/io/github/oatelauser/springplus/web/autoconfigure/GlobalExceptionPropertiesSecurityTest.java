package io.github.oatelauser.springplus.web.autoconfigure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 安全默认值回归（V01）：{@code showError} 必须默认关闭——未映射异常原文不得透出客户端。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0.1
 */
class GlobalExceptionPropertiesSecurityTest {

    @Test
    void showErrorDefaultsToFalse() {
        assertFalse(new GlobalExceptionProperties().getShowError(),
                "生产默认必须不透出异常原文（CWE-209）；排障经 spring-plus.web.error-response.show-error=true 开启");
    }

}
