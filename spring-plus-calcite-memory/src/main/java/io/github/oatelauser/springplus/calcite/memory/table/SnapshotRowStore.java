package io.github.oatelauser.springplus.calcite.memory.table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 快照行存储：构造时对每行 Object[] 做防御性拷贝，注册后数据不再受源变更影响。
 */
public class SnapshotRowStore implements RowStore {

    private final List<Object[]> rows;

    public SnapshotRowStore(List<Object[]> source) {
        List<Object[]> copy = new ArrayList<>(source.size());
        for (Object[] row : source) {
            copy.add(row.clone());
        }
        this.rows = Collections.unmodifiableList(copy);
    }

    @Override
    public List<Object[]> rows() {
        return rows;
    }

    @Override
    public int rowCount() {
        return rows.size();
    }
}
