package io.github.oatelauser.springplus.web.validation.clazz;

import jakarta.validation.Configuration;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * E3 回归：spring-plus.web.validation.fail-fast 开关。
 * true → 短路只报第一个错误；false → 一次返回全部字段错误。
 */
class ClassValidatorPostProcessorTest {

    record TwoErrors(@NotBlank String a, @NotBlank String b) {
    }

    private static int violationCount(boolean failFast) {
        Configuration<?> config = Validation.byDefaultProvider().configure();
        config.messageInterpolator(new ParameterMessageInterpolator());
        new ClassValidatorPostProcessor(failFast).customize(config);
        try (ValidatorFactory factory = config.buildValidatorFactory()) {
            return factory.getValidator().validate(new TwoErrors("", "")).size();
        }
    }

    @Test
    void failFastReportsSingleError() {
        assertEquals(1, violationCount(true));
    }

    @Test
    void fullModeReportsAllErrors() {
        assertEquals(2, violationCount(false));
    }

}
