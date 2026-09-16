package io.github.oatelauser.springplus.calcite.memory.schema;

import io.github.oatelauser.springplus.calcite.memory.convert.TypeConverterRegistry;
import io.github.oatelauser.springplus.calcite.memory.exception.RegistryException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 元数据适配器注册表：按优先级为给定元素类型选择适配器。
 *
 * <p>选择规则：在所有 {@link MetadataAdapterFactory#supports(Class)} 为真的工厂中，取最高优先级者；
 * 若最高优先级存在多个工厂，视为冲突抛 {@link RegistryException}（绝不静默二选一）。
 *
 * <p>内置工厂（默认注册）：
 * <ul>
 *   <li>{@link MapAdapterFactory}（优先级 30）- Map 元素</li>
 *   <li>{@link ScalarAdapterFactory}（优先级 20）- 标量元素（String/Number/时间/枚举等）</li>
 *   <li>{@link PojoAdapterFactory}（优先级 10）- 其余 POJO 元素</li>
 * </ul>
 * 三者 supports 互斥，默认不冲突；用户可注册更高优先级工厂覆盖特定类型。
 */
public final class MetadataAdapterRegistry {

    private final List<FactoryEntry> entries = new ArrayList<>();

    public MetadataAdapterRegistry register(int priority, MetadataAdapterFactory factory) {
        if (factory == null) {
            throw new RegistryException("工厂不能为 null");
        }
        entries.add(new FactoryEntry(priority, factory));
        return this;
    }

    /**
     * 解析适配器：取支持该类型的最高优先级工厂创建适配器；同优先级多工厂视为冲突。
     */
    public <U> MetadataAdapter<U> resolve(String tableName, List<U> items,
            Class<U> elementType, TypeConverterRegistry registry) {
        if (elementType == null) {
            throw new RegistryException("元素类型不能为 null: " + tableName);
        }
        int bestPriority = Integer.MIN_VALUE;
        MetadataAdapterFactory best = null;
        int bestCount = 0;
        for (FactoryEntry e : entries) {
            if (e.factory.supports(elementType)) {
                if (e.priority > bestPriority) {
                    bestPriority = e.priority;
                    best = e.factory;
                    bestCount = 1;
                } else if (e.priority == bestPriority) {
                    bestCount++;
                }
            }
        }
        if (best == null) {
            throw new RegistryException("无适配器支持元素类型: " + elementType.getName()
                    + "（表 " + tableName + "）");
        }
        if (bestCount > 1) {
            throw new RegistryException("元素类型 " + elementType.getName()
                    + " 匹配多个同优先级(" + bestPriority + ")适配器，存在冲突（表 " + tableName + "）");
        }
        return best.create(tableName, items, elementType, registry);
    }

    /**
     * 默认注册表：内置 Map / 标量 / POJO 工厂。
     */
    public static MetadataAdapterRegistry withDefaults() {
        MetadataAdapterRegistry r = new MetadataAdapterRegistry();
        r.register(30, new MapAdapterFactory());
        r.register(20, new ScalarAdapterFactory());
        r.register(10, new PojoAdapterFactory());
        return r;
    }

    /**
     * Map 元素工厂。
     */
    public static final class MapAdapterFactory implements MetadataAdapterFactory {
        @Override
        public boolean supports(Class<?> elementType) {
            return Map.class.isAssignableFrom(elementType);
        }

        @Override
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public <U> MetadataAdapter<U> create(String tableName, List<U> items, Class<U> elementType,
                TypeConverterRegistry registry) {
            List<Map<String, Object>> rows = (List<Map<String, Object>>) (List) items;
            return (MetadataAdapter<U>) new MapTableAdapter(tableName, rows, registry);
        }
    }

    /**
     * 标量元素工厂。
     */
    public static final class ScalarAdapterFactory implements MetadataAdapterFactory {
        @Override
        public boolean supports(Class<?> elementType) {
            return ScalarTypeMapping.isScalarValueType(elementType);
        }

        @Override
        public <U> MetadataAdapter<U> create(String tableName, List<U> items, Class<U> elementType,
                TypeConverterRegistry registry) {
            return new ScalarListAdapter<>(tableName, elementType, registry);
        }
    }

    /**
     * POJO 元素工厂（兜底）。
     */
    public static final class PojoAdapterFactory implements MetadataAdapterFactory {
        @Override
        public boolean supports(Class<?> elementType) {
            return !Map.class.isAssignableFrom(elementType)
                    && !ScalarTypeMapping.isScalarValueType(elementType);
        }

        @Override
        public <U> MetadataAdapter<U> create(String tableName, List<U> items, Class<U> elementType,
                TypeConverterRegistry registry) {
            return new PojoTableAdapter<>(tableName, elementType, registry);
        }
    }

    private record FactoryEntry(int priority, MetadataAdapterFactory factory) {
    }
}
