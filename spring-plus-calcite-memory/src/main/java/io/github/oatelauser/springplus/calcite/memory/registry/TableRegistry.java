package io.github.oatelauser.springplus.calcite.memory.registry;

import io.github.oatelauser.springplus.calcite.memory.engine.TableBuilder;
import io.github.oatelauser.springplus.calcite.memory.exception.RegistryException;
import io.github.oatelauser.springplus.calcite.memory.schema.MetadataAdapter;
import io.github.oatelauser.springplus.calcite.memory.table.MemorySchema;
import io.github.oatelauser.springplus.calcite.memory.table.MemoryTable;

import java.util.List;

/**
 * 表注册表：在指定 {@link MemorySchema} 上发布/删除快照表。
 *
 * <p>同名表重复发布即<b>版本替换</b>（{@link MemorySchema#register} 内部 {@code put} 覆盖旧表）。
 * 应用级实例绑定引擎共享 appSchema，对所有后续会话可见；
 * 会话级实例绑定会话私有 schema，仅本会话可见。</p>
 *
 * <p>本类不强制资源限额（无 {@code SessionConfig}）：限额由会话在注册时强制，
 * 应用级快照属可信运维数据，不在此处校验。</p>
 */
public final class TableRegistry {

    private final MemorySchema schema;
    private final TableBuilder tableBuilder;

    public TableRegistry(MemorySchema schema, TableBuilder tableBuilder) {
        this.schema = schema;
        this.tableBuilder = tableBuilder;
    }

    /**
     * 发布快照表（显式元素类型）。同名表覆盖。
     */
    public <T> void publishSnapshot(String tableName, List<T> items, Class<T> elementType) {
        if (tableName == null || tableName.isBlank()) {
            throw new RegistryException("表名不能为空");
        }
        if (items == null) {
            throw new RegistryException("数据不能为 null: " + tableName);
        }
        if (elementType == null) {
            throw new RegistryException("元素类型不能为 null: " + tableName);
        }
        MetadataAdapter<Object> adapter = tableBuilder.resolve(tableName, items, elementType);
        registerBuilt(tableName, adapter, items);
    }

    /**
     * 发布快照表（自动推断元素类型）。空 List、null 元素、类型不一致均抛 {@link RegistryException}。
     */
    public void publishSnapshot(String tableName, List<?> items) {
        if (tableName == null || tableName.isBlank()) {
            throw new RegistryException("表名不能为空");
        }
        Class<?> elementType = TableBuilder.inferElementType(items, tableName);
        MetadataAdapter<Object> adapter = tableBuilder.resolve(tableName, items, elementType);
        registerBuilt(tableName, adapter, items);
    }

    private void registerBuilt(String tableName, MetadataAdapter<Object> adapter, List<?> items) {
        List<TableBuilder.BuiltTable> built = tableBuilder.build(tableName, adapter, items);
        for (TableBuilder.BuiltTable t : built) {
            schema.register(t.name(), new MemoryTable(t.schema(), t.store()));
        }
    }

    /** 删除表；不存在返回 false。 */
    public boolean drop(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return false;
        }
        boolean existed = schema.get(tableName) != null;
        schema.unregister(tableName);
        return existed;
    }

    /** 当前已注册表数量。 */
    public int tableCount() {
        return schema.tableCount();
    }
}
