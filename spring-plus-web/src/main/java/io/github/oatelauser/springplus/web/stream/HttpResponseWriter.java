package io.github.oatelauser.springplus.web.stream;

import io.github.oatelauser.springplus.web.error.ServiceException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.github.oatelauser.springplus.web.response.ClientStatus.CONTENT_TYPE_NOT_SUPPORTED;

/**
 * 自定义响应{@link HttpServletResponse}返回信息
 *
 * @author <a href="mailto:545896770@qq.com">DearYang</a>
 * @date 2023-04-20
 * @since 1.0
 */
public class HttpResponseWriter {

    private final List<HttpMessageConverter<?>> messageConverters;

    public HttpResponseWriter(RequestMappingHandlerAdapter requestMappingHandlerAdapter) {
        this.messageConverters = requestMappingHandlerAdapter.getMessageConverters();
    }

    /**
     * @see #writeTo(Object, MediaType, HttpServletResponse)
     */
    public void writeTo(Object body, HttpServletResponse response) throws IOException {
        String content = response.getContentType();
        if (content == null) {
            content = response.getHeader(HttpHeaders.CONTENT_TYPE);
        }
        MediaType contentType = MediaType.parseMediaType(content);
        this.writeTo(body, contentType, response);
    }

    /**
     * 写JSON数据到响应
     *
     * @see #writeTo(Object, MediaType, HttpServletResponse)
     */
    public void jsonWriteTo(Object body, HttpServletResponse response) throws IOException {
        this.writeTo(body, MediaType.APPLICATION_JSON, response);
    }

    /**
     * 写消息到响应
     *
     * @param body        相应内容
     * @param contentType 请求类型
     * @param response    {@link HttpServletResponse}
     * @throws IOException IO异常
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void writeTo(Object body, MediaType contentType, HttpServletResponse response) throws IOException {
        for (HttpMessageConverter converter : messageConverters) {
            if (converter.canWrite(body.getClass(), contentType)) {
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType(contentType.toString());
                converter.write(body, contentType, new ServletServerHttpResponse(response));
                return;
            }
        }
        throw new ServiceException(CONTENT_TYPE_NOT_SUPPORTED);
    }

}
