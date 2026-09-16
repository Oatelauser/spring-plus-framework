package io.github.oatelauser.springplus.calcite.memory.mybatis;

import io.github.oatelauser.springplus.calcite.memory.jdbc.CalciteJdbcBootstrap;
import io.github.oatelauser.springplus.calcite.memory.table.MemorySchema;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Calcite 内存数据源：{@link DataSource} 适配，{@code getConnection} 返回绑定到共享
 * {@link MemorySchema} 的新 {@link org.apache.calcite.jdbc.CalciteConnection}，
 * 供 MyBatis 等第三方 ORM 以标准 JDBC 方式接入内存表。
 *
 * <p>该数据源是 MyBatis 独立会话的底层连接工厂，与生产 MySQL 数据源<b>完全隔离</b>：
 * 不参与事务恢复、不维护连接池（内存库查询短平快，连接随 SqlSession 创建/关闭）、无鉴权
 * （{@link #getConnection(String, String)} 忽略凭证）。</p>
 *
 * <p>线程安全：底层 {@link MemorySchema} 并发安全，{@code getConnection} 每次返回独立连接，
 * 可被多线程（如 MyBatis 的不同 SqlSession）并发调用。</p>
 */
public final class CalciteDataSource implements DataSource {

    private final MemorySchema schema;

    public CalciteDataSource(MemorySchema schema) {
        if (schema == null) {
            throw new IllegalArgumentException("MemorySchema 不能为 null");
        }
        this.schema = schema;
    }

    @Override
    public Connection getConnection() {
        return CalciteJdbcBootstrap.openConnection(schema);
    }

    @Override
    public Connection getConnection(String username, String password) {
        // Calcite 内存库无鉴权，忽略凭证
        return getConnection();
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        // 无操作：不暴露日志 writer
    }

    @Override
    public void setLoginTimeout(int seconds) {
        // 无操作：内存库连接即时建立，无登录超时概念
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("CalciteDataSource 不支持 parent logger");
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface == null) {
            throw new SQLException("接口类型不能为 null");
        }
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("CalciteDataSource 不是 " + iface + " 的包装器");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface != null && iface.isInstance(this);
    }
}
