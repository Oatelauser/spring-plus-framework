package io.github.oatelauser.springplus.web.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 记录请求和响应报文的抽象过滤器。
 * <p>
 * 子类通过实现 {@link #recordBody} 决定如何处理（日志、审计、持久化等）。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-24
 * @since 1.0
 */
@Slf4j
@Getter
@Setter
public abstract class AbstractHttpTraceFilter extends OncePerRequestFilter {

    /**
     * 默认最大缓存 512 KB
     */
    private static final int DEFAULT_MAX_PAYLOAD_SIZE = 1024 * 512;

    /**
     * 需要记录 body 的 HTTP 方法集合，子类可覆盖
     */
    private static final Set<String> DEFAULT_RECORD_METHODS = Set.of(HttpMethod.POST.name(),
            HttpMethod.PUT.name(), HttpMethod.PATCH.name());

    private int maxPayloadSize = DEFAULT_MAX_PAYLOAD_SIZE;
    private Set<String> recordMethods = DEFAULT_RECORD_METHODS;
    private static final String OVERSIZED_PAYLOAD = "[payload too large]";

    /**
     * 请求/响应旁录的最大字节数（超限记录占位说明，不影响本体）
     */
    public void setMaxPayloadSize(int maxPayloadSize) {
        this.maxPayloadSize = maxPayloadSize;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        // 是否需要记录请求
        boolean shouldRecord = this.shouldRecordBody(request);
        if (!shouldRecord) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest requestToUse = request;
        if (!isAsyncDispatch(request) && !(request instanceof ContentCachingRequestWrapper)) {
            requestToUse = new ContentCachingRequestWrapper(request, this.maxPayloadSize);
        }

        HttpServletResponse responseToUse = response;
        if (!(response instanceof BoundedTeeResponseWrapper)) {
            responseToUse = new BoundedTeeResponseWrapper(response, this.maxPayloadSize);
        }

        int status = HttpStatus.INTERNAL_SERVER_ERROR.value();
        try {
            chain.doFilter(requestToUse, responseToUse);
            status = responseToUse.getStatus();
        } finally {
            boolean asyncRequest = isAsyncStarted(requestToUse);
            if (!asyncRequest && isCandidateStatus(status)) {
                String reqPayload = extractRequestPayload(requestToUse);
                String resPayload = extractResponsePayload(responseToUse);
                recordBody(requestToUse, responseToUse, reqPayload, resPayload);
            }
        }
    }

    /**
     * 判断当前请求是否需要记录 body
     */
    protected boolean shouldRecordBody(HttpServletRequest request) {
        if (!this.recordMethods.contains(request.getMethod())) {
            return false;
        }
        // multipart 上传体不旁录（V16：大文件/二进制不进缓存与日志）
        String contentType = request.getContentType();
        return contentType == null || !contentType.toLowerCase().startsWith("multipart/");
    }

    /**
     * 提取请求体内容
     */
    private String extractRequestPayload(HttpServletRequest request) {
        ContentCachingRequestWrapper wrapper =
                WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (wrapper == null) {
            return "";
        }
        return toPayloadString(wrapper.getContentAsByteArray(), getCharset(wrapper.getCharacterEncoding()));
    }

    /**
     * 提取响应体旁录片段（有界旁录：超限/二进制由包装器返回占位说明；响应本体已直写客户端）
     */
    private String extractResponsePayload(HttpServletResponse response) {
        if (response instanceof BoundedTeeResponseWrapper tee) {
            return tee.payload();
        }
        return "";
    }

    /**
     * 安全地将字节数组转为字符串
     */
    private String toPayloadString(byte[] bytes, Charset charset) {
        if (bytes.length == 0) {
            return "";
        }
        if (bytes.length > this.maxPayloadSize) {
            return OVERSIZED_PAYLOAD;
        }
        return new String(bytes, charset);
    }

    /**
     * 安全获取字符集，回退到 UTF-8
     */
    private Charset getCharset(String encoding) {
        if (encoding == null || encoding.equalsIgnoreCase(StandardCharsets.UTF_8.displayName())) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    // ==================== 子类扩展点 ====================

    /**
     * 是否为需要记录的响应状态码
     *
     * @param status HTTP 响应状态码
     * @return 是否记录
     */
    protected abstract boolean isCandidateStatus(int status);

    /**
     * 记录请求和响应内容
     *
     * @param request         原始请求对象（可从中获取 URI、Header 等）
     * @param response        原始响应对象（可从中获取 Header 等）
     * @param requestPayload  请求体字符串
     * @param responsePayload 响应体字符串
     */
    protected abstract void recordBody(HttpServletRequest request, HttpServletResponse response,
            String requestPayload, String responsePayload);

}
