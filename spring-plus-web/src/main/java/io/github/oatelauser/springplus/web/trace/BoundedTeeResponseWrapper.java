package io.github.oatelauser.springplus.web.trace;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 有界旁录响应包装器（V16 / CWE-400）：响应体<b>流式直写客户端</b>，旁路缓存仅记录前
 * {@code maxBytes} 字节用于请求追踪——超限即停止旁录（记 {@code [payload too large]}），
 * 响应本身不受影响。
 * <p>
 * 与 Spring 的 {@code ContentCachingResponseWrapper}（全量缓冲后回写）相比：
 * <ul>
 *   <li>大响应（文件下载/导出）误标 {@code @RecordHttp} 不再撑爆堆内存</li>
 *   <li>响应天然流式透传（无整体缓冲，对大文件更友好）</li>
 * </ul>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.1.0
 */
final class BoundedTeeResponseWrapper extends HttpServletResponseWrapper {

    private final int maxBytes;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private long written;
    private boolean overflow;
    private boolean binary;

    BoundedTeeResponseWrapper(HttpServletResponse response, int maxBytes) {
        super(response);
        this.maxBytes = maxBytes;
    }

    /** 是否为二进制/文件类响应（按 Content-Type 判定，文本之外不旁录） */
    private boolean isBinaryContentType() {
        String contentType = getContentType();
        if (contentType == null) {
            return false;
        }
        String normalized = contentType.toLowerCase();
        return !normalized.startsWith("text/")
                && !normalized.contains("json")
                && !normalized.contains("xml")
                && !normalized.contains("x-www-form-urlencoded");
    }

    private void record(byte[] b, int off, int len) {
        written += len;
        if (overflow || binary || isBinaryContentType()) {
            return;
        }
        if (written > maxBytes) {
            overflow = true;
            buffer.reset();
            return;
        }
        buffer.write(b, off, len);
    }

    /** 旁录的响应体片段；超限或二进制时返回占位说明 */
    String payload() {
        if (binary || isBinaryContentType()) {
            return "[binary response skipped]";
        }
        if (overflow || written > maxBytes) {
            return "[payload too large]";
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        ServletOutputStream delegate = super.getOutputStream();
        return new ServletOutputStream() {
            @Override
            public void write(int b) throws IOException {
                delegate.write(b);
                record(new byte[] { (byte) b }, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                delegate.write(b, off, len);
                record(b, off, len);
            }

            @Override
            public boolean isReady() {
                return delegate.isReady();
            }

            @Override
            public void setWriteListener(WriteListener listener) {
                delegate.setWriteListener(listener);
            }
        };
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        PrintWriter delegate = super.getWriter();
        return new PrintWriter(delegate) {
            @Override
            public void write(char[] buf, int off, int len) {
                super.write(buf, off, len);
                byte[] bytes = new String(buf, off, len).getBytes(StandardCharsets.UTF_8);
                record(bytes, 0, bytes.length);
            }

            @Override
            public void write(String s, int off, int len) {
                super.write(s, off, len);
                byte[] bytes = s.substring(off, off + len).getBytes(StandardCharsets.UTF_8);
                record(bytes, 0, bytes.length);
            }
        };
    }

}
