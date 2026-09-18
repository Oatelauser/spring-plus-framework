package io.github.oatelauser.springplus.web.validation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 校验结果封装类：支持多个错误信息和字段级别的错误绑定
 * <p>
 * 示例代码：
 * @formatter:off
 * {@snippet :
 *
 * @Data
 * @Validator(message = "用户信息校验失败")
 * public class UserDTO implements Validatable {
 *
 *     @NotBlank(message = "用户名不能为空")
 *     private String username;
 *     private String password;
 *     private String confirmPassword;
 *     private LocalDate startDate;
 *     private LocalDate endDate;
 *     private Integer minAge;
 *     private Integer maxAge;
 *
 *     @Override
 *     public ValidationResult validate() {
 *        return ValidationResult.builder()
 *            // 密码一致性校验
 *            .addFieldErrorIf(
 *                password != null && !password.equals(confirmPassword),
 *                "confirmPassword",
 *                "两次输入的密码不一致"
 *            )
 *            // 日期范围校验
 *            .addFieldErrorIf(
 *                startDate != null && endDate != null && startDate.isAfter(endDate),
 *                "endDate",
 *                "结束日期必须晚于开始日期"
 *            )
 *            // 年龄范围校验（带参数的消息模板）
 *            .addFieldErrorIf(
 *                minAge != null && maxAge != null && minAge > maxAge,
 *                "maxAge",
 *                "最大年龄({max})必须大于最小年龄({min})",
 *                Map.of("min", minAge, "max", maxAge)
 *                )
 *                .build();
 *        }
 * }
 *}
 * @formatter:on
 *
 * @date 2026-01-27
 * @since 1.0
 */
public record ValidationResult(boolean valid, List<ValidationError> errors) {

    /**
     * 创建校验成功的结果
     */
    public static ValidationResult success() {
        return new ValidationResult(true, List.of());
    }

    /**
     * 创建校验失败的结果（类级别错误）
     */
    public static ValidationResult failure(String message) {
        return new ValidationResult(false, List.of(ValidationError.ofClass(message)));
    }

    /**
     * 创建校验失败的结果（字段级别错误）
     */
    public static ValidationResult failure(String field, String message) {
        return new ValidationResult(false, List.of(ValidationError.ofField(field, message)));
    }

    /**
     * 创建带多个错误的校验结果
     */
    public static ValidationResult failure(List<ValidationError> errors) {
        return new ValidationResult(false, new ArrayList<>(errors));
    }

    /**
     * 构建器模式创建校验结果
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 校验错误详情
     */
    @Data
    public static class ValidationError {
        /**
         * null 表示类级别错误
         */
        private final String field;
        private final String message;
        private final Map<String, Object> messageParams;

        private ValidationError(String field, String message, Map<String, Object> messageParams) {
            this.field = field;
            this.message = message;
            this.messageParams = messageParams == null ? Map.of() : Map.copyOf(messageParams);
        }

        public static ValidationError ofClass(String message) {
            return new ValidationError(null, message, null);
        }

        public static ValidationError ofField(String field, String message) {
            return new ValidationError(field, message, null);
        }

        public static ValidationError ofField(String field, String message, Map<String, Object> params) {
            return new ValidationError(field, message, params);
        }

        public boolean isFieldError() {
            return field != null;
        }
    }

    /**
     * 校验结果构建器
     */
    public static class Builder {
        private final List<ValidationError> errors = new ArrayList<>();

        /**
         * 添加类级别错误
         */
        public Builder addError(String message) {
            errors.add(ValidationError.ofClass(message));
            return this;
        }

        /**
         * 添加字段级别错误
         */
        public Builder addFieldError(String field, String message) {
            errors.add(ValidationError.ofField(field, message));
            return this;
        }

        /**
         * 添加带参数的字段级别错误（支持消息模板）
         */
        public Builder addFieldError(String field, String message, Map<String, Object> params) {
            errors.add(ValidationError.ofField(field, message, params));
            return this;
        }

        /**
         * 条件添加错误
         */
        public Builder addErrorIf(boolean condition, String message) {
            if (condition) {
                errors.add(ValidationError.ofClass(message));
            }
            return this;
        }

        /**
         * 条件添加字段错误
         */
        public Builder addFieldErrorIf(boolean condition, String field, String message) {
            if (condition) {
                errors.add(ValidationError.ofField(field, message));
            }
            return this;
        }

        /**
         * 条件添加字段错误
         */
        public Builder addFieldErrorIf(boolean condition, String field, String message, Map<String, Object> params) {
            if (condition) {
                errors.add(ValidationError.ofField(field, message, params));
            }
            return this;
        }

        public ValidationResult build() {
            return errors.isEmpty() ?
                    ValidationResult.success() :
                    ValidationResult.failure(errors);
        }
    }

}
