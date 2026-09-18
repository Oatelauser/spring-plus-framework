package io.github.oatelauser.springplus.web.error.mapper;

import io.github.oatelauser.springplus.web.autoconfigure.GlobalExceptionProperties;
import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import io.github.oatelauser.springplus.web.response.SystemStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static io.github.oatelauser.springplus.web.response.SystemStatus.INTERNAL_ERROR;

/**
 * 内置 Mapper：兜底翻译器，把任何「前面所有 Mapper 都没认出来」的 {@link Throwable} 映射为
 * {@link SystemStatus#INTERNAL_ERROR}（设计文档 6.2）。
 * <p>
 * 这是 Mapper 链的最后一道防线，吃掉 v1.0 里 {@code @ExceptionHandler(Exception.class)} 兜底分支中
 * 「未知异常」的处理逻辑。它的 {@link #order()} = {@link Integer#MAX_VALUE}，保证永远最后被调用；
 * 并且<b>始终返回非 null</b>——这让 {@code ExceptionMapperChain} 可以放心地「第一个返回非 null 的赢」，
 * 不必担心整条链都 miss。
 *
 * <h3>消息可见性受配置控制</h3>
 * <p>
 * 直接把底层异常消息透给前端有安全风险（泄露栈/SQL/内部结构），因此按 {@link GlobalExceptionProperties}：
 * <ul>
 *   <li>{@code showError=true}：透出异常的 localizedMessage（开发 / 排障期显式开启；1.0.1 起默认 false）。</li>
 *   <li>否则若配置了自定义 {@code code/msg}：用业务方指定的兜底文案。</li>
 *   <li>否则：用 {@link SystemStatus#INTERNAL_ERROR} 的标准文案「系统内部错误」。</li>
 * </ul>
 * <p>
 * 无论走哪条，日志层（{@code DefaultExceptionLogger}）都会按静态分类表对这种「兜底 Exception」
 * 打 ERROR + 完整堆栈，确保排障信息不丢（设计 5.3）。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public class DefaultExceptionMapper implements ExceptionMapper {

    private final GlobalExceptionProperties properties;

    public DefaultExceptionMapper(GlobalExceptionProperties properties) {
        this.properties = properties;
    }

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }

    @Override
    public ErrorDescriptor map(Throwable ex, ExceptionMapperContext ctx) {
        String code;
        String message;
        if (properties.getShowError()) {
            // 排障模式：透出真实异常信息，便于定位（生产环境建议关闭 showError）。
            code = INTERNAL_ERROR.getCode();
            message = StringUtils.hasText(ex.getLocalizedMessage()) ?
                    ex.getLocalizedMessage() : ex.getClass().getSimpleName();
        } else if (StringUtils.hasText(properties.getCode())) {
            // 业务方在配置里指定了兜底 code/msg（如统一返回「系统繁忙」）。
            code = properties.getCode();
            message = properties.getMsg();
        } else {
            // 最保守：标准「系统内部错误」。
            code = INTERNAL_ERROR.getCode();
            message = INTERNAL_ERROR.getMessage();
        }

        return ErrorDescriptor.of(code, message, ex).build();
    }

}
