package io.github.oatelauser.springplus.example.exception;

import java.io.Serial;

/**
 * 测试异常：表示资源不存在（v2.0 UC-13 验证）。
 * <p>
 * 用于验证 {@code @JsonExceptionResponse(httpStatus = NOT_FOUND)} 把<b>真实 HTTP 404</b>
 * 写到 {@code ResponseEntity}（设计 5.3）。区别于 v1.0 的「业务码 4xx 但 HTTP 200」。
 *
 * <h3>预期响应</h3>
 * <pre>
 *   HTTP 404 + JSON
 *   {"code":"A0404","message":"资源不存在: book#42","success":false}
 * </pre>
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public class ResourceNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String resource) {
        super(resource);
    }
}
