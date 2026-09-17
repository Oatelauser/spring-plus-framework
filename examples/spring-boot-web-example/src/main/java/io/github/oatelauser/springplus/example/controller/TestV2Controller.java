package io.github.oatelauser.springplus.example.controller;

import io.github.oatelauser.springplus.web.error.ServiceException;
import io.github.oatelauser.springplus.web.error.annotation.JsonExceptionResponse;
import io.github.oatelauser.springplus.web.error.annotation.NdjsonExceptionResponse;
import io.github.oatelauser.springplus.web.error.annotation.SseExceptionResponse;
import io.github.oatelauser.springplus.web.error.descriptor.LogStackPolicy;
import io.github.oatelauser.springplus.web.error.sse.SseConnection;
import io.github.oatelauser.springplus.web.error.sse.SseConnectionFactory;
import io.github.oatelauser.springplus.web.error.sse.SseExceptionEmitter;
import io.github.oatelauser.springplus.web.response.BusinessStatus;
import io.github.oatelauser.springplus.web.response.SimpleResponse;
import io.github.oatelauser.springplus.web.stream.ChunkStreamWriter;
import io.github.oatelauser.springplus.web.stream.FileDownloadWriter;
import io.github.oatelauser.springplus.web.stream.NdjsonStreamWriter;
import io.github.oatelauser.springplus.web.stream.HttpWriterFactory;
import io.github.oatelauser.springplus.example.dto.EchoCmd;
import io.github.oatelauser.springplus.example.exception.RateLimit2Exception;
import io.github.oatelauser.springplus.example.exception.RateLimitException;
import io.github.oatelauser.springplus.example.exception.ResourceNotFoundException;
import io.github.oatelauser.springplus.example.exception.UsernameDuplicatedException;
import io.github.oatelauser.springplus.example.output.WechatStyleOutput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * v2.0 统一错误处理验收测试 Controller。
 * <p>
 * 14 个测试端点逐一覆盖设计文档第 14 章的全部验收用例（UC-1 ~ UC-10）+ 三档 SSE
 * （A 握手 / B 流内工具 / C 连接封装）+ 占位符 + 日志策略 + 自定义渲染器。
 * 业务方接入 v2.0 时可参考本文件作为标准对照样例。
 * <p>
 * v2.1 追加编程式流式响应四件套（{@code io.github.oatelauser.springplus.web.stream} 包）：
 * Chunk 按块 / NDJSON 逐行 / NDJSON 流内异常自动补错误行 / 文件下载。
 * v2.2 追加 {@code @NdjsonExceptionResponse} 派生注解：NDJSON 错误行的声明式定制。
 * v3.0 注解 API 不变，底层更名对齐：自定义渲染 SPI 更名 {@code ExceptionBodyCustomizer}
 * （descriptor 字段 {@code outputBase} → {@code bodyCustomizer}）、真实 HTTP 状态码统一升维
 * {@code statusIntent}、协议探测收口 {@code OutputProtocolResolver}。
 * 业务方接入时可参考本文件作为标准对照样例。
 *
 * <h3>路由总表</h3>
 * <pre>
 *   POST /v2-test/json/validate              UC-1  JSON @Valid 失败 → details
 *   POST /v2-test/sse/validate-handshake     UC-2  SSE 握手期 @Valid 失败 → app-error
 *   GET  /v2-test/param-validate             UC-11 非 body 参数约束（方法级校验）
 *   GET  /v2-test/exception-class-annotation UC-3  异常类上贴注解
 *   GET  /v2-test/sse/b-tier                       SSE B 档：流内 try/catch
 *   GET  /v2-test/sse/c-tier                       SSE C 档：连接封装
 *   POST /v2-test/wechat-style               UC-6  自定义 bodyRenderer
 *   GET  /v2-test/rate-limit                 UC-7  第三方 Mapper + details 透出
 *   GET  /v2-test/rate-limit2                UC-7  异常类自身实现 Mapper + details 透出
 *   GET  /v2-test/log/always                 UC-8  日志强制打堆栈（ALWAYS）
 *   GET  /v2-test/log/never                  UC-8  日志强制不打堆栈（NEVER）
 *   POST /v2-test/protocol-conflict          UC-9  方法同时贴 JSON/SSE 派生注解
 *   GET  /v2-test/priority                   UC-10 注解 P0 覆盖 handler defaultDescriptor
 *   GET  /v2-test/json/not-found-status            JSON 真实 HTTP 404
 *   GET  /v2-test/service-exception/with-stack     ServiceException.withStack()
 *   GET  /v2-test/service-exception/with-signal    ServiceException.signal()
 *   GET  /v2-test/placeholder                      消息模板占位符 {exceptionClass}
 *   GET  /v2-test/chunk                            v2.1 按块响应（chunked 文本块）
 *   GET  /v2-test/ndjson                           v2.1 NDJSON 逐行 JSON 流
 *   GET  /v2-test/ndjson/error                     v2.1 NDJSON 流内异常自动补错误行
 *   GET  /v2-test/ndjson/custom-error              v2.2 NDJSON 自定义错误行（@NdjsonExceptionResponse.output）
 *   GET  /v2-test/download                         v2.1 文件下载（中文文件名 RFC 5987）
 * </pre>
 *
 * <h3>不持有数据库依赖</h3>
 * <p>
 * 所有端点都仅靠抛异常驱动验证，<b>不</b>访问 DB / 缓存 / 外部服务，避免依赖污染测试结果。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
