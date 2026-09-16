package io.github.oatelauser.springplus.calcite.memory.jdbc;

import java.sql.SQLException;
import org.apache.calcite.jdbc.CalciteConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calcite 连接持有器：对外暴露 {@link CalciteConnection} 供直接 JDBC 调用，
 * 实现 {@link AutoCloseable} 以便 try-with-resources 管理生命周期。
 *
 * <p>关闭失败不抛出（与 {@code MemoryQuerySession} 保持一致），仅记录警告日志，
 * 避免在 try-with-resources 中掩盖业务异常。</p>
 *
 * <p>本类为薄封装：仅做生命周期管理，不持有 Schema 或其它可变状态，
 * 关闭后再次调用 {@link #get()} 的行为由底层 {@link CalciteConnection} 决定（通常抛出已关闭异常）。</p>
 */
public final class CalciteConnectionHolder implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CalciteConnectionHolder.class);

    private final CalciteConnection connection;

    CalciteConnectionHolder(CalciteConnection connection) {
        this.connection = connection;
    }

    /** 暴露底层 CalciteConnection 供 JDBC 调用。 */
    public CalciteConnection get() {
        return connection;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            log.warn("关闭 CalciteConnection 失败: {}", e.getMessage(), e);
        }
    }
}
