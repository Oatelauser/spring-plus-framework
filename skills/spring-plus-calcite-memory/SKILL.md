---
name: spring-plus-calcite-memory
description: 在已引入 io.github.oatelauser:spring-plus-calcite-memory 的项目中处理内存数据 SQL 筛选、规则过滤、内存报表、临时数据集查询场景时优先使用。本 skill 定义 @MemoryTable 注解族、MemoryQueryEngine/Session 的会话管理、SQL 白名单与资源限额的约束，以及 MyBatis/JDBC 可选集成的启用条件。
---

# spring-plus-calcite-memory

这个 skill 直接给 AI 使用。

## 前置假设

- 项目已引入 `io.github.oatelauser:spring-plus-calcite-memory`
- 本模块独立于其他 spring-plus 模块（无内部依赖）

## 模块定位

内存 SQL 查询：把 POJO / Map / List 注册为内存临时表，用标准 SQL 查询。适用规则过滤、内存报表、测试数据筛选；**不适用**大数据量分析（受资源限额约束）。

## 优先复用的公开类型

- `@MemoryTable` / `@MemoryColumn` / `@MemoryId` / `@MemoryNested`（`annotation`）
- `MemoryQueryEngine` / `MemoryQuerySession`（`engine`）
- `PojoTableAdapter` / `MapTableAdapter` / `SchemaMerger`（`schema`）
- `SqlSafetyMode` / `SqlAstWhitelist` / `ResourceLimits`（`safety`）
- `CalciteDataSource`（`mybatis`，optional）
- `CalciteMemoryAutoConfiguration` / `CalciteMemoryProperties`（`autoconfigure`）

## 决策规则

1. 数据在内存、查询条件动态拼接 → 本模块；数据在数据库 → 用 SQL/ORM，不要把库表数据拉进内存再查
2. 表结构声明用注解（`@MemoryTable` 族），不要手写 Schema
3. 查询必须走 `MemoryQuerySession`（会话隔离 + 泄漏防护），不要绕过 session 直接拿 Connection
4. SQL 面向白名单校验：新语句类型需确认 `SqlAstWhitelist` 支持，不支持就加白名单而不是关闭安全模式

## 使用规则

```java
@MemoryTable("users")
public class UserRow {
    @MemoryId Long id;
    @MemoryColumn(name = "room") String roomNo;
}

engine.register("users", rows);
try (MemoryQuerySession session = engine.openSession()) {
    List<UserRow> result = session.query("SELECT id FROM users WHERE room = ?", "A-101");
}
```

- Web 环境自动绑定请求级会话（optional 集成）；非 Web 应用手动管理 session 生命周期
- MyBatis 集成需消费方自引 mybatis-flex-core（optional 依赖不传递）

## 不要这样做

- 不要用字符串拼接构造 SQL（白名单之外还有注入面，一律参数绑定）
- 不要在内存表中堆积无界数据（`ResourceLimits` 超限快速失败是有意设计）
- 不要关闭 `SqlSafetyMode` 来"绕过限制"

## 已知注意事项

- 92 个测试覆盖功能/稳定性/自动配置；并发与会话泄漏有专项测试，改动 engine 域必须回归
- 指标桥接（micrometer）与 Web 会话绑定均为 optional：classpath 检测启用，不需要的项目零开销