@RestController
@Tag(name = "v2.0 错误处理验收测试")
@RequiredArgsConstructor
@RequestMapping("/v2-test")
public class TestV2Controller {

    /**
     * SSE B 档需要：用框架提供的工具往业务自有 emitter 写错误事件。
     */
    private final SseExceptionEmitter sseExceptionEmitter;

    /**
     * SSE C 档需要：连接工厂托管 emitter + executor + 自动错误转换。
     */
    private final SseConnectionFactory sseConnectionFactory;

    /**
     * v2.1 流式响应演示：一行代码拿到「协议头已配好」的写入器
     * （SSE / NDJSON / Chunk / 文件下载四件套）。
     */
    private final HttpWriterFactory streamWriterFactory;

    /**
     * 演示用业务线程池（阿里规约：显式构造 {@link java.util.concurrent.ThreadPoolExecutor}，
     * 有界队列、明确拒绝策略、可读线程名）。
     * <p>
     * 演示场景每次请求只提交一个短任务，容量 16 的有界队列足够；队满时
     * {@code CallerRunsPolicy} 让提交线程自己执行，天然反压而不丢任务。
     */
    private final ExecutorService demoExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16),
            r -> {
                Thread t = new Thread(r, "v2-test-sse");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    // ─────────────────────────────────────────────────────────────
    // UC-1：JSON 接口 @Valid 失败 → SimpleResponse.details 带 violations 列表
    // ─────────────────────────────────────────────────────────────

    /**
     * 期望响应（POST body 只给 name，让 age 成为唯一失败字段）：
     * <pre>{
     *   "code":"A0430","message":"[age] age 不能为空",
     *   "data":null,
     *   "details":{"violations":[{"field":"age","msg":"age 不能为空"}]},
     *   "success":false
     * }</pre>
     * 注意：Spring Framework 7 的内置方法级校验对 {@code @Valid @RequestBody} 是
     * fail-fast——首个字段失败即抛，多字段同时失败时只携带<b>一条</b> violation
     * （顺序不定）。旧版「一次透出全部 violations」的契约上游已移除；框架的
     * 聚合代码不变，但收到的就是单条。空 body 触发时字段不定，故验收样例用
     * 只填 name 的 body 保证确定性。
     */
    @PostMapping("/json/validate")
    @Operation(summary = "UC-1：JSON @Valid 失败")
    public SimpleResponse<EchoCmd> jsonValidate(@Valid @RequestBody EchoCmd cmd) {
        return SimpleResponse.ok(cmd);
    }

    // ─────────────────────────────────────────────────────────────
    // UC-2：SSE 握手期 @Valid 失败 → app-error 事件（不返回 JSON）
    // ─────────────────────────────────────────────────────────────

    /**
     * 期望响应（POST body 只给 name，让 age 成为唯一失败字段——Spring 7 fail-fast
     * 校验下 violations 只有单条，见 {@link #jsonValidate} 注释）：
     * <pre>
     *   HTTP 200, Content-Type: text/event-stream
     *   event: app-error
     *   data: {"code":"A0430","message":"[age] age 不能为空",
     *          "details":{"violations":[{"field":"age","msg":"age 不能为空"}]}}
     * </pre>
     * 注意：返回类型是 {@link SseEmitter}，由
     * {@code OutputProtocolResolver.returnsSse} 启动期识别为 SSE 协议。
     */
    @PostMapping(value = "/sse/validate-handshake", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "UC-2：SSE 握手期 @Valid 失败")
    public SseEmitter sseValidateHandshake(@Valid @RequestBody EchoCmd cmd, HttpServletRequest request) {
        // 校验通过的话也返回 SSE，但本测试主要看异常路径。
        SseConnection conn = sseConnectionFactory.open(request, demoExecutor);
        conn.execute(emitter -> {
            emitter.send(SseEmitter.event().name("ok").data(cmd));
            emitter.complete();
        });
        return conn.emitter();
    }

    // ─────────────────────────────────────────────────────────────
    // UC-11：非 body 参数约束 → HandlerMethodValidationException（v3.1 新增处理器）
    // ─────────────────────────────────────────────────────────────

    /**
     * 期望响应（GET ?page=0）：
     * <pre>{
     *   "code":"A0430","message":"[page] page 必须大于等于 1",
     *   "data":null,
     *   "details":{"violations":[{"field":"page","msg":"page 必须大于等于 1"}]},
     *   "success":false
     * }</pre>
     * Spring 6.1+ 把非 body 参数约束校验改为抛 {@code HandlerMethodValidationException}
     * （不再是 {@code ConstraintViolationException}）；v3.1 起框架将其翻译为与 UC-1
     * 同构的 A0430 + violations。注意与 body 校验（fail-fast 单条）不同，方法级校验
     * 聚合<b>全部</b>参数结果。
     */
    @GetMapping("/param-validate")
    @Operation(summary = "UC-11：非 body 参数约束（方法级校验）")
    public SimpleResponse<Map<String, Integer>> paramValidate(
            @RequestParam @Min(value = 1, message = "page 必须大于等于 1") Integer page) {
        return SimpleResponse.ok(Map.of("page", page));
    }

    // ─────────────────────────────────────────────────────────────
    // UC-3：异常类上贴注解（ExceptionClassAnnotationMapper 验证）
    // ─────────────────────────────────────────────────────────────

    /**
     * Controller 完全不写 {@code @ExceptionResponse}，验证「异常类上的注解」自动命中。
     * 期望响应：{@code {"code":"B0204","message":"用户名已存在", ...}}。
     */
    @GetMapping("/exception-class-annotation")
    @Operation(summary = "UC-3：异常类上贴注解 + 占位符 {exception}")
    public SimpleResponse<Void> testExceptionClassAnnotation() {
        throw new UsernameDuplicatedException();
    }

    // ─────────────────────────────────────────────────────────────
    // SSE B 档：业务自己 try/catch + sseExceptionEmitter.completeWithError
    // ─────────────────────────────────────────────────────────────

    /**
     * 模拟流内异常：异步线程内连发 2 个数据帧，第 3 个时刻意抛异常。
     * <p>
     * 期望客户端按顺序收到：data#1 / data#2 / event=app-error / 流关闭。
     * 这是 v1.0 做不到的：v1.0 流内异常会让客户端只收到「连接断了」，无法区分错误类型。
     */
    @GetMapping(value = "/sse/b-tier", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE B 档：流内异常 + completeWithError")
    public SseEmitter sseBTier(HttpServletRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        demoExecutor.execute(() -> {
            try {
                emitter.send(SseEmitter.event().name("tick").data("frame#1"));
                TimeUnit.MILLISECONDS.sleep(50);
                emitter.send(SseEmitter.event().name("tick").data("frame#2"));
                TimeUnit.MILLISECONDS.sleep(50);
                throw new ServiceException(BusinessStatus.DATA_NOT_EXIST);
            } catch (Throwable ex) {
                // 标准 B 档用法：把流内异常交给框架转 app-error 并 complete emitter。
                sseExceptionEmitter.completeWithError(emitter, ex, request);
            }
        });
        return emitter;
    }

    // ─────────────────────────────────────────────────────────────
    // SSE C 档：SseConnection.execute（零 try/catch 样板）
    // ─────────────────────────────────────────────────────────────

    /**
     * 业务代码不写一句异常处理，{@link SseConnection#execute} 内部自动转换。
     * 期望和 B 档完全一致的输出，但代码量从 ~10 行降到 ~3 行。
     */
    @GetMapping(value = "/sse/c-tier", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE C 档：连接封装零样板")
    public SseEmitter sseCTier(HttpServletRequest request) {
        SseConnection conn = sseConnectionFactory.open(request, demoExecutor);
        conn.execute(emitter -> {
            emitter.send(SseEmitter.event().name("tick").data("frame#1"));
            TimeUnit.MILLISECONDS.sleep(50);
            emitter.send(SseEmitter.event().name("tick").data("frame#2"));
            TimeUnit.MILLISECONDS.sleep(50);
            // 任意异常自动转 app-error，无须任何 try/catch。
            throw new ServiceException(BusinessStatus.DATA_NOT_EXIST);
        });
        return conn.emitter();
    }

    // ─────────────────────────────────────────────────────────────
    // UC-6：自定义 bodyRenderer（微信 errcode/errmsg 风格）
    // ─────────────────────────────────────────────────────────────

    /**
     * 期望响应：{@code {"errcode":-1,"errmsg":"签名校验失败"}}。
     * 注意完全没有 {@code code/message/data/details} 这些 SimpleResponse 字段——
     * 走的是 {@link WechatStyleOutput#transform} 产出的自定义结构。
     */
    @PostMapping("/wechat-style")
    @Operation(summary = "UC-6：自定义 bodyRenderer (微信 errcode/errmsg)")
    @JsonExceptionResponse(value = ServiceException.class, code = "B0001",
            msg = "签名校验失败", output = WechatStyleOutput.class)
    public SimpleResponse<Void> testWechatStyle() {
        throw new ServiceException("B0001", "签名校验失败");
    }

    // ─────────────────────────────────────────────────────────────
    // UC-7：业务自定义 Mapper + details 透出
    // ─────────────────────────────────────────────────────────────

    /**
     * 期望响应：
     * <pre>{"code":"A0429","message":"请求过于频繁","data":null,
     *  "details":{"retryAfterSeconds":30},"success":false}</pre>
     * 验证 {@code RateLimitExceptionMapper}（业务自定义 Mapper）已被自动装配并生效。
     */
    @GetMapping("/rate-limit")
    @Operation(summary = "UC-7：业务 ExceptionMapper + details")
    public SimpleResponse<Void> testRateLimit() {
        throw new RateLimitException("rate exceeded", 30);
    }

    // ─────────────────────────────────────────────────────────────
    // UC-8：日志策略 ALWAYS / NEVER（看后端日志输出）
    // ─────────────────────────────────────────────────────────────

    /**
     * 验证 {@code LogStackPolicy.ALWAYS}：业务异常默认 WARN 不打堆栈，但因注解强制 → 打堆栈。
     * <p>
     * 看日志：应有完整的 {@code java.lang.RuntimeException} 堆栈输出。
     */
    @GetMapping("/log/always")
    @Operation(summary = "UC-8：日志强制 ALWAYS（打堆栈）")
    @JsonExceptionResponse(value = RuntimeException.class, code = "B0001",
            msg = "强制打堆栈测试", logPolicy = LogStackPolicy.ALWAYS)
    public SimpleResponse<Void> testLogAlways() {
        throw new RuntimeException("must print stack trace");
    }

    /**
     * 验证 {@code LogStackPolicy.NEVER}：未知异常按静态分类表本应打 ERROR + 堆栈，
     * 但因注解强制 → 不打堆栈。
     * <p>
     * 看日志：应只有一行简短消息，无堆栈帧。
     */
    @GetMapping("/log/never")
    @Operation(summary = "UC-8：日志强制 NEVER（不打堆栈）")
    @JsonExceptionResponse(value = RuntimeException.class, code = "B0002",
            msg = "强制不打堆栈测试", logPolicy = LogStackPolicy.NEVER)
    public SimpleResponse<Void> testLogNever() {
        throw new RuntimeException("must NOT print stack trace");
    }

    // ─────────────────────────────────────────────────────────────
    // UC-9：协议同时声明 JSON/SSE（启动期 warn + 协议过滤）
    // ─────────────────────────────────────────────────────────────

    /**
     * 同一方法同时贴 JSON 和 SSE 两种派生注解：启动期 {@code OutputProtocolResolver}
     * 会 {@code log.warn} 协议提示冲突，但两条注解仍各自作为<b>协议过滤器</b>工作：
     * <ul>
     *   <li>JSON 协议接口（本路径，produces 默认 JSON）→ 命中 X1</li>
     *   <li>若有 SSE 协议接口抛同一异常 → 命中 X2</li>
     * </ul>
     * 期望响应：{@code {"code":"X1", ...}}。
     */
    @PostMapping("/protocol-conflict")
    @Operation(summary = "UC-9：协议冲突注解（启动 warn + 运行期过滤）")
    @JsonExceptionResponse(value = IllegalStateException.class, code = "X1",
            msg = "JSON 协议命中 X1")
    @SseExceptionResponse(value = IllegalStateException.class, code = "X2",
            msg = "SSE 协议命中 X2")
    public SimpleResponse<Void> testProtocolConflict() {
        throw new IllegalStateException("ambiguous protocol");
    }

    // ─────────────────────────────────────────────────────────────
    // UC-10：注解 P0 优先级覆盖 handler defaultDescriptor（P1）
    // ─────────────────────────────────────────────────────────────

    /**
     * 抛 {@link SQLException}：v1.0 行为是被 {@code @ExceptionHandler(SQLException)} handler
     * 命中，code = {@code C0200}（系统错误）。<br>
     * v2.0 行为是方法级 {@code @JsonExceptionResponse(code="B9999")}（P0）覆盖
     * handler 自己构造的 defaultDescriptor（P1）→ 实际 code = {@code B9999}。
     * <p>
     * 验证设计 8.2 优先级规则：方法/类级注解 &gt; defaultDescriptor &gt; Mapper 链 &gt; 兜底。
     */
    @GetMapping("/priority")
    @Operation(summary = "UC-10：注解 P0 覆盖 defaultDescriptor")
    @JsonExceptionResponse(value = SQLException.class, code = "B9999", msg = "兜底业务码")
    public SimpleResponse<Void> testPriority() throws SQLException {
        throw new SQLException("conn refused");
    }

    // ─────────────────────────────────────────────────────────────
    // 真实 HTTP 状态码：@JsonExceptionResponse(httpStatus = NOT_FOUND)
    // ─────────────────────────────────────────────────────────────

    /**
     * 期望：HTTP 状态码<b>真的</b>是 {@code 404}（不是 v1.0 的「业务码 4xx + HTTP 200」）。
     */
    @GetMapping("/json/not-found-status")
    @Operation(summary = "JSON 真实 HTTP 404 状态码")
    @JsonExceptionResponse(value = ResourceNotFoundException.class, code = "A0404",
            msg = "资源不存在: {exception}", httpStatus = HttpStatus.NOT_FOUND)
    public SimpleResponse<Void> testNotFoundStatus() {
        throw new ResourceNotFoundException("book#42");
    }

    // ─────────────────────────────────────────────────────────────
    // ServiceException.withStack()
    // ─────────────────────────────────────────────────────────────

    /**
     * 业务异常默认不打堆栈；通过 {@link ServiceException#withStack()} 链式开启。
     * <p>
     * 看日志：本接口的 WARN 输出应带完整堆栈，对照 {@link #testRateLimit()} 的 WARN 不带堆栈。
     */
    @GetMapping("/service-exception/with-stack")
    @Operation(summary = "ServiceException.withStack() 强制打堆栈")
    public SimpleResponse<Void> testServiceExceptionWithStack() {
        throw new ServiceException(BusinessStatus.DATA_NOT_EXIST).withStack();
    }

    /**
     * 信号异常：连堆栈捕获都省掉（fillInStackTrace 零成本），日志同样不打堆栈。
     */
    @GetMapping("/service-exception/with-signal")
    @Operation(summary = "ServiceException.signal() 信号通知（不捕获堆栈）")
    public SimpleResponse<Void> testServiceExceptionWithSignal() {
        throw new ServiceException(BusinessStatus.DATA_NOT_EXIST).signal();
    }

    // ─────────────────────────────────────────────────────────────
    // 占位符 {exceptionClass}
    // ─────────────────────────────────────────────────────────────

    /**
     * 验证 {@code messageTemplate} 中的 {@code {exceptionClass}} 启动期被识别为占位符
     * （{@code hasPlaceholder=true}），运行期被替换为异常类的 simpleName。
     * 期望响应 {@code message="发生异常: ArithmeticException"}。
     */
    @GetMapping("/placeholder")
    @Operation(summary = "占位符 {exceptionClass}")
    @JsonExceptionResponse(value = ArithmeticException.class, code = "C9001",
            msg = "发生异常: {exceptionClass}")
    public SimpleResponse<Void> testPlaceholder() {
        // 1/0 → ArithmeticException
        int result = 1 / Integer.parseInt("0");
        return SimpleResponse.ok();
    }

    /**
     * 期望响应：与 {@code /rate-limit} 完全一致，但实现路径不同——
     * {@code RateLimit2Exception} <b>异常类自身实现</b> {@code ExceptionMapper} 接口
     * （无须独立 Mapper Bean），验证异常自带映射的直通路径同样被自动识别。
     */
    @GetMapping("/rate-limit2")
    @Operation(summary = "UC-7：异常类自身实现 ExceptionMapper + details")
    public SimpleResponse<Void> testRateLimit2() {
        throw new RateLimit2Exception("rate exceeded", 30);
    }

    // ─────────────────────────────────────────────────────────────
    // v2.1 编程式流式响应四件套（StreamWriterFactory 演示）
    // ─────────────────────────────────────────────────────────────

    /**
     * 按块响应演示：10 个文本块、每块间隔 200ms，curl 可见渐进式到达。
     * <p>
     * 要点：
     * <ul>
     *   <li>写入器直写 servlet 输出流 + 每块 flush；不设 Content-Length，
     *       容器自动启用 {@code Transfer-Encoding: chunked}</li>
     *   <li>{@code chunk()} 不代设 Content-Type（载体泛化），由本方法显式声明文本类型</li>
     *   <li>同步阻塞写（虚拟线程已启用），无 Emitter / executor 样板</li>
     * </ul>
     */
    @GetMapping("/chunk")
    @Operation(summary = "v2.1：Chunk 按块响应（chunked 传输）")
    public void chunk(HttpServletResponse response) throws Exception {
        response.setContentType("text/plain;charset=UTF-8");
        ChunkStreamWriter writer = streamWriterFactory.chunk(response);
        for (int i = 0; i < 10; i++) {
            TimeUnit.MILLISECONDS.sleep(200);
            writer.writeChunk("chunk #" + i + "\n");
        }
        writer.close();
    }

    /**
     * NDJSON 逐行 JSON 流演示：10 条记录、每条间隔 200ms（LLM token 流 / 日志导出场景）。
     * <p>
     * 要点：{@code produces = "application/x-ndjson"} 同时完成两件事——
     * 声明响应协议；异常发生时让 {@code OutputProtocolResolver} 探测为 NDJSON 协议。
     * <p>
     * 期望响应体（每行一个完整 JSON，以 \n 分隔）：
     * <pre>{"seq":0,"token":"delta-0"}
     * {"seq":1,"token":"delta-1"}
     * ...</pre>
     */
    @GetMapping(value = "/ndjson", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "v2.1：NDJSON 逐行 JSON 流")
    public void ndjson(HttpServletResponse response) throws Exception {
        NdjsonStreamWriter writer = streamWriterFactory.ndjson(response);
        for (int i = 0; i < 10; i++) {
            TimeUnit.MILLISECONDS.sleep(1000);
            writer.writeLine(Map.of("seq", i, "token", "delta-" + i));
        }
        writer.close();
    }

    /**
     * NDJSON 流内异常演示：<b>零样板</b>自动补错误行。
     * <p>
     * 写出 2 条数据后故意抛 {@link ServiceException}——不写任何 try/catch：
     * 异常冒泡到全局处理器，{@code NdjsonExceptionProcessor} 检测到响应已提交，
     * 在既有流上<b>追加一行</b>错误记录（HTTP 状态码与头均已发出，改不了也不需要改）。
     * <p>
     * 期望响应体：
     * <pre>{"seq":0,"token":"delta-0"}
     * {"seq":1,"token":"delta-1"}
     * {"code":"B0101","message":"数据不存在","data":null,"success":false}</pre>
     */
    @GetMapping(value = "/ndjson/error", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "v2.1：NDJSON 流内异常自动补错误行")
    public void ndjsonError(HttpServletResponse response) throws Exception {
        NdjsonStreamWriter writer = streamWriterFactory.ndjson(response);
        writer.writeLine(Map.of("seq", 0, "token", "delta-0"));
        TimeUnit.MILLISECONDS.sleep(5000);
        writer.writeLine(Map.of("seq", 1, "token", "delta-1"));
        TimeUnit.MILLISECONDS.sleep(200);
        throw new ServiceException(BusinessStatus.DATA_NOT_EXIST);
    }

    /**
     * NDJSON 自定义错误行演示（v2.2）：{@code @NdjsonExceptionResponse.output} 定制<b>补写错误行</b>的 body。
     * <p>
     * 与 {@code /ndjson/error} 的对照：那边补写的是默认 {@code SimpleResponse} 行；这边经
     * {@link WechatStyleOutput} 转成微信 {@code errcode/errmsg} 格式——两条路径都走
     * {@code NdjsonExceptionProcessor.resolveBody()}，差异只在 descriptor 里有没有 bodyCustomizer。
     * <p>
     * 本方法特意让注解 {@code msg} 与 {@code BusinessStatus.DATA_NOT_EXIST} 自带文案<b>不同</b>——
     * 响应里 {@code errmsg} 是注解值即证明 P0 注解命中（而非 P2 兜底）。
     * <p>
     * 两个语义边界（设计文档 8.4）：
     * <ul>
     *   <li>{@code output} 指向的类必须注册为 Spring Bean（启动期 {@code getBean} fail-fast）；
     *       {@code transform} 返回 null 会回落默认 {@code SimpleResponse} 行。</li>
     *   <li>{@code httpStatus} 仅在<b>流未开始</b>（响应未提交）时有效；本例数据行已写出、
     *       响应已提交，状态码与头均已发出不可改。</li>
     * </ul>
     * <p>
     * 期望响应体：
     * <pre>{"seq":0,"token":"delta-0"}
     * {"seq":1,"token":"delta-1"}
     * {"errcode":-1,"errmsg":"微信回调签名校验失败"}</pre>
     */
    @GetMapping(value = "/ndjson/custom-error", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @NdjsonExceptionResponse(value = ServiceException.class, code = "B0001", msg = "微信回调签名校验失败",
            output = WechatStyleOutput.class)
    @Operation(summary = "v2.2：NDJSON 自定义错误行（@NdjsonExceptionResponse.output）")
    public void ndjsonCustomError(HttpServletResponse response) throws Exception {
        NdjsonStreamWriter writer = streamWriterFactory.ndjson(response);
        writer.writeLine(Map.of("seq", 0, "token", "delta-0"));
        writer.writeLine(Map.of("seq", 1, "token", "delta-1"));
        throw new ServiceException(BusinessStatus.DATA_NOT_EXIST);
    }

    /**
     * 文件下载演示：中文文件名 + Content-Type 按扩展名推断（.csv → text/csv）。
     * <p>
     * 浏览器命中后弹下载框，落盘名「租户导出报表.csv」（RFC 5987 百分号编码）；
     * curl 加 {@code -o} 参数即可验证。
     */
    @GetMapping("/download")
    @Operation(summary = "v2.1：文件下载（中文文件名 RFC 5987）")
    public void download(HttpServletResponse response) throws Exception {
        FileDownloadWriter writer = streamWriterFactory.download(response, "租户导出报表.csv");
        String csv = "id,姓名,房间号\n1,张三,A-101\n2,李四,B-202\n";
        writer.write(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        writer.finish();
    }

}
