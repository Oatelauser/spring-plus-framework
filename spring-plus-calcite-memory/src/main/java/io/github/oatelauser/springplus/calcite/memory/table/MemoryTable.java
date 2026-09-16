package io.github.oatelauser.springplus.calcite.memory.table;

import io.github.oatelauser.springplus.calcite.memory.model.ColumnSpec;
import io.github.oatelauser.springplus.calcite.memory.model.SqlType;
import io.github.oatelauser.springplus.calcite.memory.model.TableSchema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.apache.calcite.DataContext;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.schema.ProjectableFilterableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.Pair;

/**
 * 内存表：基于 {@link AbstractTable} 实现 {@link ProjectableFilterableTable}。
 * <ul>
 *   <li>投影下推：只输出请求列；</li>
 *   <li>谓词下推：处理 IS NULL / IS NOT NULL / 等值（EQUALS）三类简单条件，其余条件必须留在 filters 交还 Calcite。</li>
 * </ul>
 * 遵守契约：未处理的过滤器不从列表移除，错误声明已处理会导致结果错误。
 */
public class MemoryTable extends AbstractTable implements ProjectableFilterableTable {

    private final TableSchema schema;
    private final RowStore store;

    public MemoryTable(TableSchema schema, RowStore store) {
        this.schema = schema;
        this.store = store;
    }

    public TableSchema schema() {
        return schema;
    }

    @Override
    public RelDataType getRowType(RelDataTypeFactory typeFactory) {
        RelDataTypeFactory.Builder builder = typeFactory.builder();
        for (ColumnSpec c : schema.getColumns()) {
            SqlType st = c.getSqlType();
            builder.add(c.getName(), createType(typeFactory, st)).nullable(st.isNullable());
        }
        return builder.build();
    }

    private static RelDataType createType(RelDataTypeFactory f, SqlType st) {
        SqlTypeName name = st.getTypeName();
        if (name == SqlTypeName.VARCHAR) {
            int prec = st.getPrecision() != null ? st.getPrecision() : 65536;
            return f.createSqlType(SqlTypeName.VARCHAR, prec);
        }
        if (name == SqlTypeName.DECIMAL) {
            int prec = st.getPrecision() != null ? st.getPrecision() : 19;
            int scale = st.getScale() != null ? st.getScale() : 4;
            return f.createSqlType(SqlTypeName.DECIMAL, prec, scale);
        }
        return f.createSqlType(name);
    }

    @Override
    public Enumerable<Object[]> scan(DataContext root, List<RexNode> filters, int[] projects) {
        List<Predicate<Object[]>> predicates = new ArrayList<>();
        Iterator<RexNode> it = filters.iterator();
        while (it.hasNext()) {
            RexNode f = it.next();
            Predicate<Object[]> p = toPredicate(f);
            if (p != null) {
                predicates.add(p);
                it.remove();
            }
        }
        final int[] proj = projects == null ? identityProjects(schema.columnCount()) : projects;
        List<Object[]> result = new ArrayList<>();
        for (Object[] row : store.rows()) {
            boolean keep = true;
            for (Predicate<Object[]> p : predicates) {
                if (!p.test(row)) {
                    keep = false;
                    break;
                }
            }
            if (keep) {
                Object[] projected = new Object[proj.length];
                for (int i = 0; i < proj.length; i++) {
                    projected[i] = row[proj[i]];
                }
                result.add(projected);
            }
        }
        return Linq4j.asEnumerable(result);
    }

    /**
     * 将可处理的 RexNode 转为行谓词；不可处理返回 null（保留在 filters 中）。
     */
    private static Predicate<Object[]> toPredicate(RexNode node) {
        if (!(node instanceof RexCall call)) {
            return null;
        }
        SqlKind kind = call.getKind();
        if (kind == SqlKind.IS_NULL || kind == SqlKind.IS_NOT_NULL) {
            if (call.getOperands().size() != 1
                || !(call.getOperands().get(0) instanceof RexInputRef ref)) {
                return null;
            }
            int idx = ref.getIndex();
            return kind == SqlKind.IS_NULL
                ? row -> row[idx] == null
                : row -> row[idx] != null;
        }
        if (kind == SqlKind.EQUALS) {
            Pair<Integer, Object> pair = extractRefLiteral(call);
            if (pair == null) {
                return null;
            }
            int idx = pair.left;
            Object lit = pair.right;
            return row -> equalsValue(row[idx], lit);
        }
        return null;
    }

    private static Pair<Integer, Object> extractRefLiteral(RexCall call) {
        List<RexNode> ops = call.getOperands();
        if (ops.size() != 2) {
            return null;
        }
        RexNode a = ops.get(0);
        RexNode b = ops.get(1);
        if (a instanceof RexInputRef ref && b instanceof RexLiteral lit) {
            return Pair.of(ref.getIndex(), literalValue(lit));
        }
        if (b instanceof RexInputRef ref && a instanceof RexLiteral lit) {
            return Pair.of(ref.getIndex(), literalValue(lit));
        }
        return null;
    }

    /**
     * 提取字面量值：Calcite 字符串字面量以 {@link org.apache.calcite.sql.NlsString} 承载，
     * 需取出其内部 String 才能与行值比较；其余类型保持原样。
     */
    private static Object literalValue(RexLiteral lit) {
        Object v = lit.getValue();
        if (v instanceof org.apache.calcite.util.NlsString ns) {
            return ns.getValue();
        }
        return v;
    }

    /**
     * 等值比较：数值统一转 BigDecimal 用 compareTo（避免 scale 与缓存坑）；其余用 Objects.equals。
     * 任一为 null 视为不等（null 由 IS NULL 处理）。
     */
    private static boolean equalsValue(Object rowVal, Object lit) {
        if (rowVal == null || lit == null) {
            return false;
        }
        if (rowVal instanceof Number && lit instanceof Number) {
            return new BigDecimal(rowVal.toString()).compareTo(new BigDecimal(lit.toString())) == 0;
        }
        return Objects.equals(rowVal, lit);
    }

    private static int[] identityProjects(int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = i;
        }
        return a;
    }
}
