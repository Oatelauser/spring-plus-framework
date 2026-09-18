package io.github.oatelauser.springplus.boot.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * FileResources 路径穿越防护回归（V06，随 utils 归位自 web 模块迁入）。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-18
 * @since 1.1
 */
class FileResourcesTest {

    @Test
    void traversalRejected() {
        assertThrows(IllegalArgumentException.class, () -> FileResources.getResource("../secret.txt"));
        assertThrows(IllegalArgumentException.class, () -> FileResources.getResource("a/../../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> FileResources.getResource("/etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> FileResources.getResource("C:/windows/win.ini"));
        assertThrows(IllegalArgumentException.class, () -> FileResources.getResource("D:\\secret"));
        assertThrows(IllegalArgumentException.class, () -> FileResources.getResource("  "));
    }

    @Test
    void legitimateRelativePathsStillPass() {
        assertDoesNotThrow(() -> FileResources.getResource("config/app.yml"));
        assertDoesNotThrow(() -> FileResources.getResource("lua\\bget.lua"));
    }

}
