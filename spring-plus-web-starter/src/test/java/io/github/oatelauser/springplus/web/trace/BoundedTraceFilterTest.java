package io.github.oatelauser.springplus.web.trace;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V16 / CWE-400：追踪过滤器响应旁录防护——大响应不缓冲（直写透传 + 超限占位）、
 * multipart 请求不旁录、二进制响应跳过。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.1.0
 */
class BoundedTraceFilterTest {

    /** 测试用过滤器：暴露 recordBody 旁录结果 */
    static final class CapturingFilter extends AbstractHttpTraceFilter {

        volatile String lastResponsePayload = "unset";

        @Override
        protected boolean isCandidateStatus(int status) {
            return true;
        }

        @Override
        protected void recordBody(HttpServletRequest request, HttpServletResponse response,
                String requestPayload, String responsePayload) {
            this.lastResponsePayload = responsePayload;
        }

    }

    private static MockHttpServletRequest post(String contentType) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/x");
        if (contentType != null) {
            request.setContentType(contentType);
        }
        return request;
    }

    @Test
    void oversizedResponseNotBufferedAndReportedAsPlaceholder() throws ServletException, IOException {
        CapturingFilter filter = new CapturingFilter();
        filter.setMaxPayloadSize(64);
        // 限 64 字节，写 1MB 响应：客户端必须完整收到（直写透传），旁录只留占位
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 512 * 1024; i++) {
            big.append("ab");
        }
        String body = big.toString();

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(post("application/json"), response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                try {
                    res.getWriter().write(body);
                } catch (IOException ignored) {
                }
            }
        });

        assertEquals(body.length(), response.getContentAsString().length(), "客户端必须收到完整响应（直写透传）");
        assertEquals("[payload too large]", filter.lastResponsePayload);
    }

    @Test
    void smallTextResponseRecordedVerbatim() throws ServletException, IOException {
        CapturingFilter filter = new CapturingFilter();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(post("application/json"), response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                try {
                    res.setContentType("application/json");
                    res.getWriter().write("{\"ok\":true}");
                } catch (IOException ignored) {
                }
            }
        });
        assertEquals("{\"ok\":true}", filter.lastResponsePayload);
        assertEquals("{\"ok\":true}", response.getContentAsString());
    }

    @Test
    void multipartRequestSkipsBodyRecording() throws ServletException, IOException {
        CapturingFilter filter = new CapturingFilter();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(post("multipart/form-data; boundary=x"), response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                // 若被包装为 ContentCachingRequestWrapper，multipart 解析将失效；此处验证链路直通
                assertTrue(req instanceof MockHttpServletRequest);
            }
        });
        assertEquals("unset", filter.lastResponsePayload, "multipart 请求不应触发 body 记录");
    }

    @Test
    void binaryResponseSkipped() throws ServletException, IOException {
        CapturingFilter filter = new CapturingFilter();
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] bytes = new byte[1024];
        filter.doFilter(post("application/json"), response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                try {
                    res.setContentType("application/octet-stream");
                    res.getOutputStream().write(bytes);
                } catch (IOException ignored) {
                }
            }
        });
        assertEquals("[binary response skipped]", filter.lastResponsePayload);
    }

    @Test
    void utf8WriterPayloadRecordedInUtf8() throws ServletException, IOException {
        CapturingFilter filter = new CapturingFilter();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(post("application/json"), response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                try {
                    res.setContentType("application/json");
                    res.getWriter().write("中文响应体");
                } catch (IOException ignored) {
                }
            }
        });
        assertEquals("中文响应体", filter.lastResponsePayload);
        assertEquals("中文响应体",
                response.getContentAsString(StandardCharsets.UTF_8));
    }

}
