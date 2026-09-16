package io.github.oatelauser.springplus.calcite.memory.result;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 查询结果行：持有 Object[] 值与列名->下标映射，提供类型化取值。
 * 列名查找区分大小写；下标从 0 开始。
 */
public class Row {

    private final Object[] values;
    private final Map<String, Integer> indexByName;

    public Row(Object[] values, Map<String, Integer> indexByName) {
        this.values = values;
        this.indexByName = indexByName;
    }

    public Object getObject(int index) {
        return values[index];
    }

    public Object getObject(String name) {
        Integer idx = indexByName.get(name);
        if (idx == null) {
            throw new IllegalArgumentException("未知列: " + name);
        }
        return values[idx];
    }

    public String getString(String name) {
        Object v = getObject(name);
        return v == null ? null : v.toString();
    }

    public Integer getInt(String name) {
        Object v = getObject(name);
        return v == null ? null : ((Number) v).intValue();
    }

    public Long getLong(String name) {
        Object v = getObject(name);
        return v == null ? null : ((Number) v).longValue();
    }

    public BigDecimal getBigDecimal(String name) {
        Object v = getObject(name);
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(v.toString());
    }

    public int columnCount() {
        return values.length;
    }
}
