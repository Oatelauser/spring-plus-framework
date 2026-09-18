package io.github.oatelauser.springplus.boot.client.interceptor;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-8 回归：BODY 日志缓冲封顶。
 * <ul>
 *   <li>超限时：日志只见前缀（有界内存），下游仍能读完整 body</li>
 *   <li>未超限时：body 可重复读（非流式路径上被多次读取）</li>
 * </ul>
 */
class BufferedClientHttpResponseTest {

    /**
     * 单一流实例的 delegate——与真实 HttpClient 响应语义一致（getBody 返回同一条流）
     */
    private static ClientHttpResponse delegateOf(byte[] body) {
        return new ClientHttpResponse() {
            private final ByteArrayInputStream stream = new ByteArrayInputStream(body);

            @Override
            public HttpStatusCode getStatusCode() {
                return HttpStatusCode.valueOf(200);
            }

            @Override
            public String getStatusText() {
                return "OK";
            }

            @Override
            public HttpHeaders getHeaders() {
                return HttpHeaders.EMPTY;
            }

            @Override
            public InputStream getBody() {
                return stream;
            }

            @Override
            public void close() {
            }
        };
    }

    @Test
    void truncatedBodyLogsPrefixButDeliversEverything() throws Exception {
        byte[] body = "0123456789ABCDEFGHIJ".getBytes(StandardCharsets.UTF_8); // 20 字节
        BufferedClientHttpResponse wrapped = new BufferedClientHttpResponse(delegateOf(body), 10);

        assertTrue(wrapped.isTruncated());
        assertEquals(10, wrapped.getBufferedBody().length);
        assertArrayEquals(body, wrapped.getBody().readAllBytes());
    }

    @Test
    void smallBodyIsReplayable() throws Exception {
        byte[] body = "short".getBytes(StandardCharsets.UTF_8);
        BufferedClientHttpResponse wrapped = new BufferedClientHttpResponse(delegateOf(body), 10);

        assertFalse(wrapped.isTruncated());
        assertArrayEquals(body, wrapped.getBufferedBody());
        assertArrayEquals(body, wrapped.getBody().readAllBytes());
        assertArrayEquals(body, wrapped.getBody().readAllBytes());
    }

}
