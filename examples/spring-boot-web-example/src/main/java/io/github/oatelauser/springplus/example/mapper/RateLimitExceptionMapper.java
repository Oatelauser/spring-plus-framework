package io.github.oatelauser.springplus.example.mapper;

import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import io.github.oatelauser.springplus.web.error.mapper.ExceptionMapper;
import io.github.oatelauser.springplus.web.error.mapper.ExceptionMapperContext;
import io.github.oatelauser.springplus.example.exception.RateLimitException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 业务自定义 {@link ExceptionMapper}：把 {@link RateLimitException} 翻译为 {@code A0429}（v2.0 UC-7 验证）。
 * <p>
 * 这是 v2.0 第三方扩展点的核心验证：业务模块或中间件 starter 注册自己的 Mapper Bean，
 * 框架代码<b>零改动</b>就能识别新的异常类型。本实现额外把 {@code retryAfterSeconds}
 * 写进 {@link ErrorDescriptor#details()}，最终透出到 {@code SimpleResponse.details}。
 *
 * <h3>order = 100</h3>
 * <p>
 * 业务 Mapper 建议用<b>正数</b> order（设计 6.1 / 6.2）：排在内置 {@code ExceptionClassAnnotationMapper}(-1000)
 * 与 {@code ServerStatusMapper}(-900) 之后、{@code DefaultExceptionMapper}(MAX_VALUE) 之前。
 * 所以本 Mapper 优先于兜底，但仍允许「异常类上贴注解 / ServerStatus 体系」覆盖业务规则。
 *
 * <h3>预期响应（UC-7）</h3>
 * <pre>
 *   HTTP 200 + JSON
 *   {"code":"A0429","message":"请求过于频繁","data":null,
 *    "details":{"retryAfterSeconds":30},"success":false}
 * </pre>
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
@Component
public class RateLimitExceptionMapper implements ExceptionMapper {

    @Override
    public int order() {
        return 100;
    }

    @Nullable
    @Override
    public ErrorDescriptor map(Throwable ex, ExceptionMapperContext ctx) {
        // 因果链遍历由 ExceptionMapperChain 负责，本方法只判当前节点。
        if (ex instanceof RateLimitException rle) {
            return ErrorDescriptor.of("A0429", "请求过于频繁", ex)
                    .details(Map.of("retryAfterSeconds", rle.getRetryAfterSeconds()))
                    .build();
        }
        return null;
    }
}
