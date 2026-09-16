package io.github.oatelauser.springplus.calcite.memory.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SqlParameterizer} 单元测试：字面量提取、保守范围（结构位置保留）、
 * 同型 SQL 模板归并与解析失败回退。
 */
class SqlParameterizerTest {

    /** 数值统一按 long 规范化后比较（Calcite 字面量值类型随精度可为 Integer/Long/BigDecimal）。 */
    private static List<Object> canonical(List<Object> params) {
        return params.stream()
                .map(v -> v instanceof Number n ? (Object) Long.valueOf(n.longValue()) : v)
                .toList();
    }

    @Test
    void stringLiteralExtracted() {
        SqlParameterizer.ParameterizedSql p = SqlParameterizer.parameterize(
                "SELECT * FROM orders WHERE status = 'PAID'");
        assertEquals(List.of("PAID"), p.parameters());
        assertTrue(p.templateSql().contains("?"), "模板应含占位符");
        assertFalse(p.templateSql().contains("PAID"), "字面量值不应残留在模板中");
    }

    @Test
    void numericLiteralExtracted() {
        SqlParameterizer.ParameterizedSql p = SqlParameterizer.parameterize(
                "SELECT id FROM orders WHERE amount > 100");
        assertEquals(List.of(100L), canonical(p.parameters()));
    }

    @Test
    void inListLiteralsExtractedInOrder() {
        SqlParameterizer.ParameterizedSql p = SqlParameterizer.parameterize(
                "SELECT id FROM orders WHERE id IN (1, 2, 3)");
        assertEquals(List.of(1L, 2L, 3L), canonical(p.parameters()));
    }

    @Test
    void booleanLiteralExtracted() {
        SqlParameterizer.ParameterizedSql p = SqlParameterizer.parameterize(
                "SELECT id FROM orders WHERE flag = true");
        assertEquals(List.of(Boolean.TRUE), p.parameters());
    }

    @Test
    void negativeNumberFoldedIntoLiteralValue() {
        SqlParameterizer.ParameterizedSql p = SqlParameterizer.parameterize(
                "SELECT id FROM orders WHERE amount > -5");
        assertEquals(List.of(-5L), canonical(p.parameters()), "负数整体作为字面量值提取");
        assertTrue(p.templateSql().contains("?"));
    }

    @Test
    void sameShapeDifferentValuesMergedToSameTemplate() {
        SqlParameterizer.ParameterizedSql a = SqlParameterizer.parameterize(
                "SELECT * FROM orders WHERE status = 'PAID'");
        SqlParameterizer.ParameterizedSql b = SqlParameterizer.parameterize(
                "SELECT * FROM orders WHERE status = 'PENDING'");
        assertEquals(a.templateSql(), b.templateSql(), "结构相同、值不同应归并同一模板");
    }

    @Test
    void orderByOrdinalAndLimitPreserved() {
        String sql = "SELECT id FROM orders ORDER BY 1 LIMIT 5";
        SqlParameterizer.ParameterizedSql p = SqlParameterizer.parameterize(sql);
        assertTrue(p.parameters().isEmpty(), "ORDER BY 序数与 LIMIT 行数不参数化");
        assertEquals(sql, p.templateSql(), "无参数化时保持原始 SQL");
    }

    @Test
    void selectBareLiteralAndGroupByOrdinalPreserved() {
        SqlParameterizer.ParameterizedSql p = SqlParameterizer.parameterize(
                "SELECT status, 'X' AS flag FROM orders WHERE a = 1 GROUP BY status, 1");
        assertEquals(List.of(1L), canonical(p.parameters()), "仅 WHERE 内字面量参数化");
        assertTrue(p.templateSql().contains("'X'"), "SELECT 裸字面量保留");
    }

    @Test
    void timestampLiteralPreserved() {
        SqlParameterizer.ParameterizedSql p = SqlParameterizer.parameterize(
                "SELECT id FROM orders WHERE created_at > TIMESTAMP '2026-01-01 00:00:00' AND status = 'PAID'");
        assertEquals(List.of("PAID"), p.parameters(), "时间戳字面量保留，字符串字面量提取");
    }

    @Test
    void subqueryInWhereParameterized() {
        SqlParameterizer.ParameterizedSql p = SqlParameterizer.parameterize(
                "SELECT id FROM orders WHERE id IN (SELECT id FROM items WHERE y = 2)");
        assertEquals(List.of(2L), canonical(p.parameters()), "子查询 WHERE 内字面量参数化，投影列保持");
    }

    @Test
    void unparsableSqlFallsBackToIdentity() {
        String sql = "SELEC bogus FROM";
        SqlParameterizer.ParameterizedSql p = SqlParameterizer.parameterize(sql);
        assertEquals(sql, p.templateSql());
        assertTrue(p.parameters().isEmpty());
    }
}
