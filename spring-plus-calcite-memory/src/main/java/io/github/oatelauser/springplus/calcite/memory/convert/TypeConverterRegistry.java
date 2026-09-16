package io.github.oatelauser.springplus.calcite.memory.convert;

import io.github.oatelauser.springplus.calcite.memory.convert.impl.BigDecimalConverter;
import io.github.oatelauser.springplus.calcite.memory.convert.impl.EnumConverter;
import io.github.oatelauser.springplus.calcite.memory.convert.impl.TemporalConverter;
import io.github.oatelauser.springplus.calcite.memory.convert.impl.UuidConverter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 类型转换器注册表，线程安全。
 * {@link #resolve(Class)} 按注册顺序返回首个支持的转换器，因此更具体的转换器应先注册。
 */
public class TypeConverterRegistry {

    private final List<TypeConverter> converters = new CopyOnWriteArrayList<>();

    public void register(TypeConverter converter) {
        converters.add(converter);
    }

    public TypeConverter resolve(Class<?> javaType) {
        if (javaType == null) {
            return null;
        }
        for (TypeConverter c : converters) {
            if (c.supports(javaType)) {
                return c;
            }
        }
        return null;
    }

    /** 内置默认转换器：枚举、UUID、时间、BigDecimal。 */
    public static TypeConverterRegistry withDefaults() {
        TypeConverterRegistry registry = new TypeConverterRegistry();
        registry.register(new EnumConverter());
        registry.register(new UuidConverter());
        registry.register(new TemporalConverter());
        registry.register(new BigDecimalConverter());
        return registry;
    }
}
