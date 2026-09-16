package io.github.oatelauser.springplus.calcite.memory.schema;

import io.github.oatelauser.springplus.calcite.memory.convert.TypeConverterRegistry;

import java.util.List;

/**
 * 元数据适配器工厂：根据元素类型创建 {@link MetadataAdapter}。
 *
 * <p>优先级由 {@link MetadataAdapterRegistry#register(int, MetadataAdapterFactory)} 注册时指定；
 * 同优先级且同时支持某类型时视为冲突并抛错（绝不静默二选一）。
 */
public interface MetadataAdapterFactory {

    /**
     * 是否处理该元素类型。
     */
    boolean supports(Class<?> elementType);

    /**
     * 创建适配器。items 用于需要采样推断的适配器（如 Map）；POJO/标量适配器可仅凭 elementType 推断。
     *
     * @param tableName   表名
     * @param items       原始数据列表
     * @param elementType 元素类型
     * @param registry    类型转换器注册表
     */
    <U> MetadataAdapter<U> create(String tableName, List<U> items, Class<U> elementType, TypeConverterRegistry registry);

}
