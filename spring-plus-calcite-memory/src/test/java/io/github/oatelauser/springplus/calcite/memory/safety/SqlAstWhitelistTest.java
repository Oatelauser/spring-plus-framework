package io.github.oatelauser.springplus.calcite.memory.safety;

import io.github.oatelauser.springplus.calcite.memory.exception.SqlException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link SqlAstWhitelist} RESTRICTED 模式白名单校验测试。
 */
class SqlAstWhitelistTest {

    @Test
    void allowReadOnlySelects() {
        assertDoesNotThrow(() -> SqlAstWhitelist.validate("SELECT * FROM t"));
        assertDoesNotThrow(() -> SqlAstWhitelist.validate(
            "SELECT id, name FROM t WHERE id = 1 ORDER BY name"));
        assertDoesNotThrow(() -> SqlAstWhitelist.validate(
            "SELECT a, count(*) AS c FROM t GROUP BY a HAVING count(*) > 1"));
        assertDoesNotThrow(() -> SqlAstWhitelist.validate(
            "SELECT t1.id, t2.name FROM t1 JOIN t2 ON t1.id = t2.t1_id"));
        assertDoesNotThrow(() -> SqlAstWhitelist.validate(
            "SELECT * FROM t1 UNION SELECT * FROM t2"));
        assertDoesNotThrow(() -> SqlAstWhitelist.validate(
            "SELECT * FROM (SELECT id FROM t) x WHERE x.id = 1"));
        assertDoesNotThrow(() -> SqlAstWhitelist.validate("VALUES (1, 2, 3)"));
        assertDoesNotThrow(() -> SqlAstWhitelist.validate(
            "WITH cte AS (SELECT id FROM t) SELECT * FROM cte"));
        assertDoesNotThrow(() -> SqlAstWhitelist.validate(
            "SELECT id FROM t LIMIT 10 OFFSET 5"));
    }

    @Test
    void rejectDml() {
        assertThrows(SqlException.class, () -> SqlAstWhitelist.validate("INSERT INTO t VALUES (1)"));
        assertThrows(SqlException.class, () -> SqlAstWhitelist.validate("UPDATE t SET x = 1 WHERE id = 2"));
        assertThrows(SqlException.class, () -> SqlAstWhitelist.validate("DELETE FROM t WHERE id = 2"));
        assertThrows(SqlException.class, () -> SqlAstWhitelist.validate("MERGE INTO t USING s ON t.id=s.id WHEN MATCHED THEN UPDATE SET x=1"));
    }

    @Test
    void rejectDdl() {
        assertThrows(SqlException.class, () -> SqlAstWhitelist.validate("CREATE TABLE x (id INT)"));
        assertThrows(SqlException.class, () -> SqlAstWhitelist.validate("DROP TABLE t"));
        assertThrows(SqlException.class, () -> SqlAstWhitelist.validate("ALTER TABLE t ADD COLUMN x INT"));
        assertThrows(SqlException.class, () -> SqlAstWhitelist.validate("CREATE VIEW v AS SELECT * FROM t"));
        assertThrows(SqlException.class, () -> SqlAstWhitelist.validate("CREATE INDEX i ON t(id)"));
    }

    @Test
    void rejectManagementAndIntrospection() {
        assertThrows(SqlException.class, () -> SqlAstWhitelist.validate("EXPLAIN SELECT * FROM t"));
        assertThrows(SqlException.class, () -> SqlAstWhitelist.validate("COMMIT"));
        assertThrows(SqlException.class, () -> SqlAstWhitelist.validate("ROLLBACK"));
        assertThrows(SqlException.class, () -> SqlAstWhitelist.validate("CALL my_proc()"));
    }

    @Test
    void rejectCteWithInsert() {
        assertThrows(SqlException.class, () -> SqlAstWhitelist.validate(
            "WITH cte AS (SELECT id FROM t) INSERT INTO t2 SELECT * FROM cte"));
    }

    @Test
    void emptySqlRejected() {
        assertThrows(SqlException.class, () -> SqlAstWhitelist.validate("   "));
    }
}
