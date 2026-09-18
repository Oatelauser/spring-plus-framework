package io.github.oatelauser.springplus.web.error.mapper;

import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import io.github.oatelauser.springplus.web.response.ServerStatus;
import io.github.oatelauser.springplus.web.response.ServerStatusProvider;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 内置 Mapper：把实现 {@link ServerStatus} / {@link ServerStatusProvider} 的异常翻译成
 * {@link ErrorDescriptor}（设计文档 6.2）。
 * <p>
 * 这是 {@code ServiceException} 及一切「自带业务码」异常的归属路径。v1.0 下这类逻辑散落在
 * {@code @ExceptionHandler(Exception.class)} 兜底分支里（{@code handleImplementException} +
 * 因果链爬取）；v2.0 收口到本 Mapper，配合 {@link ExceptionMapperChain} 的因果链遍历，
 * 「异常本身实现 ServerStatus」和「cause 链里含 ServerStatus」两种情况都被统一覆盖。
 *
 * <h3>order = -900</h3>
 * <p>
 * 排在 {@code ExceptionClassAnnotationMapper}(-1000) 之后、业务 Mapper 之前。语义：
 * 异常类上的 {@code @ExceptionResponse} 优先（业务显式声明覆盖默认码）；其次才轮到
 * 「异常自己就是个 ServerStatus」这一兜底翻译。
 *
 * <h3>因果链遍历</h3>
 * <p>
 * 本方法只判断「传入的这一个异常节点」是否实现 {@link ServerStatus} / {@link ServerStatusProvider}；
 * 沿 cause 链往上找的工作由 {@code ExceptionMapperChain} 完成——它会依次把每个 cause 节点喂给链上
 * 所有 Mapper。所以这里<b>不</b>需要自己写 {@code getCause()} 循环。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public class ServerStatusMapper implements ExceptionMapper {

    @Override
    public int order() {
        return -900;
    }

    @Nullable
    @Override
    public ErrorDescriptor map(Throwable ex, ExceptionMapperContext ctx) {
        // 情况一：异常本身直接实现 ServerStatus（如 ServiceException）。
        if (ex instanceof ServerStatus status) {
            return ErrorDescriptor.of(status.getCode(), status.getMessage(), ex).build();
        }
        // 情况二：异常通过 ServerStatusProvider 间接携带状态码。
        if (ex instanceof ServerStatusProvider provider) {
            ServerStatus status = provider.getServerStatus();
            return ErrorDescriptor.of(status.getCode(), status.getMessage(), ex).build();
        }
        // 两种都不是 → 轮到链上下一个 Mapper。
        return null;
    }

}
