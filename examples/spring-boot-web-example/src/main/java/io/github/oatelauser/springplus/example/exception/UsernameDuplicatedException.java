package io.github.oatelauser.springplus.example.exception;

import io.github.oatelauser.springplus.web.error.annotation.ExceptionResponse;

import java.io.Serial;

/**
 * 测试异常：在<b>异常类上</b>贴 {@link ExceptionResponse}（v2.0 UC-3 验证）。
 * <p>
 * 验证由 {@code ExceptionClassAnnotationMapper}（order = -1000）自动识别异常类上的注解，
 * Controller 方法<b>无须重复声明</b>即可命中 {@code B0204}。同时验证占位符
 * {@code {exception}} 的运行期替换（设计 12.3.1 / `materialize`）。
 *
 * <h3>预期响应</h3>
 * <pre>
 *   HTTP 200 + JSON
 *   {"code":"B0204","data":null,"details":{},"message":"用户名已存在","success":false}
 * </pre>
 * 日志层应得到 WARN 级别（业务异常体系，{@code ExceptionLogPolicyTable} 默认 WARN，不打堆栈）。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
@ExceptionResponse(code = "B0204", msg = "用户名已存在")
public class UsernameDuplicatedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 消息不携带用户名原值（脱敏红线：错误消息不含请求值——V18 示例净化）。
     * {exception} 占位符的演示见 {@code /v2-test/placeholder} 端点（消息模板来自注解属性）。
     */
    public UsernameDuplicatedException() {
        super("用户名已存在");
    }
}
