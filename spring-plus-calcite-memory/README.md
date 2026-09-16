# spring-plus-calcite-memory

基于 Apache Calcite 的内存表 SQL 查询框架：把 POJO / Map / List 注册为内存临时表，用标准 SQL 查询——面向规则过滤、内存报表、测试数据筛选等场景。

## 能力清单

| 能力 | 入口 |
|---|---|
| 表注册注解 | `@MemoryTable` / `@MemoryColumn` / `@MemoryId` / `@MemoryNested` |
| 查询引擎 | `MemoryQueryEngine` / `MemoryQuerySession`（会话级隔离） |
| Schema 适配 | `PojoTableAdapter` / `MapTableAdapter` / `SchemaMerger` |
| 类型转换 | `TypeConverter` 体系 |
| SQL 安全 | `SqlAstWhitelist`（AST 白名单）/ `ResourceLimits`（资源限额）/ `SqlSafetyMode` |
| MyBatis 集成 | `CalciteDataSource`（optional，经原生 ibatis API 构建会话） |
| 指标 | `MicrometerMemoryMetrics`（optional） |
| JDBC 直连 | `jdbc` 包（标准 JDBC URL 接入） |

## 坐标

```xml
<dependency>
    <groupId>io.github.oatelauser</groupId>
    <artifactId>spring-plus-calcite-memory</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

本模块**完全独立**：不依赖 spring-plus 其他模块，Spring 集成（autoconfigure / micrometer / web 请求级会话绑定）均为 optional。

## 用法

```java
// 注解声明内存表
@MemoryTable("users")
public class UserRow {
    @MemoryId Long id;
    @MemoryColumn(name = "name") String name;
    @MemoryColumn(name = "room") String roomNo;
}

// 注册并查询（Boot 应用自动装配 MemoryQueryEngine）
engine.register("users", userRows);

try (MemoryQuerySession session = engine.openSession()) {
    // 含外部输入的查询一律绑参（防注入），注入串只会作为数据比较
    List<UserRow> result = session.query(
            "SELECT id, name FROM users WHERE room = ? ORDER BY id", "A-101");
}
```

## 工程质量

- 92 个测试：功能（schema/engine/registry/jdbc/mybatis 集成）+ 稳定性（会话泄漏 / 并发 / 大规模注册）+ 自动配置（ApplicationContextRunner）
- JMH 基准（`MemoryQueryBenchmark`，test scope，不随发布运行）
- 会话生命周期：Web 环境下经 `RequestContextHolder` 绑定请求级会话（optional 集成，非 Web 应用不生效）

## 已知注意事项

- MyBatis 集成为 optional：消费方引入 mybatis-flex-core 后才可使用 `CalciteDataSource`
- SQL 执行走 AST 白名单校验，非白名单语句直接拒绝（防注入面）
- 内存表数据量受 `ResourceLimits` 约束，超限快速失败而不是拖垮 JVM
