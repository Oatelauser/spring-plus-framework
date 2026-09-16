package io.github.oatelauser.springplus.web.response;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BasePageRequest} 校验规则：默认值、下限、上限、null 放行。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0
 */
class BasePageRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultsAreOneAndTen() {
        BasePageRequest request = new BasePageRequest();
        assertEquals(1, request.getPageNum());
        assertEquals(10, request.getPageSize());
        assertTrue(validator.validate(request).isEmpty(), "默认值应直接通过校验");
    }

    @Test
    void pageNumMustBeAtLeastOne() {
        BasePageRequest request = new BasePageRequest();
        request.setPageNum(0);
        Set<ConstraintViolation<BasePageRequest>> violations = validator.validate(request);
        assertEquals(1, violations.size());
        assertEquals("pageNum 必须大于等于 1", violations.iterator().next().getMessage());
    }

    @Test
    void pageSizeBoundedToOneAndFiveHundred() {
        BasePageRequest tooSmall = new BasePageRequest();
        tooSmall.setPageSize(0);
        assertEquals(1, validator.validate(tooSmall).size());

        BasePageRequest tooLarge = new BasePageRequest();
        tooLarge.setPageSize(501);
        assertEquals(1, validator.validate(tooLarge).size());

        BasePageRequest boundary = new BasePageRequest();
        boundary.setPageSize(500);
        assertTrue(validator.validate(boundary).isEmpty(), "上限 500 闭区间");
    }

    @Test
    void nullFieldsPassBeanValidation() {
        // @Min/@Max 对 null 放行是 Bean Validation 标准行为；PageResponse 工厂负责判空回退
        BasePageRequest request = new BasePageRequest();
        request.setPageNum(null);
        request.setPageSize(null);
        assertTrue(validator.validate(request).isEmpty());
    }

}
