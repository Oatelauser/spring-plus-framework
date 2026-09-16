package io.github.oatelauser.springplus.calcite.memory.engine;

import io.github.oatelauser.springplus.calcite.memory.convert.TypeConverterRegistry;
import io.github.oatelauser.springplus.calcite.memory.exception.NestedMappingException;
import io.github.oatelauser.springplus.calcite.memory.exception.TypeException;
import io.github.oatelauser.springplus.calcite.memory.model.ColumnSpec;
import io.github.oatelauser.springplus.calcite.memory.model.SqlType;
import io.github.oatelauser.springplus.calcite.memory.model.TableSchema;
import io.github.oatelauser.springplus.calcite.memory.schema.MetadataAdapter;
import io.github.oatelauser.springplus.calcite.memory.schema.MetadataAdapterRegistry;
import io.github.oatelauser.springplus.calcite.memory.schema.PojoTableAdapter;
import io.github.oatelauser.springplus.calcite.memory.table.RowStore;
import io.github.oatelauser.springplus.calcite.memory.table.SnapshotRowStore;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.calcite.sql.type.SqlTypeName;

/**
 * 表构建器：将一份快照数据经 {@link MetadataAdapterRegistry} 解析适配器后，
 * 构造为不可变 {@link BuiltTable}（父表 + 可选子表）。供会话级与应用级注册共用，
 * 避免建表逻辑重复。
 *
 * <p>构建结果为 {@link SnapshotRowStore} 快照（ADR-002）：注册时数据即被拷贝为不可变行，
 * 之后对源集合的修改不影响已注册表。</p>
 */
public final class TableBuilder {

    private static final String SYNTHETIC_ROW_ID = "__row_id";

    private final TypeConverterRegistry converterRegistry;
    private final MetadataAdapterRegistry adapterRegistry;

    public TableBuilder(TypeConverterRegistry converterRegistry, MetadataAdapterRegistry adapterRegistry) {
        this.converterRegistry = converterRegistry;
        this.adapterRegistry = adapterRegistry;
    }

    /** 解析适配器：委托 {@link MetadataAdapterRegistry#resolve}。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public MetadataAdapter<Object> resolve(String tableName, List<?> items, Class<?> elementType) {
        return (MetadataAdapter<Object>) adapterRegistry.resolve(tableName, (List) items, (Class) elementType, converterRegistry);
    }

    /** 本次注册将新增的表数量（父表 + 子表）。用于资源限额预检。 */
    public int tablesToAdd(MetadataAdapter<?> adapter) {
        if (adapter instanceof PojoTableAdapter) {
            @SuppressWarnings("rawtypes") PojoTableAdapter pojo = (PojoTableAdapter) adapter;
            return 1 + pojo.childFields().size();
        }
        return 1;
    }


    /**
     * 自动推断元素类型：空 List、null 元素、类型不一致均抛 {@link io.github.oatelauser.springplus.calcite.memory.exception.RegistryException}。
     */
    public static Class<?> inferElementType(List<?> items, String tableName) {
        if (items == null) {
            throw new io.github.oatelauser.springplus.calcite.memory.exception.RegistryException("数据不能为 null: " + tableName);
        }
        if (items.isEmpty()) {
            throw new io.github.oatelauser.springplus.calcite.memory.exception.RegistryException(
                    "空 List 无法自动推断元素类型，请用显式指定元素类型: " + tableName);
        }
        Class<?> elementType = null;
        for (Object o : items) {
            if (o == null) {
                throw new io.github.oatelauser.springplus.calcite.memory.exception.RegistryException(
                        "表 " + tableName + " 不允许 null 元素");
            }
            if (elementType == null) {
                elementType = o.getClass();
            } else if (!elementType.isInstance(o)) {
                throw new io.github.oatelauser.springplus.calcite.memory.exception.RegistryException("表 " + tableName
                        + " 元素类型不一致: " + elementType.getName() + " 与 " + o.getClass().getName());
            }
        }
        return elementType;
    }

