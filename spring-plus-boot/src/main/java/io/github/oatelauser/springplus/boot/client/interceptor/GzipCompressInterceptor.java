package io.github.oatelauser.springplus.boot.client.interceptor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

/**
 * GZIP压缩请求拦截器
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-19
 * @since 1.0
 */
public class GzipCompressInterceptor implements ClientHttpRequestInterceptor {

    private final long minSize;

    public GzipCompressInterceptor(long minSize) {
        this.minSize = minSize;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        if (body.length >= minSize) {
            byte[] compressed = compress(body);
            request.getHeaders().set(HttpHeaders.CONTENT_ENCODING, "gzip");
            request.getHeaders().setContentLength(compressed.length);
            return execution.execute(request, compressed);
        }
        return execution.execute(request, body);
    }

    private byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(16, data.length / 2));
        try (GZIPOutputStream gos = new GZIPOutputStream(bos)) {
            gos.write(data);
        }
        return bos.toByteArray();
    }

}
