package io.github.oatelauser.springplus.calcite.memory.registry;

import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQueryEngine;
import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQuerySession;
import io.github.oatelauser.springplus.calcite.memory.result.QueryResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TableRegistry} 测试：应用级版本替换、会话可见性、drop、会话级隔离。
 */
class TableRegistryTest {

    private final MemoryQueryEngine engine = MemoryQueryEngine.create();

    @AfterEach
    void tearDown() {
        engine.close();
    }

    private static List<Map<String, Object>> products(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
            .mapToObj(i -> Map.<String, Object>of("id", i, "name", "p" + i))
            .toList();
    }

    @Test
    void appPublishVisibleToNewSession() {
        engine.applicationTableRegistry().publishSnapshot("products", products(2));

        try (MemoryQuerySession session = engine.openSession()) {
            QueryResult r = session.query("SELECT id FROM products ORDER BY id");
            assertEquals(2, r.rowCount());
        }
    }

    @Test
    void versionReplaceNewSessionSeesLatestOldSessionUnaffected() {
        TableRegistry app = engine.applicationTableRegistry();
        app.publishSnapshot("products", products(2));

        try (MemoryQuerySession session1 = engine.openSession()) {
            // session1 在打开时快照了 v1（2 行）
            assertEquals(2, session1.query("SELECT id FROM products").rowCount());

            // 应用级版本替换为 v2（3 行）
            app.publishSnapshot("products", products(3));

            // 新会话看到最新版本
            try (MemoryQuerySession session2 = engine.openSession()) {
                assertEquals(3, session2.query("SELECT id FROM products").rowCount());
            }
            // 已打开的 session1 仍持旧快照（不可变引用）
            assertEquals(2, session1.query("SELECT id FROM products").rowCount());
        }
    }

    @Test
    void dropAtAppMakesTableInvisibleToNewSession() {
        TableRegistry app = engine.applicationTableRegistry();
        app.publishSnapshot("products", products(2));
        assertTrue(app.drop("products"));
        assertFalse(app.drop("products"));

        try (MemoryQuerySession session = engine.openSession()) {
            assertThrows(Exception.class, () -> session.query("SELECT id FROM products"));
        }
    }

    @Test
    void sessionRegistryIsolatedPerSession() {
        try (MemoryQuerySession session1 = engine.openSession();
             MemoryQuerySession session2 = engine.openSession()) {
            session1.sessionTableRegistry().publishSnapshot("tmp", List.of(Map.of("id", 1)));

            // 发布会话自身可见
            assertEquals(1, session1.query("SELECT id FROM tmp").rowCount());
            // 其他会话不可见（会话级隔离）
            assertThrows(Exception.class, () -> session2.query("SELECT id FROM tmp"));
        }
    }
}
