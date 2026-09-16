package io.github.oatelauser.springplus.calcite.memory.schema;

import io.github.oatelauser.springplus.calcite.memory.convert.TypeConverter;
import io.github.oatelauser.springplus.calcite.memory.convert.TypeConverterRegistry;
import io.github.oatelauser.springplus.calcite.memory.exception.TypeException;
import io.github.oatelauser.springplus.calcite.memory.model.ColumnSpec;
import io.github.oatelauser.springplus.calcite.memory.model.SqlType;
import io.github.oatelauser.springplus.calcite.memory.model.TableSchema;

import java.util.List;

/**
 * 标量列表适配器：{@code List<String>} / {@code List<Integer>} 等单值元素列表映射为单列 value 表。
 * 列名固定为 {@value #COLUMN_NAME}，类型由元素类型推断。
 */
public class ScalarListAdapter<T> implements MetadataAdapter<T> {

    /**
     * 标量列表的唯一列名。
     */
    public static final String COLUMN_NAME = "value";

    private final TableSchema schema;
    private final TypeConverterRegistry registry;

    public ScalarListAdapter(String tableName, Class<T> elementType, TypeConverterRegistry registry) {
        if (elementType == null) {
            throw new TypeException("标量列表元素类型不能为 null: " + tableName);
        }
        this.registry = registry;
        SqlType sqlType = SqlType.of(ScalarTypeMapping.of(elementType, registry, COLUMN_NAME), true);
        this.schema = new TableSchema(tableName, List.of(
                ColumnSpec.builder()
                        .name(COLUMN_NAME)
                        .sqlType(sqlType)
                        .sourcePath(COLUMN_NAME)
                        .primaryKey(false)
                        .build()
        ));
    }

    @Override
    public TableSchema inferSchema() {
        return schema;
    }

    @Override
    public Object[] toRow(T item) {
        if (item == null) {
            throw new TypeException("标量行不能为 null（表 " + schema.getTableName() + "）");
        }
        TypeConverter conv = registry.resolve(item.getClass());
        return new Object[]{ conv == null ? item : conv.toStorage(item) };
    }
}
