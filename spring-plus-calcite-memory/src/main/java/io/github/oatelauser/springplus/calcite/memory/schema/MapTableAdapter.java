package io.github.oatelauser.springplus.calcite.memory.schema;

import io.github.oatelauser.springplus.calcite.memory.convert.TypeConverter;
import io.github.oatelauser.springplus.calcite.memory.convert.TypeConverterRegistry;
import io.github.oatelauser.springplus.calcite.memory.exception.RegistryException;
import io.github.oatelauser.springplus.calcite.memory.exception.SchemaException;
import io.github.oatelauser.springplus.calcite.memory.exception.TypeException;
import io.github.oatelauser.springplus.calcite.memory.model.ColumnSpec;
import io.github.oatelauser.springplus.calcite.memory.model.SqlType;
import io.github.oatelauser.springplus.calcite.memory.model.TableSchema;
import org.apache.calcite.sql.type.SqlTypeName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Map 元数据适配器：以 {@code List<Map<String,Object>>} 为输入，按键推断 Schema 并提取行。
 *
 * <p>推断语义：
 * <ul>
 *   <li>列 = Map 键的首次出现顺序；列名取键原样。</li>
 *   <li>同列跨行类型经 {@link SchemaMerger} 安全提升；跨族（整数与浮点、数值与字符等）直接抛错，
 *       绝不静默退化为 VARCHAR。</li>
 *   <li>全 null 列默认 VARCHAR；空 Map 或空 List 抛错。</li>
 *   <li>行数超过 {@value #SAMPLE_LIMIT} 时仅采样前 {@value #SAMPLE_LIMIT} 行推断 Schema，
 *       超出采样的新键将被丢弃（已知限制）。</li>
 * </ul>
 * 所有列默认可空（Map 值可能缺失或为 null）。
 */
public class MapTableAdapter implements MetadataAdapter<Map<String, Object>> {

    /** 超过此行数仅采样推断 Schema，避免全量扫描开销。 */
    public static final int SAMPLE_LIMIT = 10000;
    private static final Logger log = LoggerFactory.getLogger(MapTableAdapter.class);

    private final TableSchema schema;
    private final List<String> keyOrder;
    private final TypeConverterRegistry registry;

    public MapTableAdapter(String tableName, List<Map<String, Object>> rows, TypeConverterRegistry registry) {
        if (tableName == null || tableName.isBlank()) {
            throw new RegistryException("表名不能为空");
        }
        if (rows == null) {
            throw new RegistryException("Map 数据不能为 null: " + tableName);
        }
        if (registry == null) {
            throw new RegistryException("TypeConverterRegistry 不能为 null: " + tableName);
        }
        this.registry = registry;
        for (int i = 0; i < rows.size(); i++) {
            Object o = rows.get(i);
            if (o == null) {
                throw new TypeException("Map 表 " + tableName + " 第 " + i + " 行不允许 null 元素");
            }
            if (!(o instanceof Map<?, ?>)) {
                throw new TypeException("Map 表 " + tableName + " 第 " + i + " 行必须是 Map，实际: "
                    + o.getClass().getName());
            }
        }
        this.keyOrder = new ArrayList<>();
        this.schema = infer(tableName, rows);
    }

    private TableSchema infer(String tableName, List<Map<String, Object>> rows) {
        int scanCount = Math.min(rows.size(), SAMPLE_LIMIT);
        if (rows.size() > SAMPLE_LIMIT) {
            log.warn("Map 表 {} 数据 {} 行超过采样上限 {}，仅扫描前 {} 行推断 Schema",
                tableName, rows.size(), SAMPLE_LIMIT, scanCount);
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        Map<String, SqlType> types = new java.util.HashMap<>();
        for (int i = 0; i < scanCount; i++) {
            Map<String, Object> row = rows.get(i);
            for (Map.Entry<String, Object> e : row.entrySet()) {
                String key = e.getKey();
                if (key == null) {
                    throw new SchemaException("Map 表 " + tableName + " 第 " + i + " 行存在 null 键");
                }
                if (keys.add(key)) {
                    types.put(key, null);
                }
                Object v = e.getValue();
                if (v == null) {
                    continue;
                }
                SqlTypeName name = ScalarTypeMapping.of(v.getClass(), registry, key);
                SqlType inferred = SqlType.of(name, true);
                SqlType cur = types.get(key);
                types.put(key, cur == null ? inferred : SchemaMerger.merge(cur, inferred));
            }
        }
        if (keys.isEmpty()) {
            throw new SchemaException("Map 表 " + tableName + " 无可注册列（所有行均为空 Map）");
        }
        List<ColumnSpec> columns = new ArrayList<>(keys.size());
        for (String key : keys) {
            SqlType type = types.get(key);
            if (type == null) {
                type = SqlType.varchar(65536, true);
            }
            columns.add(ColumnSpec.builder()
                .name(key)
                .sqlType(type)
                .sourcePath(key)
                .primaryKey(false)
                .build());
            keyOrder.add(key);
        }
        return new TableSchema(tableName, columns);
    }

    @Override
    public TableSchema inferSchema() {
        return schema;
    }

    @Override
    public Object[] toRow(Map<String, Object> item) {
        if (item == null) {
            throw new TypeException("Map 行不能为 null（表 " + schema.getTableName() + "）");
        }
        Object[] row = new Object[keyOrder.size()];
        for (int i = 0; i < keyOrder.size(); i++) {
            Object v = item.get(keyOrder.get(i));
            if (v == null) {
                row[i] = null;
                continue;
            }
            TypeConverter conv = registry.resolve(v.getClass());
            Object storage = conv == null ? v : conv.toStorage(v);
            row[i] = coerce(storage, schema.column(i).getSqlType());
        }
        return row;
    }


    /**
     * 将存储值按列的合并 SQL 类型做数值族提升。
     * <p>Map 同列跨行可能为 Integer/Long/BigDecimal 等不同具体类型，合并后列声明为统一类型（如 BIGINT/DECIMAL），
     * 需把每个值转换为声明类型，否则 Calcite 读取时类型不匹配抛 ClassCastException。
     * 非数值列原样返回。BigDecimal 一律用 String 构造，避免 double 精度坑。
     */
    private static Object coerce(Object v, SqlType target) {
        if (v == null) {
            return null;
        }
        switch (target.getTypeName()) {
            case BIGINT:
                if (v instanceof Long) {
                    return v;
                }
                if (v instanceof Number) {
                    return ((Number) v).longValue();
                }
                return v;
            case INTEGER:
                if (v instanceof Integer) {
                    return v;
                }
                if (v instanceof Number) {
                    return ((Number) v).intValue();
                }
                return v;
            case SMALLINT:
                if (v instanceof Short) {
                    return v;
                }
                if (v instanceof Number) {
                    return ((Number) v).shortValue();
                }
                return v;
            case TINYINT:
                if (v instanceof Byte) {
                    return v;
                }
                if (v instanceof Number) {
                    return ((Number) v).byteValue();
                }
                return v;
            case DECIMAL:
                if (v instanceof java.math.BigDecimal) {
                    return v;
                }
                if (v instanceof java.math.BigInteger) {
                    return new java.math.BigDecimal((java.math.BigInteger) v);
                }
                if (v instanceof Number) {
                    return new java.math.BigDecimal(v.toString());
                }
                return v;
            case DOUBLE:
                if (v instanceof Double) {
                    return v;
                }
                if (v instanceof Number) {
                    return ((Number) v).doubleValue();
                }
                return v;
            case FLOAT:
            case REAL:
                if (v instanceof Float) {
                    return v;
                }
                if (v instanceof Number) {
                    return ((Number) v).floatValue();
                }
                return v;
            default:
                return v;
        }
    }
}
