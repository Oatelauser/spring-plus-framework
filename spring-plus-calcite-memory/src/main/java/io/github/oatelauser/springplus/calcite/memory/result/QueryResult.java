package io.github.oatelauser.springplus.calcite.memory.result;

import java.util.List;

/**
 * 查询结果：列名 + 行集合，不可变。
 */
public class QueryResult {

    private final List<String> columnNames;
    private final List<Row> rows;

    public QueryResult(List<String> columnNames, List<Row> rows) {
        this.columnNames = List.copyOf(columnNames);
        this.rows = List.copyOf(rows);
    }

    public List<String> columnNames() {
        return columnNames;
    }

    public List<Row> rows() {
        return rows;
    }

    public int rowCount() {
        return rows.size();
    }

    public Row row(int index) {
        return rows.get(index);
    }
}
