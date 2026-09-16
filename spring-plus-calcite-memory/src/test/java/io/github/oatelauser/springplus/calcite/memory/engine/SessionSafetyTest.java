package io.github.oatelauser.springplus.calcite.memory.engine;

import io.github.oatelauser.springplus.calcite.memory.exception.ResourceLimitExceededException;
import io.github.oatelauser.springplus.calcite.memory.exception.SqlException;
import io.github.oatelauser.springplus.calcite.memory.result.QueryResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话级安全与资源限额集成测试：RESTRICTED 白名单、表数/行数/结果行数限额、默认会话向后兼容。
 */
class SessionSafetyTest {

    private final MemoryQueryEngine engine = MemoryQueryEngine.create();

    @AfterEach
    void tearDown() {
        engine.close();
    }

    private static List<Map<String, Object>> people() {
        return List.of(
            Map.of("id", 1, "name", "alice"),
            Map.of("id", 2, "name", "bob"),
            Map.of("id", 3, "name", "carol"));
    }

    @Test
    void restrictedRejectsInsert() {
        SessionConfig cfg = SessionConfig.builder().safetyMode(SqlSafetyMode.RESTRICTED).build();
        try (MemoryQuerySession session = engine.openSession(cfg)) {
            session.register("people", people());
            SqlException ex = assertThrows(SqlException.class,
                () -> session.query("INSERT INTO people (id, name) VALUES (4, 'dave')"));
            assertTrue(ex.getMessage().contains("RESTRICTED"), ex.getMessage());
        }
    }

    @Test
    void restrictedAllowsSelect() {
        SessionConfig cfg = SessionConfig.builder().safetyMode(SqlSafetyMode.RESTRICTED).build();
        try (MemoryQuerySession session = engine.openSession(cfg)) {
            session.register("people", people());
            QueryResult r = session.query("SELECT id, name FROM people WHERE id > 1 ORDER BY id");
            assertEquals(2, r.rowCount());
        }
    }

    @Test
    void trustedDoesNotPreValidateInsert() {
        // TRUSTED 模式不走白名单：INSERT 交由 Calcite 处理，最终以异常失败（非白名单消息）
        SessionConfig cfg = SessionConfig.builder().safetyMode(SqlSafetyMode.TRUSTED).build();
        try (MemoryQuerySession session = engine.openSession(cfg)) {
            session.register("people", people());
            assertThrows(Exception.class,
                () -> session.query("INSERT INTO people (id, name) VALUES (4, 'dave')"));
        }
    }

    @Test
    void maxTablesExceeded() {
        SessionConfig cfg = SessionConfig.builder().maxTables(1).build();
        try (MemoryQuerySession session = engine.openSession(cfg)) {
            session.register("t1", List.of(Map.of("id", 1)));
            assertThrows(ResourceLimitExceededException.class,
                () -> session.register("t2", List.of(Map.of("id", 2))));
        }
    }

    @Test
    void maxRowsPerTableExceeded() {
        SessionConfig cfg = SessionConfig.builder().maxRowsPerTable(2).build();
        try (MemoryQuerySession session = engine.openSession(cfg)) {
            assertThrows(ResourceLimitExceededException.class,
                () -> session.register("people", people()));
        }
    }

    @Test
    void maxResultRowsExceeded() {
        SessionConfig cfg = SessionConfig.builder().maxResultRows(1).build();
        try (MemoryQuerySession session = engine.openSession(cfg)) {
            session.register("people", people());
            assertThrows(ResourceLimitExceededException.class,
                () -> session.query("SELECT id FROM people"));
        }
    }

    @Test
    void defaultSessionStillWorks() {
        try (MemoryQuerySession session = engine.openSession()) {
            session.register("people", people());
            QueryResult r = session.query("SELECT id FROM people WHERE id >= 2 ORDER BY id");
            assertEquals(2, r.rowCount());
        }
    }
}