    /**
     * 构建表：若 POJO 适配器含 {@code @MemoryNested(CHILD_TABLE)} 子表，返回父表 + 各子表；否则返回单表。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<BuiltTable> build(String tableName, MetadataAdapter<Object> adapter, List<?> items) {
        if (adapter instanceof PojoTableAdapter pojo) {
            if (!pojo.childFields().isEmpty()) {
                return buildWithChildTables(pojo, items);
            }
        }
        return List.of(buildSingle(tableName, adapter, items));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private BuiltTable buildSingle(String tableName, MetadataAdapter<Object> adapter, List<?> items) {
        TableSchema tableSchema = adapter.inferSchema();
        List<Object[]> rows = new ArrayList<>(items.size());
        int rowNum = 0;
        for (Object item : items) {
            Object[] row = adapter.toRow(item);
            validateRow(tableSchema, row, rowNum);
            rows.add(row);
            rowNum++;
        }
        return new BuiltTable(tableName, tableSchema, new SnapshotRowStore(rows));
    }

    /**
     * 构建含子表的 POJO 表：先建父表（必要时追加合成 __row_id 主键列），
     * 再为每个 {@code @MemoryNested(CHILD_TABLE)} 集合字段建子表并写入外键。
     * 当前版本支持一级子表；多级嵌套子表抛 {@link NestedMappingException}。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<BuiltTable> buildWithChildTables(PojoTableAdapter parentAdapter, List items) {
        List<BuiltTable> result = new ArrayList<>();
        TableSchema baseSchema = parentAdapter.inferSchema();
        String parentTable = baseSchema.getTableName();
        List<PojoTableAdapter.ChildFieldBinding> childFields = parentAdapter.childFields();

        boolean needSyntheticRowId = false;
        List<ChildLink> links = new ArrayList<>(childFields.size());
        for (PojoTableAdapter.ChildFieldBinding cf : childFields) {
            int pkIdx = -1;
            String logicalKeyName = null;
            if (cf.parentKeyHint() != null && !cf.parentKeyHint().isEmpty()) {
                pkIdx = findColumnByPath(baseSchema, cf.parentKeyHint());
                if (pkIdx < 0) {
                    throw new NestedMappingException("子表 " + cf.childTableName()
                            + " 指定的 parentKey 字段 " + cf.parentKeyHint() + " 在父表不存在");
                }
                logicalKeyName = baseSchema.column(pkIdx).getName();
            } else {
                for (int i = 0; i < baseSchema.columnCount(); i++) {
                    if (baseSchema.column(i).isPrimaryKey()) {
                        pkIdx = i;
                        logicalKeyName = baseSchema.column(i).getName();
                        break;
                    }
                }
            }
            if (pkIdx < 0) {
                needSyntheticRowId = true;
                logicalKeyName = "row_id";
            }
            String fkColName = (cf.foreignKeyHint() != null && !cf.foreignKeyHint().isEmpty())
                    ? cf.foreignKeyHint()
                    : parentTable + "_" + logicalKeyName;
            links.add(new ChildLink(cf, pkIdx, fkColName));
        }

        int rowIdIdx = -1;
        List<ColumnSpec> parentCols = new ArrayList<>(baseSchema.getColumns());
        if (needSyntheticRowId) {
            rowIdIdx = parentCols.size();
            parentCols.add(ColumnSpec.builder()
                    .name(SYNTHETIC_ROW_ID)
                    .sqlType(SqlType.of(SqlTypeName.BIGINT, false))
                    .sourcePath(SYNTHETIC_ROW_ID)
                    .primaryKey(true)
                    .build());
        }
        TableSchema parentSchema = new TableSchema(parentTable, parentCols);

        List<Object[]> parentRows = new ArrayList<>(items.size());
        for (int r = 0; r < items.size(); r++) {
            Object[] base = parentAdapter.toRow(items.get(r));
            Object[] row;
            if (needSyntheticRowId) {
                row = new Object[base.length + 1];
                System.arraycopy(base, 0, row, 0, base.length);
                row[rowIdIdx] = (long) r;
            } else {
                row = base;
            }
            validateRow(parentSchema, row, r);
            parentRows.add(row);
        }
        result.add(new BuiltTable(parentTable, parentSchema, new SnapshotRowStore(parentRows)));

        for (ChildLink link : links) {
            result.add(buildChildTable(link, items, parentRows, parentSchema, rowIdIdx));
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private BuiltTable buildChildTable(ChildLink link, List parentItems, List<Object[]> parentRows,
            TableSchema parentSchema, int rowIdIdx) {
        PojoTableAdapter.ChildFieldBinding cf = link.binding();
        int parentKeyIdx = link.pkIdx() >= 0 ? link.pkIdx() : rowIdIdx;
        SqlType fkType = parentSchema.column(parentKeyIdx).getSqlType();

        PojoTableAdapter<Object> childAdapter =
                new PojoTableAdapter<>(cf.childTableName(), (Class) cf.childElementType(), converterRegistry);
        if (!childAdapter.childFields().isEmpty()) {
            throw new NestedMappingException("多级子表暂不支持: " + cf.childTableName()
                    + "（子表元素仍含 @MemoryNested(CHILD_TABLE) 集合）");
        }
        TableSchema childBase = childAdapter.inferSchema();
        int fkIdx = childBase.columnCount();
        List<ColumnSpec> childCols = new ArrayList<>(childBase.getColumns());
        childCols.add(ColumnSpec.builder()
                .name(link.fkColName())
                .sqlType(fkType)
                .sourcePath(link.fkColName())
                .primaryKey(false)
                .build());
        TableSchema childSchema = new TableSchema(cf.childTableName(), childCols);

        List<Object[]> childRows = new ArrayList<>();
        for (int r = 0; r < parentItems.size(); r++) {
            Object keyValue = parentRows.get(r)[parentKeyIdx];
            Collection<?> elements = extractCollection(parentItems.get(r), cf.parentPath());
            if (elements == null) {
                continue;
            }
            for (Object elem : elements) {
                if (elem == null) {
                    throw new TypeException("子表 " + cf.childTableName() + " 不允许 null 元素");
                }
                Object[] baseChild = childAdapter.toRow(elem);
                Object[] childRow = new Object[baseChild.length + 1];
                System.arraycopy(baseChild, 0, childRow, 0, baseChild.length);
                childRow[fkIdx] = keyValue;
                validateRow(childSchema, childRow, childRows.size());
                childRows.add(childRow);
            }
        }
        return new BuiltTable(cf.childTableName(), childSchema, new SnapshotRowStore(childRows));
    }

    private static Collection<?> extractCollection(Object root, List<Field> parentPath) {
        Object cur = root;
        for (Field f : parentPath) {
            try {
                cur = f.get(cur);
            } catch (IllegalAccessException e) {
                throw new TypeException("取值失败: " + f.getName(), e);
            }
            if (cur == null) {
                return null;
            }
        }
        if (cur == null) {
            return null;
        }
        if (cur instanceof Collection<?> c) {
            return c;
        }
        if (cur.getClass().isArray()) {
            int n = java.lang.reflect.Array.getLength(cur);
            List<Object> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(java.lang.reflect.Array.get(cur, i));
            }
            return list;
        }
        throw new TypeException("字段路径非集合类型: " + cur.getClass().getName());
    }

    private static int findColumnByPath(TableSchema schema, String pathOrName) {
        for (int i = 0; i < schema.columnCount(); i++) {
            ColumnSpec c = schema.column(i);
            if (pathOrName.equals(c.getSourcePath()) || pathOrName.equals(c.getName())) {
                return i;
            }
        }
        return -1;
    }

    private static void validateRow(TableSchema tableSchema, Object[] row, int rowNum) {
        for (int i = 0; i < tableSchema.columnCount(); i++) {
            if (!tableSchema.column(i).getSqlType().isNullable() && row[i] == null) {
                throw new TypeException("表 " + tableSchema.getTableName() + " 第 " + rowNum
                        + " 行列 " + tableSchema.column(i).getName() + " 声明 NOT NULL 但值为 null");
            }
        }
    }

    /** 构建产物：表名 + Schema + 不可变行存储。调用方据此 {@code new MemoryTable(schema, store)} 注册。 */
    public record BuiltTable(String name, TableSchema schema, RowStore store) {
    }

    /** 单个子表的关联配置：父键列下标（-1 表示用合成 __row_id）与子表外键列名。 */
    private record ChildLink(PojoTableAdapter.ChildFieldBinding binding, int pkIdx, String fkColName) {
    }
}
