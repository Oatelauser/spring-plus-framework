package io.github.oatelauser.springplus.boot.client.interceptor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Arrays;

/**
 * 可重复读取的 ClientHttpResponse 包装器（缓冲有上界）。
 * <p>
 * 构造时最多缓冲 {@code maxBufferSize} 字节供日志使用；
 * body 超过上限时只缓冲前缀，剩余部分以"前缀流 + 原始流"的方式
 * 拼接交付给调用方——<b>日志内存有界，下游拿到的 body 完整</b>。
 * </p>
 * <p>
 * 仅在 {@code LogLevel.BODY} 时使用。注意：body 超限被截断缓冲时，
 * {@link #getBody()} 只有第一次调用能拿到完整内容（尾流只能读一次）。
 * </p>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-19
 * @since 1.1
 */
@SuppressWarnings("NullableProblems")
public class BufferedClientHttpResponse implements ClientHttpResponse {

    private final ClientHttpResponse delegate;
    /**
     * 已缓冲的前缀字节（≤ maxBufferSize），供日志读取
     */
    private final byte[] bufferedPrefix;
    /**
     * body 超过缓冲上限时的剩余流（含构造时多读的 1 个判定字节）；
     * 为 null 表示 body 已完整缓冲。
     */
    private final InputStream tail;

    /**
     * @param delegate      原始响应
     * @param maxBufferSize 日志缓冲上限（字节），构造时即完成有界读取
     */
    public BufferedClientHttpResponse(ClientHttpResponse delegate, int maxBufferSize) throws IOException {
        this.delegate = delegate;
        int cap = Math.max(0, maxBufferSize);
        byte[] raw = readAtMost(delegate.getBody(), cap + 1);
        if (raw.length > cap) {
            this.bufferedPrefix = Arrays.copyOf(raw, cap);
            this.tail = new SequenceInputStream(
                    new ByteArrayInputStream(raw, cap, raw.length - cap),
                    delegate.getBody());
        } else {
            this.bufferedPrefix = raw;
            this.tail = null;
        }
    }

    @Override
    public HttpStatusCode getStatusCode() throws IOException {
        return delegate.getStatusCode();
    }

    @Override
    public String getStatusText() throws IOException {
        return delegate.getStatusText();
    }

    @Override
    public HttpHeaders getHeaders() {
        return delegate.getHeaders();
    }

    @Override
    public InputStream getBody() {
        if (this.tail == null) {
            return new ByteArrayInputStream(this.bufferedPrefix);
        }
        return new SequenceInputStream(
                new ByteArrayInputStream(this.bufferedPrefix), this.tail);
    }

    /**
     * 获取缓冲的 body 前缀字节（供日志使用，最多 maxBufferSize 字节）
     */
    public byte[] getBufferedBody() {
        return this.bufferedPrefix;
    }

    /**
     * body 是否因超过缓冲上限被截断
     */
    public boolean isTruncated() {
        return this.tail != null;
    }

    @Override
    public void close() {
        delegate.close();
    }

    /**
     * 从流中读取至多 n 字节；流提前结束则返回实际长度的副本
     */
    private static byte[] readAtMost(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int read = in.read(buf, off, n - off);
            if (read == -1) {
                return Arrays.copyOf(buf, off);
            }
            off += read;
        }
        return buf;
    }

}
