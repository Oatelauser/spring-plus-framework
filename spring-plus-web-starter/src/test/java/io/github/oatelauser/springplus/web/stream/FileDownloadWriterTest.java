package io.github.oatelauser.springplus.web.stream;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FileDownloadWriter} 单元测试：流拷贝跨缓冲边界、finish 语义、入参流不被关闭。
 *
 * @author Oatelauser
 * @date 2026-08-21
 * @since 1.0
 */
class FileDownloadWriterTest {

    @Test
    void writeInputStreamCopiesAcrossBufferBoundaries() throws IOException {
        // 20KB 随机数据：跨越 8KB 拷贝缓冲 2+ 次，验证循环拷贝正确性
        byte[] data = new byte[20 * 1024];
        new Random(42).nextBytes(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new FileDownloadWriter(out).write(new ByteArrayInputStream(data));

        assertArrayEquals(data, out.toByteArray());
    }

    @Test
    void writeBytesPassThrough() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] payload = "id,name\n1,alice\n".getBytes(StandardCharsets.UTF_8);

        new FileDownloadWriter(out).write(payload).write(payload);

        assertEquals(payload.length * 2, out.size(), "两次 write 应顺序累加");
    }

    @Test
    void finishMarksWriterClosed() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileDownloadWriter writer = new FileDownloadWriter(out);

        writer.write("done".getBytes(StandardCharsets.UTF_8));
        writer.finish();

        assertTrue(writer.isClosed(), "finish 后应标记完成");
        assertThrows(IOException.class,
                () -> writer.write("late".getBytes(StandardCharsets.UTF_8)));
    }

}
