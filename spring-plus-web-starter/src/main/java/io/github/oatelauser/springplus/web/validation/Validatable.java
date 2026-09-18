package io.github.oatelauser.springplus.web.validation;

/**
 * 可校验接口，实现此接口的类可以进行自定义校验
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-01-27
 * @since 1.0
 */
public interface Validatable {

    /**
     * 执行校验逻辑
     *
     * @return 校验结果
     * @see ValidationResult
     */
    ValidationResult validate();

}
