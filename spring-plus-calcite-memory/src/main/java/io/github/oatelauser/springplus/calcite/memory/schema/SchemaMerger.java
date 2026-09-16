package io.github.oatelauser.springplus.calcite.memory.schema;

import io.github.oatelauser.springplus.calcite.memory.exception.SchemaException;
import io.github.oatelauser.springplus.calcite.memory.model.SqlType;
import org.apache.calcite.sql.type.SqlTypeName;

/**
 * Schema 合并器：将多行（尤其 Map 行）观测到的同列类型按安全提升规则统一为单一类型。
 *
 * <p>提升规则：
 * <ul>
 *   <li>整数族：TINYINT -&gt; SMALLINT -&gt; INTEGER -&gt; BIGINT -&gt; DECIMAL</li>
 *   <li>浮点族：FLOAT -&gt; DOUBLE（近似数，不与整数族/DECIMAL 静默混用）</li>
 *   <li>字符族：CHAR + VARCHAR -&gt; VARCHAR，精度取较大值</li>
 *   <li>DATE / TIME / TIMESTAMP 互异视为不兼容</li>
 *   <li>跨族（整数与浮点、数值与字符、日期互异等）直接抛错，<strong>绝不</strong>静默退化为 VARCHAR</li>
 * </ul>
 * 可空性取或：任一观测为可空则结果可空。
 */
public final class SchemaMerger {

    private SchemaMerger() {
    }

    /**
     * 合并两个同列的 SQL 类型。
     *
     * @param a 已观测类型，null 表示尚未观测
     * @param b 新观测类型，null 表示尚未观测
     * @return 合并后的类型；两参皆 null 返回 null
     */
    public static SqlType merge(SqlType a, SqlType b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        boolean nullable = a.isNullable() || b.isNullable();
        SqlTypeName ta = a.getTypeName();
        SqlTypeName tb = b.getTypeName();
        if (ta == tb) {
            return SqlType.builder()
                    .typeName(ta)
                    .precision(maxOrNull(a.getPrecision(), b.getPrecision()))
                    .scale(maxOrNull(a.getScale(), b.getScale()))
                    .nullable(nullable)
                    .build();
        }
        Family fa = family(ta);
        Family fb = family(tb);
        if (fa != fb) {
            throw new SchemaException("类型不兼容，无法合并: " + ta + " 与 " + tb
                    + "（整数/浮点/字符/日期不可静默混用）");
        }
        SqlTypeName promoted = switch (fa) {
            case INT -> intRank(ta) >= intRank(tb) ? ta : tb;
            case FLOAT -> floatRank(ta) >= floatRank(tb) ? ta : tb;
            case CHAR -> (ta == SqlTypeName.VARCHAR || tb == SqlTypeName.VARCHAR)
                    ? SqlTypeName.VARCHAR : ta;
            default -> throw new SchemaException("类型不兼容，无法合并: " + ta + " 与 " + tb);
        };
        return SqlType.builder()
                .typeName(promoted)
                .precision(maxOrNull(a.getPrecision(), b.getPrecision()))
                .scale(maxOrNull(a.getScale(), b.getScale()))
                .nullable(nullable)
                .build();
    }

    private enum Family {INT, FLOAT, CHAR, DATE, TIME, TIMESTAMP, BOOLEAN, BINARY, OTHER}

    private static Family family(SqlTypeName t) {
        return switch (t) {
            case TINYINT, SMALLINT, INTEGER, BIGINT, DECIMAL -> Family.INT;
            case FLOAT, REAL, DOUBLE -> Family.FLOAT;
            case CHAR, VARCHAR -> Family.CHAR;
            case DATE -> Family.DATE;
            case TIME, TIME_WITH_LOCAL_TIME_ZONE -> Family.TIME;
            case TIMESTAMP, TIMESTAMP_WITH_LOCAL_TIME_ZONE -> Family.TIMESTAMP;
            case BOOLEAN -> Family.BOOLEAN;
            case BINARY, VARBINARY -> Family.BINARY;
            default -> Family.OTHER;
        };
    }

    private static int intRank(SqlTypeName t) {
        return switch (t) {
            case TINYINT -> 1;
            case SMALLINT -> 2;
            case INTEGER -> 3;
            case BIGINT -> 4;
            case DECIMAL -> 5;
            default -> 0;
        };
    }

    private static int floatRank(SqlTypeName t) {
        return switch (t) {
            case FLOAT -> 1;
            case REAL -> 2;
            case DOUBLE -> 3;
            default -> 0;
        };
    }

    private static Integer maxOrNull(Integer a, Integer b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return Math.max(a, b);
    }
}
