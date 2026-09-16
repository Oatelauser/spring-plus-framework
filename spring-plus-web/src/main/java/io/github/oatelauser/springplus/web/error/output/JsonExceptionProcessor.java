package io.github.oatelauser.springplus.web.error.output;

import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import io.github.oatelauser.springplus.web.error.descriptor.ExceptionContext;
import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;
import io.github.oatelauser.springplus.web.response.SimpleResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * JSON 协议渲染器（v3.0，设计文档 4.4）。
 * <p>
 * 把 {@link ErrorDescriptor} 翻译成 {@code ResponseEntity<SimpleResponse>}（或自定义 body）。
 * 渲染优先级：
 * <ol>
 *   <li>描述对象带 {@code bodyCustomizer}（注解声明、且其 {@code transform} 返回非 null）→
 *       用自定义对象做 body，绕过 {@link SimpleResponse} 结构（典型：对接微信返回 {@code errcode/errmsg}）。</li>
 *   <li>否则 → 默认渲染：{@link SimpleResponse#fail(String, String, java.util.Map)} 作为 body。</li>
 * </ol>
 *
 * <h3>v3.0：状态码读 {@code statusIntent}，不再下钻 JsonErrorHint</h3>
 * <p>
 * v2.x 的 HTTP 状态码装在 {@code JsonErrorHint.httpStatus}（协议元数据）里，NDJSON 跟着复用
 * JSON hint。v3.0 状态码升维为协议无关的 {@link ErrorDescriptor#getStatusIntent()}——
 * JSON 全程有效（本渲染器只在响应未提交时被引擎调用），{@code null} 表示未指定，
 * 按协议缺省 200（业务码承载错误语义，兼容 v1.0 风格）。sealed hint 家族在 JSON 侧
 * 已无专属参数，自然消失。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public class JsonExceptionProcessor implements ExceptionOutputProcessor {

    @Override
    public OutputProtocol protocol() {
        return OutputProtocol.HTTP_JSON;
    }

    @Override
    public Object handle(ErrorDescriptor descriptor, ExceptionContext ctx) {
        // 1. 自定义响应体优先（如对接第三方要求的特殊结构），返回 null 回退默认。
        Object responseBody = null;
        if (descriptor.getBodyCustomizer() != null) {
            responseBody = descriptor.getBodyCustomizer().transform(ctx);
        }

        // 2. 默认渲染：ErrorDescriptor → SimpleResponse。
        if (responseBody == null) {
            responseBody = SimpleResponse.fail(descriptor.getCode(), descriptor.getMessage(), descriptor.getDetails());
        }

        // 3. 状态码：协议无关意图（statusIntent），null → 缺省 200。
        HttpStatus status = descriptor.getStatusIntent() != null ? descriptor.getStatusIntent() : HttpStatus.OK;
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
    }

}
