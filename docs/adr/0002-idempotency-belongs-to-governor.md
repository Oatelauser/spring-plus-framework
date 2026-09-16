# 幂等能力归属 governor（服务治理域），不留在 web

`@Idempotent` / `@RepeatSubmit` 与 idempotent 实现（拦截器、键策略、双存储）从旧项目 `spring-web-framework` 迁出，归入 `spring-plus-governor`——尽管其织入依赖 MVC/AOP 基础设施（因此 governor 依赖 web 模块）。

## Considered Options

- **归 governor（已选）**：幂等/防重与限流/熔断同属服务治理关注点（旧项目 governor 空壳 pom 本就预留 resilience4j 坐标，此决策是该规划的落地）；`IdempotentInterceptor` 本质是 AOP 治理手段而非 Web 协议能力，留在 web 会稀释 web 的"协议层"定位。
- **留 web（弃）**：省去一个模块与一条依赖边，但 web 会同时承载协议与治理两种语义，未来限流熔断将无处安放或被迫也塞进 web。

## Consequences

- governor → web 单向依赖；AOP Advisor 装配从原 web 自动配置迁至 `SpringPlusGovernorAutoConfiguration`
- 未来的限流/熔断能力直接落 governor，不再迁移既有代码
- `StartupProcess` SPI 因被 web 的校验器与 boot 的生命周期共同实现，下沉到 `web.lifecycle` 包（依赖方向：boot → web）
