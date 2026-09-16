package io.github.oatelauser.springplus.calcite.memory.schema;

import io.github.oatelauser.springplus.calcite.memory.exception.SchemaException;
import io.github.oatelauser.springplus.calcite.memory.model.SqlType;
import org.apache.calcite.sql.type.SqlTypeName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SchemaMerger} 类型提升规则测试（对应文档 08 测试矩阵 3.4）。
 */
class SchemaMergerTest {

    @Test
    void mergeNullPassthrough() {
        assertEquals(SqlTypeName.INTEGER,
            SchemaMerger.merge(null, SqlType.of(SqlTypeName.INTEGER)).getTypeName());
        assertEquals(SqlTypeName.VARCHAR,
            SchemaMerger.merge(SqlType.of(SqlTypeName.VARCHAR), null).getTypeName());
    }

    @Test
    void mergeIntegerFamilyPromotesUp() {
        assertEquals(SqlTypeName.BIGINT,
            SchemaMerger.merge(SqlType.of(SqlTypeName.INTEGER), SqlType.of(SqlTypeName.BIGINT)).getTypeName());
        assertEquals(SqlTypeName.SMALLINT,
            SchemaMerger.merge(SqlType.of(SqlTypeName.TINYINT), SqlType.of(SqlTypeName.SMALLINT)).getTypeName());
        assertEquals(SqlTypeName.DECIMAL,
            SchemaMerger.merge(SqlType.of(SqlTypeName.INTEGER), SqlType.of(SqlTypeName.DECIMAL)).getTypeName());
    }

    @Test
    void mergeFloatFamilyPromotesToDouble() {
        assertEquals(SqlTypeName.DOUBLE,
            SchemaMerger.merge(SqlType.of(SqlTypeName.FLOAT), SqlType.of(SqlTypeName.DOUBLE)).getTypeName());
    }

    @Test
    void mergeSameTypeKeepsMaxPrecision() {
        SqlType r = SchemaMerger.merge(
            SqlType.varchar(10, true), SqlType.varchar(20, true));
        assertEquals(SqlTypeName.VARCHAR, r.getTypeName());
        assertEquals(20, r.getPrecision());
    }

    @Test
    void mergeNullableTakesOr() {
        SqlType r = SchemaMerger.merge(
            SqlType.of(SqlTypeName.INTEGER, false), SqlType.of(SqlTypeName.INTEGER, true));
        assertTrue(r.isNullable());
    }

    @Test
    void mergeCrossFamilyThrows() {
        assertThrows(SchemaException.class, () ->
            SchemaMerger.merge(SqlType.of(SqlTypeName.INTEGER), SqlType.of(SqlTypeName.VARCHAR)));
        assertThrows(SchemaException.class, () ->
            SchemaMerger.merge(SqlType.of(SqlTypeName.INTEGER), SqlType.of(SqlTypeName.DOUBLE)));
        assertThrows(SchemaException.class, () ->
            SchemaMerger.merge(SqlType.of(SqlTypeName.DATE), SqlType.of(SqlTypeName.TIMESTAMP)));
        assertThrows(SchemaException.class, () ->
            SchemaMerger.merge(SqlType.of(SqlTypeName.BOOLEAN), SqlType.of(SqlTypeName.INTEGER)));
    }
}
