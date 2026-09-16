package io.github.oatelauser.springplus.web.stream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 文件下载流式写入器：边生成边写出，大文件导出不撑内存。
 * <p>
 * 由 {@code StreamWriterFactory.download(response, filename)} 创建——协议头
 * （{@code Content-Disposition} / {@code Content-Type}）由工厂统一设置，本类只负责写数据。
 *
 * <h3>写入语义</h3>
 * <ol>
 *   <li>{@link #write(byte[])}：写入一段数据并 flush（客户端即刻开始接收）</li>
 *   <li>{@link #write(InputStream)}：8KB 缓冲循环拷贝直到 EOF——<b>不关闭入参流</b>，
 *       其生命周期归调用方（try-with-resources 由调用方负责）</li>
 *   <li>{@link #finish()} / {@link #close()}：flush 收尾，标记写入器完成</li>
 * </ol>
 * <p>
 * {@link #write(InputStream)} 的整段拷贝在<b>锁内</b>执行：流式下载语义要求单次拷贝
 * 期间独占输出流，防止与并发 {@code write(byte[])} 交错导致字节错位——这与
 * {@link SseStreamWriter} 的「锁外序列化」不同：拷贝没有可外提的序列化阶段，
 * 读源与写目标必须原子成对。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @GetMapping("/export")
 * public void export(HttpServletResponse response) throws IOException {
 *     try (FileDownloadWriter writer = streamWriterFactory.download(response, "订单导出.csv");
 *          InputStream in = Files.newInputStream(path)) {
 *         writer.write(in);
 *     }
 * }
 * }</pre>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-08-21
 * @since 1.0
 */
public class FileDownloadWriter extends AbstractStreamWriter {

    /** 拷贝缓冲区大小：8KB（一次网络发送的有效载荷量级）。 */
    private static final int BUFFER_SIZE = 8 * 1024;

    public FileDownloadWriter(OutputStream outputStream) {
        super(outputStream, null);
    }

    /**
     * 写入一段数据并 flush。
     *
     * @param data 数据段
     * @return this（链式）
     * @throws IOException IO异常
     */
    public FileDownloadWriter write(byte[] data) throws IOException {
        lock.lock();
        try {
            this.checkClosed();
            outputStream.write(data);
            outputStream.flush();
        } finally {
            lock.unlock();
        }
        return this;
    }

    /**
     * 循环拷贝输入流直到 EOF，每满一缓冲 flush 一次；不关闭入参流。
     *
     * @param in 数据源（生命周期归调用方）
     * @return this（链式）
     * @throws IOException IO异常
     */
    public FileDownloadWriter write(InputStream in) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        lock.lock();
        try {
            this.checkClosed();
            int read;
            while ((read = in.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
                outputStream.flush();
            }
        } finally {
            lock.unlock();
        }
        return this;
    }

    /**
     * 结束写入（flush 收尾并标记完成）：{@link #close()} 的语义化别名，推荐显式调用以示下载收尾。
     *
     * @throws IOException IO异常
     */
    public void finish() throws IOException {
        this.close();
    }

}
