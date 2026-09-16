package io.github.oatelauser.springplus.boot.client.core;

import io.github.oatelauser.springplus.boot.client.ApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.ref.Cleaner;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式响应体
 * <p>
 * 用于 {@link ApiClient#stream(ApiRequest)} 返回值，承载尚未读完、连接仍开放的响应。
 * <b>必须以 try-with-resources 使用</b>，否则连接会泄漏到连接池外。
 * </p>
 *
 * <p><b>使用示例（SSE / LLM 流式）：</b></p>
 * <pre>{@code
 * ApiResponse<StreamingBody> resp = client.stream(ApiRequest.post("/v1/chat").json(req).build());
 * try (StreamingBody body = resp.requireBody()) {
 *     if (!resp.isSuccessful()) {
 *         // 错误路径：调用方自己读流处理
 *         byte[] err = body.rawStream().readAllBytes();
 *         throw new MyApiException(resp.getStatusCode(), new String(err, UTF_8));
 *     }
 *     try (Reader reader = body.rawReader();
 *          BufferedReader br = new BufferedReader(reader)) {
 *         String line;
 *         while ((line = br.readLine()) != null) {
 *             // 调用方自己解析 SSE / NDJSON / 自定义协议
 *             handleChunk(line);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><b>互斥消费规则：</b>{@link #rawStream()} 和 {@link #rawReader()} / {@link #rawReader(Charset)}
 * <b>只能调用一个</b>。底层是同一个 socket InputStream，不允许多路读。第二次调用会抛
 * {@link IllegalStateException}。</p>
 *
 * <p><b>资源泄漏兜底：</b>使用 JDK {@link Cleaner} 注册 GC 触发的关流回调。
 * 调用方主动 {@link #close()} 时会注销该回调（零额外开销）；
 * 调用方未关而被 GC 时，{@link Cleaner} 会强制关流并打印 {@code log.error}（含构造时的调用栈），
 * 用于在压测 / 灰度阶段快速暴露泄漏点。这是事故防御机制，<b>不应作为日常依赖</b>。</p>
 *
 * <p><b>HTTP 错误状态码下的 body：</b>流式路径下，4xx / 5xx 响应仍返回 StreamingBody
 * （而非 {@code null}），调用方需先用 {@link ApiResponse#isSuccessful()} 判断后再决定如何读。
 * 这与非流式 {@link ApiClient#execute} 的行为不同 —— 框架不会"擅自"读错误 body。</p>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-06-16
 * @since 1.0
 */
@Slf4j
public final class StreamingBody implements AutoCloseable {

    /**
     * 全局共享的 Cleaner 实例
     * <p>
     * Cleaner 内部用单个守护线程处理所有注册的 cleanable，
     * 全局共享避免每个 StreamingBody 实例都起线程。
     * </p>
     */
    private static final Cleaner CLEANER = Cleaner.create();

    private final ConvertibleClientHttpResponse response;
    private final HttpHeaders headers;
    private final String requestId;

    /**
     * Cleaner 注册返回的 Cleanable 句柄
     * <p>调用方主动 close 时通过它注销 GC 兜底。</p>
     */
    private final Cleaner.Cleanable cleanable;

    /**
     * Cleaner 状态对象（持有关流逻辑 + 泄漏标志）
     * <p><b>注意：</b>此字段必须由 StreamingBody 持有，但其引用的对象
     * <b>不能反向持有 StreamingBody 实例</b>，否则 GC 永不可达，Cleaner 永不触发。
     * 这是 Cleaner 的强约束，用 static 内部类保证。</p>
     */
    private final CleanupState cleanupState;

    /**
     * 标记流是否已被消费（rawStream / rawReader 互斥）
     */
    private final AtomicBoolean consumed = new AtomicBoolean(false);

    /**
     * 标记 close 是否已被调用（保证幂等）
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * 构造 StreamingBody 并注册 GC 兜底
     *
     * @param response  底层响应（持有 socket / 连接，close 时归还连接池）
     * @param requestId 请求 ID，用于泄漏日志关联
     */
    public StreamingBody(ConvertibleClientHttpResponse response, String requestId) {
        this.response = Objects.requireNonNull(response, "response must not be null");
        this.headers = response.getHeaders();
        this.requestId = requestId;
        // 抓取构造时的调用栈，仅在 GC 兜底触发时打印
        StackTraceElement[] openedAt = new Throwable().getStackTrace();
        this.cleanupState = new CleanupState(response, requestId, openedAt);
        this.cleanable = CLEANER.register(this, this.cleanupState);
    }

    /**
     * 原始字节流（一等公民）
     * <p>
     * 如果上游响应启用了 gzip 等 transfer encoding，
     * 底层 {@link org.springframework.http.client.ClientHttpRequestFactory} 通常会自动解码，
     * 这里返回的是已解码的字节流。
     * </p>
     *
     * @return 响应体字节流；调用方读完不需要单独关闭它，关闭 {@link StreamingBody} 即可
     * @throws IllegalStateException 流已被 {@link #rawStream()} / {@link #rawReader} 消费过 / 已 close
     * @throws IOException           底层 IO 错误
     */
    public InputStream rawStream() throws IOException {
        ensureOpenAndMarkConsumed();
        return response.getBody();
    }

    /**
     * 原始字符流（默认 UTF-8）
     *
     * @see #rawReader(Charset)
     */
    public Reader rawReader() throws IOException {
        return rawReader(StandardCharsets.UTF_8);
    }

    /**
     * 原始字符流（指定 charset）
     * <p>
     * <b>注意：</b>charset 应从 {@link #headers()} 的 {@code Content-Type} 中读取最准确，
     * 例如 {@code text/event-stream; charset=utf-8}。本方法不做自动推断。
     * </p>
     *
     * @param charset 字符集
     * @return 字符流；关闭 {@link StreamingBody} 即可，无需单独关 Reader
     * @throws IllegalStateException 流已被消费 / 已 close
     * @throws IOException           底层 IO 错误
     */
    public Reader rawReader(Charset charset) throws IOException {
        Objects.requireNonNull(charset, "charset must not be null");
        ensureOpenAndMarkConsumed();
        return new InputStreamReader(response.getBody(), charset);
    }

    /**
     * 响应头
     * <p>
     * 调用方据此判断 {@code Content-Type}（{@code text/event-stream}
     * / {@code application/x-ndjson} / {@code application/json} 等）
     * 决定如何解析流。可重复调用，不消费流。
     * </p>
     */
    public HttpHeaders headers() {
        return headers;
    }

    /**
     * 关闭流并归还底层连接到连接池
     * <p>
     * 幂等：多次调用安全。调用此方法后 {@link #rawStream()} / {@link #rawReader} 抛 IllegalStateException。
     * </p>
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return; // 幂等
        }
        // 1. 标记已关，让 Cleaner 兜底跳过泄漏告警
        cleanupState.closedNormally = true;
        // 2. 触发清理（关流 + 注销 Cleaner 注册）
        cleanable.clean();
    }

    private void ensureOpenAndMarkConsumed() {
        if (closed.get()) {
            throw new IllegalStateException(
                    "StreamingBody already closed, requestId=" + requestId);
        }
        if (!consumed.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "StreamingBody already consumed (rawStream/rawReader can only be called once), requestId="
                            + requestId);
        }
    }

    /**
     * Cleaner 状态对象
     * <p>
     * <b>必须是 static 嵌套类，且只能持有"清理所需的最小状态"</b>，
     * 不能反向引用 StreamingBody（否则 GC 不可达，Cleaner 永不触发，泄漏检测失效）。
     * </p>
     */
    private static final class CleanupState implements Runnable {

        private final ConvertibleClientHttpResponse response;
        private final String requestId;
        private final StackTraceElement[] openedAt;

        /**
         * 由外部 {@link StreamingBody#close()} 在主动关闭时设为 true，
         * 用于区分"主动关闭"（不打告警）和"GC 兜底"（要打告警）。
         * <p>
         * volatile：close() 可能在不同线程调用，Cleaner 在专用线程读取。
         * </p>
         */
        volatile boolean closedNormally = false;

        CleanupState(ConvertibleClientHttpResponse response, String requestId,
                     StackTraceElement[] openedAt) {
            this.response = response;
            this.requestId = requestId;
            this.openedAt = openedAt;
        }

        @Override
        public void run() {
            try {
                response.close();
            } catch (Exception e) {
                if (!closedNormally) {
                    log.error("Failed to close leaked StreamingBody, requestId={}", requestId, e);
                }
                return;
            }
            if (!closedNormally) {
                // GC 兜底触发：泄漏告警
                log.error("StreamingBody leaked! requestId={}, openedAt:\n{}",
                        requestId, formatStack(openedAt));
            }
        }

        private static String formatStack(StackTraceElement[] stack) {
            if (stack == null || stack.length == 0) {
                return "  (no stack trace available)";
            }
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (int i = 0; i < stack.length && shown < 8; i++) {
                StackTraceElement e = stack[i];
                String cls = e.getClassName();
                // 跳过 StreamingBody 自身和 CleanupState 的栈帧
                if (cls.equals(StreamingBody.class.getName())
                        || cls.equals(CleanupState.class.getName())) {
                    continue;
                }
                sb.append("\tat ").append(e).append('\n');
                shown++;
            }
            return sb.toString();
        }
    }

}
