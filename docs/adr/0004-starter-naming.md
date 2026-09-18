# 全家族 starter 化重命名

修订 [ADR 0001](./0001-capability-domain-module-split.md) 弃用「核心库 + xxx-spring-boot-starter 装配胶水层」时的命名结论：不引入装配胶水层，但坐标命名向 starter 语义靠拢。

## Context

- 每个 spring-plus 模块都自带 `AutoConfiguration.imports` 注册，消费方"引依赖即自动装配"，行为上就是 starter，坐标却未体现——名实不符。
- 家族缺统一后缀，`spring-plus-boot` 这类名字既看不出"引了就生效"，也容易与 Spring Boot 官方保留的 `spring-boot-starter-*` 前缀模式混淆。
- `spring-plus-calcite-memory` 自 1.1.0 起已退出 Central 发布范围，定位鸡肋，后续将删除或移至他处，不值得投入重命名。

## Considered Options

- **全家族统一加 `-starter` 后缀（已选）**：5 个发布模块改名 `spring-plus-boot-starter` / `spring-plus-web-starter` / `spring-plus-redis-starter` / `spring-plus-governor-starter` / `spring-plus-security-starter`，模块目录与 skills/ 技能目录随坐标同步改名。
- **仿官方第三方惯例 `xxx-spring-boot-starter`（弃）**：对本项目会是 `spring-plus-web-spring-boot-starter` 这类双品牌嵌套，冗长难读；且本项目非"库 + 装配层"结构（ADR 0001），无必要对齐该惯例。
- **仅 boot 改名（弃）**：其余模块同样是自启动的，只改一个反而制造命名不一致。

## Decision

1. **重命名范围**：5 个发布模块统一加 `-starter` 后缀；`spring-plus-calcite-memory` 不参与（冻结现状，待删除或移出仓库）。
2. **只改坐标层**：Java 包名（`io.github.oatelauser.springplus.*`）、自动装配机制、模块依赖结构（ADR 0003 结论）全部不变；不拆 `autoconfigure` / `starter` 双模块，不新增总聚合 starter。
3. **不兼容直接替换**：1.1.0 起旧坐标自然冻结于 Central，不发布 relocation / 弃用桥 POM（无存量用户，迁移成本为零，CHANGELOG 记录坐标映射即可）。

## Consequences

- 消费方升级 1.1.0 需全量替换 5 个坐标；仓库内 example 的依赖坐标已同步。
- `skills/` 下按模块命名的技能目录随新名（`skills/spring-plus-boot-starter/` 等），`skills/spring-plus-framework/`（聚合技能）与 `skills/spring-plus-calcite-memory/` 目录名不变。
- Maven Central 徽章 / central.sonatype.com 页面按新坐标重新计数，旧坐标页面保留为 1.0.x 快照。
- 历史 ADR（0001–0003）与安全审计报告按落笔时的坐标保留，不改写；本 ADR 提供新旧映射。
