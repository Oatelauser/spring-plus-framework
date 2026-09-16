package io.github.oatelauser.springplus.calcite.memory.safety;

import io.github.oatelauser.springplus.calcite.memory.exception.SqlException;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlDataTypeSpec;
import org.apache.calcite.sql.SqlDynamicParam;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlIntervalQualifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;

import java.util.EnumSet;
import java.util.Set;

/**
 * SQL AST 白名单校验器：用于 {@link io.github.oatelauser.springplus.calcite.memory.engine.SqlSafetyMode#RESTRICTED} 模式。
 *
 * <p>用 Calcite {@link SqlParser#parseStmt()} 解析为 AST，递归遍历校验：
 * <ul>
 *   <li>顶层必须是只读查询：SELECT / ORDER_BY / UNION / INTERSECT / EXCEPT / WITH / VALUES。</li>
 *   <li>整树禁止出现 DML（INSERT/UPDATE/DELETE/MERGE/TRUNCATE）、DDL（CREATE/DROP/ALTER 系列）、
 *       会话管理（SET_OPTION/COMMIT/ROLLBACK/GRANT/REVOKE）、过程调用（PROCEDURE_CALL）、
 *       EXPLAIN/DESCRIBE 等。</li>
 *   <li>未识别的节点类型默认拒绝（白名单语义，宁可误拒不可放行）。</li>
 * </ul>
 * SQL 语法本身不允许在 SELECT 表达式内嵌套 DML/DDL，故顶层拦截即可阻断写操作；
 * 全树遍历提供纵深防御并覆盖 {@code WITH ... INSERT} 这类 CTE 改写语句。
 */
public final class SqlAstWhitelist {

    /** 允许的顶层语句类型。 */
    private static final Set<SqlKind> ALLOWED_TOP = EnumSet.of(
        SqlKind.SELECT, SqlKind.ORDER_BY, SqlKind.UNION, SqlKind.INTERSECT, SqlKind.EXCEPT,
        SqlKind.WITH, SqlKind.VALUES);

    /** 全树禁止的语句/操作类型。 */
    private static final Set<SqlKind> BLOCKED = EnumSet.of(
        SqlKind.INSERT, SqlKind.UPDATE, SqlKind.DELETE, SqlKind.MERGE, SqlKind.TRUNCATE_TABLE,
        SqlKind.CREATE_SCHEMA, SqlKind.CREATE_FOREIGN_SCHEMA, SqlKind.DROP_SCHEMA,
        SqlKind.CREATE_TABLE, SqlKind.CREATE_TABLE_LIKE, SqlKind.ALTER_TABLE, SqlKind.DROP_TABLE,
        SqlKind.CREATE_VIEW, SqlKind.ALTER_VIEW, SqlKind.DROP_VIEW,
        SqlKind.CREATE_MATERIALIZED_VIEW, SqlKind.ALTER_MATERIALIZED_VIEW, SqlKind.DROP_MATERIALIZED_VIEW,
        SqlKind.CREATE_SEQUENCE, SqlKind.ALTER_SEQUENCE, SqlKind.DROP_SEQUENCE,
        SqlKind.CREATE_INDEX, SqlKind.ALTER_INDEX, SqlKind.DROP_INDEX,
        SqlKind.CREATE_TYPE, SqlKind.DROP_TYPE,
        SqlKind.SET_OPTION, SqlKind.ALTER_SESSION,
        SqlKind.COMMIT, SqlKind.ROLLBACK,
        SqlKind.EXPLAIN, SqlKind.DESCRIBE_SCHEMA, SqlKind.DESCRIBE_TABLE,
        SqlKind.PROCEDURE_CALL);

    private SqlAstWhitelist() {
    }

    /**
     * 校验 SQL 是否符合只读白名单；不符合抛 {@link SqlException}。
     */
    public static void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new SqlException("RESTRICTED 模式 SQL 不能为空");
        }
        SqlNode node = parse(sql);
        SqlKind top = node.getKind();
        if (!ALLOWED_TOP.contains(top)) {
            throw new SqlException("RESTRICTED 模式仅允许只读查询，顶层语句类型: " + top);
        }
        walk(node);
    }

    private static SqlNode parse(String sql) {
        SqlParser parser = SqlParser.create(sql, SqlParser.config());
        try {
            return parser.parseStmt();
        } catch (SqlParseException e) {
            throw new SqlException("RESTRICTED 模式 SQL 解析失败: " + e.getMessage(), e);
        }
    }

    private static void walk(SqlNode node) {
        if (node == null) {
            return;
        }
        SqlKind kind = node.getKind();
        if (BLOCKED.contains(kind)) {
            throw new SqlException("RESTRICTED 模式禁止 " + kind + " 语句");
        }
        if (node instanceof SqlSelect select) {
            walk(select.getSelectList());
            walk(select.getFrom());
            walk(select.getWhere());
            walk(select.getGroup());
            walk(select.getHaving());
            walk(select.getOrderList());
            walk(select.getWindowList());
            walk(select.getOffset());
            walk(select.getFetch());
            walk(select.getHints());
            return;
        }
        if (node instanceof SqlCall call) {
            for (SqlNode operand : call.getOperandList()) {
                walk(operand);
            }
            return;
        }
        if (node instanceof SqlNodeList list) {
            for (SqlNode n : list) {
                walk(n);
            }
            return;
        }
        if (node instanceof SqlLiteral || node instanceof SqlIdentifier
            || node instanceof SqlDynamicParam || node instanceof SqlIntervalQualifier
            || node instanceof SqlDataTypeSpec) {
            return;
        }
        throw new SqlException("RESTRICTED 模式遇到未识别节点类型: " + node.getClass().getName()
            + "（白名单默认拒绝）");
    }
}
