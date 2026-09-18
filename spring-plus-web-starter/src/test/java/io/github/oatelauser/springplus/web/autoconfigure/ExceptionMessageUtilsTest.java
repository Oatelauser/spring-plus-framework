package io.github.oatelauser.springplus.web.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ExceptionMessageUtils} 方法级校验格式化测试（v3.1：HandlerMethodValidationException 路径）。
 * <p>
 * 手工构造 {@link HandlerMethodValidationException}（经 {@link MethodValidationResult#create}），
 * 验证其输出与 body 校验（{@code BindException}）路径<b>同构</b>：同样的 "[field] msg; " 拼接、
 * 同样的 {@code violations} details 结构——前端一套解析逻辑通吃两条校验路径。
 * <p>
 * 参数名断言采用动态期望值：真实参数名可用（{@code -parameters} 编译，Boot 默认开启）时
 * 断言真实名，不可用时断言 {@code arg + 下标} 回退——两种编译形态下契约都成立。
 *
 * @author Oatelauser
 * @date 2026-08-24
 * @since 3.1
 */
class ExceptionMessageUtilsTest {

    /**
     * 供 {@link MethodParameter} 反射使用的样例方法（参数 0/1 各带一个约束错误的语义）。
     */
    @SuppressWarnings("unused")
    void sample(String name, Integer limit) {
    }

    @Test
    void formatJoinsAllParameterErrors() throws Exception {
        HandlerMethodValidationException ex = exceptionWith(
                result(param(0), "name 不能为空"),
                result(param(1), "必须大于等于 1"));

        String message = ExceptionMessageUtils.formatHandlerMethodValidation(ex);

        assertEquals("[" + expectedName(0) + "] name 不能为空; ["
                + expectedName(1) + "] 必须大于等于 1", message);
    }

    @Test
    void detailsMirrorViolationsStructure() throws Exception {
        HandlerMethodValidationException ex = exceptionWith(
                result(param(0), "name 不能为空"),
                result(param(1), "必须大于等于 1"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> violations =
                (List<Map<String, String>>) ExceptionMessageUtils.toFieldDetails(ex).get("violations");

        assertEquals(2, violations.size());
        assertEquals(expectedName(0), violations.get(0).get("field"));
        assertEquals("name 不能为空", violations.get(0).get("msg"));
        assertEquals(expectedName(1), violations.get(1).get("field"));
        assertEquals("必须大于等于 1", violations.get(1).get("msg"));
    }

    @Test
    void aggregatesMultipleErrorsOnSameParameter() throws Exception {
        HandlerMethodValidationException ex = exceptionWith(
                result(param(1), "必须大于等于 1", "必须小于等于 100"));

        String message = ExceptionMessageUtils.formatHandlerMethodValidation(ex);

        assertEquals("[" + expectedName(1) + "] 必须大于等于 1; "
                + "[" + expectedName(1) + "] 必须小于等于 100", message);
    }

    @Test
    void crossParameterConstraintMappedToLiteralField() throws Exception {
        HandlerMethodValidationException ex = new HandlerMethodValidationException(MethodValidationResult.create(
                this, sampleMethod(), List.of(),
                List.of(new DefaultMessageSourceResolvable(new String[0], new Object[0], "参数组合不合法"))));

        String message = ExceptionMessageUtils.formatHandlerMethodValidation(ex);

        assertEquals("[crossParameter] 参数组合不合法", message);
    }

    // ====================== 构造工具 ======================

    private static Method sampleMethod() throws Exception {
        return ExceptionMessageUtilsTest.class.getDeclaredMethod("sample", String.class, Integer.class);
    }

    private static MethodParameter param(int index) throws Exception {
        return new MethodParameter(sampleMethod(), index);
    }

    private static ParameterValidationResult result(MethodParameter parameter, String... messages) {
        List<DefaultMessageSourceResolvable> errors = Arrays.stream(messages)
                .map(msg -> new DefaultMessageSourceResolvable(new String[0], new Object[0], msg))
                .toList();
        return new ParameterValidationResult(parameter, null, errors, null, null, null, null);
    }

    private static HandlerMethodValidationException exceptionWith(ParameterValidationResult... results)
            throws Exception {
        // target 只需非空对象（模拟被校验的 controller 实例），静态上下文无法用 this
        return new HandlerMethodValidationException(
                MethodValidationResult.create(new ExceptionMessageUtilsTest(), sampleMethod(), List.of(results)));
    }

    /** 与 {@code ExceptionMessageUtils#resolveParameterName} 同语义的期望值计算。 */
    private static String expectedName(int index) throws Exception {
        String name = param(index).getParameterName();
        return name != null ? name : "arg" + index;
    }

}
