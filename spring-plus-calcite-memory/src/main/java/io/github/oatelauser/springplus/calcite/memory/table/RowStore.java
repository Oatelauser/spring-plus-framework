package io.github.oatelauser.springplus.calcite.memory.table;

import java.util.List;

/**
 * 行存储接口：提供不可变行视图。
 * 默认实现 {@link SnapshotRowStore} 在构造时固化数据副本（ADR-002 快照语义）。
 */
public interface RowStore {

    /** 不可变行视图，每行为 Object[]。 */
    List<Object[]> rows();

    int rowCount();
}
