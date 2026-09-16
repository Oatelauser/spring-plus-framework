package io.github.oatelauser.springplus.web.validation.clazz;

import io.github.oatelauser.springplus.web.validation.Validatable;
import io.github.oatelauser.springplus.web.validation.ValidationResult;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.jspecify.annotations.NonNull;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;

/**
 * 类级别的校验器
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2025-05-01
 * @since 1.0
 */
@Slf4j
public class ClassCheckValidator implements ConstraintValidator<ClassValidator, Object> {

    public static final String DEFAULT_METHOD_NAME = "validate";

    private String methodName;
    private boolean failFast;

    @Override
    public void initialize(ClassValidator annotation) {
        String method = annotation.method();
        this.failFast = annotation.failFast();
        this.methodName = StringUtils.hasText(method) ? method : DEFAULT_METHOD_NAME;
    }

    @Override
    public boolean isValid(Object target, ConstraintValidatorContext context) {
        if (target == null) {
            return true;
        }
        ValidationResult result = this.performValidation(target);
        if (result.valid()) {
            return true;
        }
        // 禁用默认的约束违规消息
        context.disableDefaultConstraintViolation();
        // 添加自定义的错误信息
        this.addConstraintViolations(result, context);
        return false;
    }

    /**
     * 执行校验逻辑
     *
     * @param target 目标对象
     * @return 校验结果
     */
    @NonNull
    private ValidationResult performValidation(Object target) {
        if (target instanceof Validatable validatable) {
            try {
                return validatable.validate();
            } catch (Exception e) {
                log.error("Validatable.validate execution failed [{}]",
                        target.getClass().getName(), e);
                return ValidationResult.failure("Validation execution error: " + e.getMessage());
            }
        }
        return this.invokeValidationMethod(target);
    }

    @NonNull
    private ValidationResult invokeValidationMethod(Object target) {
        Class<?> targetClass = target.getClass();

        Optional<ValidationLambdaFactory.ValidationInvoker> invokerOpt =
                ValidationLambdaFactory.getOrCreateInvoker(methodName, targetClass);

        if (invokerOpt.isEmpty()) {
            // 没有找到校验方法，默认通过
            log.debug("No validation method [{}] found in class [{}], skipping validation",
                    methodName, targetClass.getName());
            return ValidationResult.success();
        }

        try {
            return invokerOpt.get().invoke(target);
        } catch (Exception e) {
            log.error("Failed to invoke validation method [{}] on class {}",
                    methodName, targetClass.getName(), e);
            return ValidationResult.failure("Validation method invocation failed: " +
                    getRootCauseMessage(e));
        }
    }

    /**
     * 添加约束违规信息到上下文
     */
    private void addConstraintViolations(ValidationResult result, ConstraintValidatorContext context) {
        for (ValidationResult.ValidationError error : result.errors()) {
            var violationBuilder = context.buildConstraintViolationWithTemplate(error.getMessage());
            if (error.isFieldError()) {
                violationBuilder.addPropertyNode(error.getField()).addConstraintViolation();
            } else {
                violationBuilder.addConstraintViolation();
            }
            this.addMessageParameters(error, context);
            if (failFast) {
                break;
            }
        }
    }

    /**
     * 添加消息参数（支持消息模板中的变量替换）
     */
    private void addMessageParameters(ValidationResult.ValidationError error, ConstraintValidatorContext context) {
        Map<String, Object> params = error.getMessageParams();
        if (CollectionUtils.isEmpty(params)) {
            return;
        }
        if (context instanceof HibernateConstraintValidatorContext hibernateContext) {
            params.forEach(hibernateContext::addMessageParameter);
        }
    }

    /**
     * 获取根异常信息
     */
    private String getRootCauseMessage(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }

}
