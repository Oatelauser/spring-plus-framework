package io.github.oatelauser.springplus.calcite.memory.schema;

import io.github.oatelauser.springplus.calcite.memory.model.TableSchema;

/**
 * 元数据适配器扩展点：负责从数据源推断 {@link TableSchema} 并将单条记录转为 Object[] 行。
 * <ul>
 *   <li>POJO 场景：{@link PojoTableAdapter}</li>
 *   <li>Map 场景：P2 提供</li>
 *   <li>自定义：用户实现本接口并注入引擎</li>
 * </ul>
 */
public interface MetadataAdapter<T> {

    /**
     * 推断表 Schema。
     */
    TableSchema inferSchema();

    /**
     * 将单条记录转为 Object[] 行，顺序与 Schema 列顺序一致。
     */
    Object[] toRow(T item);
}
