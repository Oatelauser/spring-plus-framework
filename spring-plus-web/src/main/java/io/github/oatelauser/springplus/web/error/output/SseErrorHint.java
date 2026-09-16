package io.github.oatelauser.springplus.web.error.output;

import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;
import org.jspecify.annotations.Nullable;

/**
 * SSE 协议专属渲染参数（v3.0：随 {@link ErrorHint} 迁入 output 包，瘦身）。
 * <p>
 * 仅承载 SSE 事件帧的真实字段：
 * <ul>
 *   <li>{@code event}：错误事件名（缺省回落全局配置 {@code defaultEventName}）</li>
 *   <li>{@code retry}：建议客户端重连间隔毫秒（-1 = 不输出）</li>
 * </ul>
 * 原第三字段 {@code handshakeStatus} 已删除——它与 JSON 的 httpStatus 同义，v3.0 统一升维为
 * 协议无关的 {@code ErrorDescriptor.statusIntent}（经 {@code @ExceptionResponse#httpStatus()} 桥接）。
 *
 * @param event 错误事件名；null 表示未指定，处理器回落全局缺省事件名
 * @param retry 重连间隔毫秒；-1 表示不输出 retry 字段
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public record SseErrorHint(@Nullable String event, long retry) implements ErrorHint {

    @Override
    public OutputProtocol protocol() {
        return OutputProtocol.HTTP_SSE;
    }
}
