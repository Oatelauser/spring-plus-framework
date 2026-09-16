package io.github.oatelauser.springplus.calcite.memory.engine;

import org.apache.calcite.avatica.util.Casing;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.SqlDynamicParam;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.util.SqlShuttle;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SQL 参数化器：将带字面量的 SQL 归并为 {@code ?} 模板 + 按序提取的参数值，
 * 供 {@link StatementCache} 以模板为 key 复用 PreparedStatement（Druid
 * {@code ParameterizedOutputVisitorUtils#parameterize} 的 Calcite 等价实现）。
 *
 * <p>与引入外部解析器的方案不同，模板的生产与消费使用同一套 Calcite 语法
 * （本类 unparse 产出 -> CalciteConnection 重新 parse），roundtrip 闭环保真；
 * 失败时回退原始 SQL，查询行为与未参数化完全一致（最坏情况仅失去缓存复用）。</p>
 *
 * <p>保守参数化范围：仅替换 WHERE / HAVING 子树内的数值、字符串、布尔字面量。
 * 以下位置一律保留原样（Calcite 校验器拒绝动态参数，或值无法无损还原）：
 * <ul>
 *   <li>SELECT 投影中的裸字面量（如 {@code SELECT status, 'X' AS flag}）</li>
 *   <li>ORDER BY / GROUP BY 序数（如 {@code ORDER BY 1}，序数语义非值语义）</li>
 *   <li>LIMIT / OFFSET / FETCH 行数</li>
 *   <li>FROM 子树（JOIN ON 条件、{@code VALUES} 行、表函数参数）</li>
 *   <li>时间戳/日期/区间字面量、NULL、符号量（时区语义与类型还原风险）</li>
 * </ul></p>
 */
public final class SqlParameterizer {

    /** 解析配置：标识符大小写保持原样，与连接的 unquotedCasing=UNCHANGED 对齐。 */
    private static final SqlParser.Config PARSER_CONFIG = SqlParser.config()
            .withUnquotedCasing(Casing.UNCHANGED)
            .withQuotedCasing(Casing.UNCHANGED);

    /** 回写方言：标识符大小写保持原样，否则 orders 会被折成 ORDERS 与表名不匹配。 */
    private static final SqlDialect DIALECT = new SqlDialect(
            SqlDialect.EMPTY_CONTEXT
                    .withUnquotedCasing(Casing.UNCHANGED)
                    .withQuotedCasing(Casing.UNCHANGED)) {
    };

    private SqlParameterizer() {
    }

    /**
     * 参数化 SQL：结构相同、字面量不同的 SQL 归并到同一模板。
     *
     * @param sql 原始 SQL（含具体字面量）
     * @return 模板 SQL（含 {@code ?} 占位符）与按占位符出现顺序对应的参数值；
     *         无可参数化字面量或解析失败时返回原始 SQL + 空参数
     */
    public static ParameterizedSql parameterize(String sql) {
        try {
            SqlNode node = SqlParser.create(sql, PARSER_CONFIG).parseStmt();
            Set<SqlParserPos> keepPositions = new HashSet<>();
            collectKeepPositions(node, false, keepPositions);
            ParameterizingShuttle shuttle = new ParameterizingShuttle(keepPositions);
            SqlNode template = node.accept(shuttle);
            if (shuttle.values.isEmpty()) {
                return new ParameterizedSql(sql, List.of());
            }
            return new ParameterizedSql(template.toSqlString(DIALECT).getSql(), List.copyOf(shuttle.values));
        } catch (SqlParseException | RuntimeException e) {
            return new ParameterizedSql(sql, List.of());
        }
    }

    /** 参数化结果：模板 SQL 与对应参数值。 */
    public record ParameterizedSql(String templateSql, List<Object> parameters) {
    }

    /**
     * 第一趟：收集禁止参数化的字面量位置（非 WHERE/HAVING 子树内的全部字面量）。
     * 位置对象与后续 shuttle 遍历的是同一批 AST 实例，集合命中即跳过。
     */
    private static void collectKeepPositions(SqlNode node, boolean inFilter, Set<SqlParserPos> keep) {
        if (node == null) {
            return;
        }
        if (node instanceof SqlSelect select) {
            collectKeepPositions(select.getSelectList(), false, keep);
            collectKeepPositions(select.getFrom(), false, keep);
            collectKeepPositions(select.getWhere(), true, keep);
            collectKeepPositions(select.getGroup(), false, keep);
            collectKeepPositions(select.getHaving(), true, keep);
            collectKeepPositions(select.getOrderList(), false, keep);
            collectKeepPositions(select.getWindowList(), false, keep);
            collectKeepPositions(select.getOffset(), false, keep);
            collectKeepPositions(select.getFetch(), false, keep);
            collectKeepPositions(select.getHints(), false, keep);
            return;
        }
        if (node instanceof SqlCall call) {
            for (SqlNode operand : call.getOperandList()) {
                collectKeepPositions(operand, inFilter, keep);
            }
            return;
        }
        if (node instanceof SqlNodeList list) {
            for (SqlNode item : list) {
                collectKeepPositions(item, inFilter, keep);
            }
            return;
        }
        if (node instanceof SqlLiteral literal && !inFilter) {
            keep.add(literal.getParserPosition());
        }
    }

    /**
     * 提取可绑定值：数值（Integer/Long/BigDecimal/Double）、字符串、布尔。
     * 其余类型返回 null，保持字面量原样。
     */
    private static Object extractValue(SqlLiteral literal) {
        if (literal.getTypeName() == SqlTypeName.CHAR) {
            return literal.getValueAs(String.class);
        }
        Object value = literal.getValue();
        if (value instanceof Integer || value instanceof Long || value instanceof BigDecimal) {
            return value;
        }
        if (literal.getTypeName() == SqlTypeName.DOUBLE && value instanceof Double d) {
            return d;
        }
        if (literal.getTypeName() == SqlTypeName.BOOLEAN && value instanceof Boolean b) {
            return b;
        }
        return null;
    }

    /** 第二趟：将允许位置的字面量替换为动态参数并按序收集值，返回改写后的 AST。 */
    private static final class ParameterizingShuttle extends SqlShuttle {

        private final Set<SqlParserPos> keepPositions;
        private final List<Object> values = new ArrayList<>();

        ParameterizingShuttle(Set<SqlParserPos> keepPositions) {
            this.keepPositions = keepPositions;
        }

        @Override
        public SqlNode visit(SqlLiteral literal) {
            if (keepPositions.contains(literal.getParserPosition())) {
                return literal;
            }
            Object value = extractValue(literal);
            if (value == null) {
                return literal;
            }
            values.add(value);
            return new SqlDynamicParam(values.size() - 1, literal.getParserPosition());
        }
    }
}
