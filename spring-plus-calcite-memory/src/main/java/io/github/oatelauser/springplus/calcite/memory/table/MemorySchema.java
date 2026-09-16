package io.github.oatelauser.springplus.calcite.memory.table;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;

/**
 * 内存 Schema：持有表名 -> {@link MemoryTable} 的并发映射，支持运行时注册/注销。
 * 直接覆写 {@link #getTable(String)} 与 {@link #getTableMap()}，避免父类缓存导致后注册表不可见。
 *
 * <p>结构版本号：任何注册/注销后递增，供语句缓存检测失效（陈旧的 PreparedStatement
 * 计划引用旧表对象，结构变化后必须重建）。</p>
 */
public class MemorySchema extends AbstractSchema {

    private final ConcurrentHashMap<String, MemoryTable> tables = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong();

    public void register(String name, MemoryTable table) {
        tables.put(name, table);
        version.incrementAndGet();
    }

    public void unregister(String name) {
        tables.remove(name);
        version.incrementAndGet();
    }

    public MemoryTable get(String name) {
        return tables.get(name);
    }

    /** 将本 Schema 的表引用复制到目标 Schema（用于会话初始化共享应用级表）。 */
    public void copyInto(MemorySchema target) {
        target.tables.putAll(tables);
        target.version.incrementAndGet();
    }

    public void clear() {
        tables.clear();
        version.incrementAndGet();
    }

    /** 当前结构版本号：每次表集合变化后递增。 */
    public long version() {
        return version.get();
    }

    @Override
    public Map<String, Table> getTableMap() {
        return new HashMap<>(tables);
    }


    /** 当前已注册表数量（零分配，用于资源限额检查）。 */
    public int tableCount() {
        return tables.size();
    }
}
