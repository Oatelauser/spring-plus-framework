package io.github.oatelauser.springplus.calcite.memory.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Value;

/**
 * 内存表 Schema：表名 + 有序列 + 列名索引。
 * 不可变值对象；构造时固化列名->下标映射。
 */
@Value
public class TableSchema {

    String tableName;
    List<ColumnSpec> columns;
    Map<String, Integer> indexByName;

    public TableSchema(String tableName, List<ColumnSpec> columns) {
        this.tableName = tableName;
        this.columns = List.copyOf(columns);
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            idx.put(columns.get(i).getName(), i);
        }
        this.indexByName = Map.copyOf(idx);
    }

    public Integer indexOf(String columnName) {
        return indexByName.get(columnName);
    }

    public int columnCount() {
        return columns.size();
    }

    public ColumnSpec column(int index) {
        return columns.get(index);
    }
}
