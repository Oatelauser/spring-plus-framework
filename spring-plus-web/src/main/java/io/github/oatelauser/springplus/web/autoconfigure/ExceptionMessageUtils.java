package io.github.oatelauser.springplus.web.autoconfigure;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 异常消息格式化工具（v3.1：随 {@code GlobalExceptionAdvice} 一并迁入 {@code autoconfigure}
 * 包——同包互调，类可见性收回包内）。
 * <p>
 * 移除了 v1.0 内部的 {@code ExceptionLogger}（日志已收口到 {@code DefaultExceptionLogger}），
 * 新增 {@link #toFieldDetails} / {@link #toConstraintViolationDetails} 产出结构化 details，
 * 供 {@code ErrorDescriptor.getDetails()} → {@code SimpleResponse.details} 透出给前端。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-01-29
 * @since 1.0
 */
final class ExceptionMessageUtils {

    // ====================== 校验错误格式化 ======================

    public static String formatBindingErrors(BindException ex) {
        var errors = ex.getBindingResult().getAllErrors();
        if (CollectionUtils.isEmpty(errors)) {
            return "参数校验失败";
        }
        return errors.stream()
                .map(ExceptionMessageUtils::formatObjectError)
                .collect(Collectors.joining("; "));
    }


    static String formatObjectError(ObjectError error) {
        String field = (error instanceof FieldError fe) ? fe.getField() : error.getObjectName();
        return "[" + field + "] " + error.getDefaultMessage();
    }

    public static String formatConstraintViolation(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int dotIndex = path.lastIndexOf('.');
        String field = dotIndex > 0 ? path.substring(dotIndex + 1) : path;
        return "[" + field + "] " + violation.getMessage();
    }

    /**
     * 把 {@link BindException}（{@code @Valid} 失败）转成结构化 details（设计文档 4.3）。
     * <p>
     * 产出形如：{@code {"violations":[{"field":"name","msg":"不能为空"}]}}，写入
     * {@code ErrorDescriptor.details} 后透出到 {@code SimpleResponse.details}。
     *
     * @param ex 绑定异常
     * @return 含 {@code violations} 清单的 Map
     */
    public static Map<String, Object> toFieldDetails(BindException ex) {
        var errors = ex.getBindingResult().getAllErrors();
        List<Map<String, String>> violations = errors.stream()
                .map(ExceptionMessageUtils::violationEntry)
                .toList();
        return Map.of("violations", violations);
    }

    /**
     * 把 {@link ConstraintViolationException}（{@code @Validated} 失败）转成结构化 details。
     *
     * @param violations 约束违反集合
     * @return 含 {@code violations} 清单的 Map
     */
    public static Map<String, Object> toConstraintViolationDetails(Set<? extends ConstraintViolation<?>> violations) {
        List<Map<String, String>> list = violations.stream()
                .map(v -> {
                    String path = v.getPropertyPath().toString();
                    int dotIndex = path.lastIndexOf('.');
                    String field = dotIndex > 0 ? path.substring(dotIndex + 1) : path;
                    return violationEntry(field, v.getMessage());
                })
                .toList();
        return Map.of("violations", list);
    }

    /**
     * 格式化 {@link HandlerMethodValidationException} 的全部校验错误
     * （Spring 6.1+ 非 body 参数约束失败，v3.1 新增）。
     * <p>
     * 输出与 {@link #formatBindingErrors(BindException)} 同构：{@code [参数名] 消息}
     * 以 "; " 连接，前端无需区分两条校验路径。
     *
     * @param ex 方法级校验异常
     * @return 拼接后的错误消息
     */
    public static String formatHandlerMethodValidation(HandlerMethodValidationException ex) {
        List<Map<String, String>> violations = toHandlerMethodViolations(ex);
        if (violations.isEmpty()) {
            return "参数校验失败";
        }
        return violations.stream()
                .map(v -> "[" + v.get("field") + "] " + v.get("msg"))
                .collect(Collectors.joining("; "));
    }

    /**
     * 把 {@link HandlerMethodValidationException} 转成结构化 details
     * （与 {@link #toFieldDetails(BindException)} 同构输出，v3.1 新增）。
     *
     * @param ex 方法级校验异常
     * @return 含 {@code violations} 清单的 Map
     */
    public static Map<String, Object> toFieldDetails(HandlerMethodValidationException ex) {
        return Map.of("violations", toHandlerMethodViolations(ex));
    }

    /**
     * 提取方法级校验的全部违规项：逐参数展开其约束错误；跨参数约束
     * （{@code @ScriptAssert} 类，不属于任何单一参数）归到字面量 {@code crossParameter}。
     * <p>
     * 与 body 校验（Spring 7 fail-fast 只抛首个字段）不同，方法级校验聚合<b>全部</b>参数结果。
     */
    private static List<Map<String, String>> toHandlerMethodViolations(HandlerMethodValidationException ex) {
        List<Map<String, String>> violations = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String field = resolveParameterName(result.getMethodParameter());
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                violations.add(violationEntry(field, error.getDefaultMessage()));
            }
        }
        for (MessageSourceResolvable error : ex.getCrossParameterValidationResults()) {
            violations.add(violationEntry("crossParameter", error.getDefaultMessage()));
        }
        return violations;
    }

    /**
     * 参数名解析：{@code -parameters} 编译时为真实参数名，否则回退 {@code arg + 下标}
     * （Boot parent 默认开启 -parameters，回退仅防御手工编译场景）。
     */
    private static String resolveParameterName(MethodParameter parameter) {
        String name = parameter.getParameterName();
        return name != null ? name : "arg" + parameter.getParameterIndex();
    }

    private static Map<String, String> violationEntry(ObjectError error) {
        String field = (error instanceof FieldError fe) ? fe.getField() : error.getObjectName();
        return violationEntry(field, error.getDefaultMessage());
    }

    private static Map<String, String> violationEntry(String field, @Nullable String message) {
        // LinkedHashMap 保留 field/msg 顺序，前端展示稳定。
        Map<String, String> entry = new LinkedHashMap<>(2);
        entry.put("field", field);
        entry.put("msg", message != null ? message : "");
        return entry;
    }

    // ====================== HTTP消息解析 ======================

    public static String extractReadableMessage(HttpMessageNotReadableException ex) {        String message = ex.getMessage();
        if (message == null) {
            return "请求体解析失败";
        }
        if (message.contains("Required request body is missing")) {
            return "请求体不能为空";
        }
        if (message.contains("JSON parse error")) {
            return extractJsonParseError(message);
        }
        return "请求体格式错误";
    }

    private static String extractJsonParseError(String message) {
        int start = message.indexOf("JSON parse error:");
        int end = message.indexOf(";");
        if (start >= 0 && end > start) {
            return message.substring(start, end);
        }
        return "JSON解析失败";
    }

    // ====================== 异常工具 ======================

    public static Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

}
