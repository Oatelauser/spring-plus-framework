# 按能力域拆分模块，而非「核心库 + starter 装配层」模式

spring-plus-framework 的模块边界按**能力域**划分：`spring-plus-web`（Spring Web 层拓展）与 `spring-plus-boot`（Spring Boot 生态拓展）各自独立成模块、各自持有自己的自动配置注册；而非业界常见的「核心库 + xxx-spring-boot-starter 装配胶水层」模式。

## Considered Options

- **能力域拆分（已选）**：web / boot / governor / security / calcite-memory 各自自带 AutoConfiguration + `AutoConfiguration.imports`。每个模块的语义是"一类能力"，Boot 变体不是任何模块的附属品。
- **核心库 + starter 模式（弃）**：`spring-plus-web`（纯核心）+ `spring-plus-web-boot`（仅装配）。被弃原因：`spring-plus-boot` 这个名字在该模式下名不副实（它只装配 web 却暗示全家族装配层）；且 security / calcite 本来就是单模块自带装配的模式，统一为能力域拆分全项目一致。

## Consequences

- 只需要配置加密/优雅停机而不需要 web 异常体系的用户，仍会传递引入 `spring-plus-web`（boot → web 单向依赖）——通过"所有自动装配均带条件注解、可选能力 provided 化"缓解
- 未来若拆分 security，模式可复制：`spring-plus-security` 内部消化，不产生跨模块装配层
- 与 Spring 官方 starter 命名体系有意区隔：本项目不做 `xxx-spring-boot-starter` 后缀
