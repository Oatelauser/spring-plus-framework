package io.github.oatelauser.springplus.web.error.descriptor;

/**
 * 响应协议类型枚举。
 * <p>
 * 这是统一错误处理 v2.0 引入的「协议适配」维度的核心标识。异常处理流程本身不感知具体协议，
 * 所有协议特化代码集中在 {@code ExceptionOutputProcessor} 实现里；本枚举的作用是在流程的三个关键节点
 * 做「按协议分流」：
 * <ul>
 *   <li><b>协议探测</b>（{@code HandlerExceptionProtocolProcessor}）：启动期根据方法注解 / {@code produces} /
 *       返回值类型预解析每个 {@code HandlerMethod} 的协议，运行期 O(1) 查表。</li>
 *   <li><b>注解协议过滤</b>：{@code @JsonExceptionResponse} / {@code @SseExceptionResponse} 通过
 *       元注解 {@code @ExceptionResponse(protocols = ...)} 锁死自己的协议，仅在该协议下命中。</li>
 *   <li><b>渲染分发</b>：引擎根据协议选择对应的 {@code ExceptionOutputProcessor}（JSON 走
 *       {@code ResponseEntity}，SSE 走 {@code SseEmitter}）。</li>
 * </ul>
 * <p>
 * 已实现 {@link #HTTP_JSON}、{@link #HTTP_SSE}、{@link #NDJSON}；接入新协议 = 新增枚举值 +
 * 一个 {@code ExceptionOutputProcessor} 实现（容器自动装配进引擎），描述层
 * （{@link ErrorDescriptor}）完全复用。<b>不预占未实现的枚举值</b>——v2.1 清理了占位的
 * {@code WEBSOCKET}（无渲染器、无识别规则，只有枚举名）：缺渲染器的协议若被识别到，
 * 引擎会降级 JSON 渲染并 ERROR 落盘，但空占位只会掩盖「协议做了一半」的事实。
 * <p>
 * <b>不放 gRPC</b>：等真接入 spring-grpc 时再加，避免提前引入未经验证的协议维度。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public enum OutputProtocol {

    /**
     * HTTP JSON 协议——最常见的请求/响应接口，错误以 {@code ResponseEntity<SimpleResponse>} 形式写出。
     * <p>
     * 兼容 v1.0 的「业务码非 200 但 HTTP 200」风格：默认渲染下 HTTP 状态码仍为 200，
     * 除非业务通过 {@code @JsonExceptionResponse(httpStatus = ...)} 显式指定真实状态码。
     */
    HTTP_JSON,

    /**
     * HTTP SSE（Server-Sent Events）协议——流式推送接口，错误以 {@code app-error} 事件形式写出。
     * <p>
     * 这是 v2.0 的核心改造目标之一：v1.0 下 SSE 接口报错会返回 JSON，与流协议不一致；
     * v2.0 通过协议探测让 SSE 接口自动获得 SSE 错误事件。
     */
    HTTP_SSE,

    /**
     * NDJSON（Newline Delimited JSON）流式协议——逐行 JSON 流式接口（v2.1 做实）。
     * <p>
     * 接口以 {@code produces = "application/x-ndjson"} 声明（或 Accept 头精确命中）即识别为本协议。
     * 错误输出分两态：
     * <ul>
     *   <li><b>流未开始</b>（响应未提交）：设 HTTP 状态码 + 写一行 {@code SimpleResponse} 错误 JSON。</li>
     *   <li><b>流已开始</b>（已写出数据行）：在已提交的流上<b>补写一行错误记录</b>再收流——
     *       客户端逐行解析时能感知失败，而不是把「半截流」当正常结束。</li>
     * </ul>
     * 实现见 {@code NdjsonExceptionProcessor}；数据写入用 {@code StreamWriterFactory.ndjson(response)}。
     */
    NDJSON
}
