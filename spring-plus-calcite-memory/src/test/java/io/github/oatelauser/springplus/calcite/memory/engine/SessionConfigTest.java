package io.github.oatelauser.springplus.calcite.memory.engine;

import io.github.oatelauser.springplus.calcite.memory.exception.ResourceLimitExceededException;
import io.github.oatelauser.springplus.calcite.memory.safety.ResourceLimits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link SessionConfig} / {@link ResourceLimits} 配置模型与限制校验测试。
 */
class SessionConfigTest {

    @Test
    void defaults() {
        SessionConfig c = SessionConfig.defaults();
        assertEquals("UTC", c.timeZone());
        assertEquals(SqlSafetyMode.TRUSTED, c.safetyMode());
        assertEquals(100_000L, c.maxRowsPerTable());
        assertEquals(64, c.maxTables());
        assertEquals(100_000L, c.maxResultRows());
        assertEquals(5_000L, c.queryTimeoutMillis());
    }

    @Test
    void builderOverrides() {
        SessionConfig c = SessionConfig.builder()
            .timeZone("Asia/Shanghai")
            .maxRowsPerTable(50)
            .maxTables(3)
            .maxResultRows(200)
            .queryTimeoutMillis(1_000)
            .safetyMode(SqlSafetyMode.RESTRICTED)
            .build();
        assertEquals("Asia/Shanghai", c.timeZone());
        assertEquals(50L, c.maxRowsPerTable());
        assertEquals(3, c.maxTables());
        assertEquals(200L, c.maxResultRows());
        assertEquals(1_000L, c.queryTimeoutMillis());
        assertEquals(SqlSafetyMode.RESTRICTED, c.safetyMode());
    }

    @Test
    void blankTimeZoneRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            SessionConfig.builder().timeZone(" ").build());
    }

    @Test
    void checkRegisterTableEnforcesMaxTables() {
        ResourceLimits limits = ResourceLimits.builder().maxTables(2).maxRowsPerTable(1000).build();
        assertDoesNotThrow(() -> limits.checkRegisterTable(1, 1, 10, "t2"));
        assertThrows(ResourceLimitExceededException.class, () ->
            limits.checkRegisterTable(2, 1, 10, "t3"));
    }

    @Test
    void checkRegisterTableEnforcesRowsPerTable() {
        ResourceLimits limits = ResourceLimits.builder().maxTables(64).maxRowsPerTable(5).build();
        assertDoesNotThrow(() -> limits.checkRegisterTable(0, 1, 5, "t"));
        assertThrows(ResourceLimitExceededException.class, () ->
            limits.checkRegisterTable(0, 1, 6, "t"));
    }

    @Test
    void checkQueryResultEnforcesMaxResultRows() {
        ResourceLimits limits = ResourceLimits.builder().maxResultRows(3).build();
        assertDoesNotThrow(() -> limits.checkQueryResult(3));
        assertThrows(ResourceLimitExceededException.class, () ->
            limits.checkQueryResult(4));
    }
}
