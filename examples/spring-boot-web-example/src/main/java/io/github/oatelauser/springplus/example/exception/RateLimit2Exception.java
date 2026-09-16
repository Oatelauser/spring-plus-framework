package io.github.oatelauser.springplus.example.exception;

import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import io.github.oatelauser.springplus.web.error.mapper.ExceptionMapper;
import io.github.oatelauser.springplus.web.error.mapper.ExceptionMapperContext;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.Map;

/**
 * 测试异常：模拟「第三方限流 SDK」抛出的异常（v2.0 UC-7 验证）。
 * <p>
 * <b>不</b>贴任何注解、<b>不</b>实现 {@code ServerStatus}——刻意做成「框架不认识的陌生异常」，
 * 留给业务自己写的 {@code RateLimitExceptionMapper} 去翻译。验证 Mapper 链的扩展能力：
 * 业务方注册一个 {@code @Component implements ExceptionMapper}，无须改任何框架代码。
 *
 * <h3>设计意图</h3>
 * <ul>
 *   <li>{@link #retryAfterSeconds} 暴露给 Mapper，由 Mapper 写入 {@code ErrorDescriptor.details}，
 *       最终以 {@code details.retryAfterSeconds} 透出到前端响应。</li>
 *   <li>不继承 {@link RuntimeException} 之外的任何业务体系，让框架对它<b>毫无先验认知</b>，
 *       仅靠 Mapper 链才能命中。</li>
 * </ul>
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
@Getter
public class RateLimit2Exception extends RuntimeException implements ExceptionMapper {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 客户端建议在多少秒后重试。 */
    private final int retryAfterSeconds;

    public RateLimit2Exception(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    public @Nullable ErrorDescriptor map(Throwable ex, ExceptionMapperContext ctx) {
        return ErrorDescriptor.of("A0429", "请求过于频繁", ex)
                .details(Map.of("retryAfterSeconds", retryAfterSeconds))
                .build();
    }
}
