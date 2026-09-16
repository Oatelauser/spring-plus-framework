package io.github.oatelauser.springplus.calcite.memory.engine;

import org.apache.calcite.jdbc.CalciteConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 会话级 PreparedStatement 模板缓存：按参数化模板 SQL 复用语句，
 * 跳过 Calcite 每次查询重复的解析/校验/优化/linq4j 代码生成（固定开销的主要来源），
 * 同时使每个唯一模板只生成一份 linq4j 类，控制 Metaspace 增长。
 *
 * <p>失效与安全策略：
 * <ul>
 *   <li>Schema 版本失效：注册/删表后版本变化，缓存整体清空（陈旧计划引用旧表对象）</li>
 *   <li>LRU 有界：超上限逐出最久未用语句并关闭，防 ad-hoc 洪水撑爆缓存</li>
 *   <li>异常逐出：执行失败的语句移出缓存（可能处于坏状态，如被看门狗取消后）</li>
 *   <li>会话关闭时随连接统一释放</li>
 * </ul></p>
 *
 * <p>线程模型：会话为请求作用域单线程使用，方法加锁仅作防御性保护。</p>
 */
final class StatementCache {

    private static final Logger log = LoggerFactory.getLogger(StatementCache.class);

    private final int maxStatements;
    private final LinkedHashMap<String, PreparedStatement> statements;
    private long schemaVersion = -1L;

    StatementCache(int maxStatements) {
        this.maxStatements = maxStatements;
        this.statements = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, PreparedStatement> eldest) {
                boolean remove = size() > StatementCache.this.maxStatements;
                if (remove) {
                    closeQuietly(eldest.getValue());
                }
                return remove;
            }
        };
    }

    /**
     * 获取语句：命中直接返回；未命中则经连接 prepare 后入缓存。
     * schema 版本与缓存构建时不一致则先整体清空（表结构已变，旧计划作废）。
     */
    synchronized PreparedStatement acquire(CalciteConnection connection, String sql, long currentSchemaVersion)
            throws SQLException {
        if (currentSchemaVersion != schemaVersion) {
            closeAll();
            schemaVersion = currentSchemaVersion;
        }
        PreparedStatement ps = statements.get(sql);
        if (ps != null) {
            return ps;
        }
        ps = connection.prepareStatement(sql);
        statements.put(sql, ps);
        return ps;
    }

    /** 逐出指定语句并关闭（该语句执行失败，可能处于坏状态）。 */
    synchronized void evict(String sql) {
        PreparedStatement ps = statements.remove(sql);
        if (ps != null) {
            closeQuietly(ps);
        }
    }

    /** 会话关闭时调用：关闭并清空全部缓存语句。 */
    synchronized void close() {
        closeAll();
    }

    /** 当前缓存语句数（模板条目数），供测试与观测。 */
    synchronized int size() {
        return statements.size();
    }

    private void closeAll() {
        for (PreparedStatement ps : statements.values()) {
            closeQuietly(ps);
        }
        statements.clear();
    }

    private static void closeQuietly(PreparedStatement ps) {
        try {
            ps.close();
        } catch (SQLException e) {
            log.debug("关闭缓存语句失败: {}", e.getMessage());
        }
    }
}
