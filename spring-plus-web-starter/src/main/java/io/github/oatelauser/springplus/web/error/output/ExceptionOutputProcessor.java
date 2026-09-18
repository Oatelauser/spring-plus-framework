package io.github.oatelauser.springplus.web.error.output;

import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import io.github.oatelauser.springplus.web.error.descriptor.ExceptionContext;
import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;

/**
 * 协议适配器：把 {@link ErrorDescriptor} 翻译为协议特定的输出（设计文档 4.4）。
 * <p>
 * 每个协议一个实现：JSON 走 {@code ResponseEntity<SimpleResponse>}，SSE 走 {@code SseEmitter} 事件。
 * 引擎按 {@link #protocol()} 把渲染器装进 {@code Map}，运行期查表分发。
 * <p>
 * 这是「描述错误」与「渲染错误」解耦的最后一环：所有协议无关的语义都装在 {@link ErrorDescriptor} 里，
 * 本接口的实现只关心「怎么把它写到具体的协议传输层」。未来加 WebSocket 只需新增一个实现，描述层完全复用。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public interface ExceptionOutputProcessor {

    /**
     * 本渲染器服务的协议。
     */
    OutputProtocol protocol();

    /**
     * 把错误描述渲染为协议特定输出。
     *
     * @param descriptor 错误描述
     * @param ctx        渲染上下文（含 request / handlerMethod / protocol）
     * @return 协议特定输出（JSON: {@code ResponseEntity}；SSE: {@code ResponseEntity<SseEmitter>}）
     */
    Object handle(ErrorDescriptor descriptor, ExceptionContext ctx);

    /**
     * 响应已提交时，本渲染器是否仍能写出「协议内错误」。
     * <p>
     * 流一旦开始（首字节已 flush），HTTP 状态码与响应头即已发出，普通渲染器无法再改写响应。
     * 但<b>逐行流式协议</b>（NDJSON）可以在已提交的流上追加一条错误记录，让客户端在逐行解析中
     * 感知失败，而不是把「半截流」当正常结束。
     * <ul>
     *   <li>NDJSON → true：{@code NdjsonExceptionProcessor} 补写一行错误 JSON。</li>
     *   <li>JSON / SSE → false（默认）：SSE 流内错误由 B/C 档
     *       {@code SseExceptionEmitter} / {@code SseConnection} 在业务异步线程内处理。</li>
     * </ul>
     *
     * @since 2.1
     */
    default boolean handlesCommitted() {
        return false;
    }

}
