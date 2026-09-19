---
name: spring-plus-framework
description: spring-plus-framework 全家族使用约定的总入口（groupId io.github.oatelauser，模块 spring-plus-web-starter / boot-starter / redis-starter / governor-starter / security-starter / calcite-memory）。在基于本框架的 Spring Boot 项目写业务代码、做重构或排障时使用：SimpleResponse 统一响应、全局异常、流式输出、幂等防重、声明式鉴权、Redis 工具、HTTP 客户端等能力的强制约定、检查清单与强制停止条件；也覆盖修改框架源码本身的工作流（先读 ADR 与 javadoc 理解原设计）。不确定涉及哪个子模块时本 skill 负责指路。
---

# spring-plus-framework

这个 skill 直接给 AI 使用，是家族六个模块 skill 的总入口。

## 子模块路由（按任务加载对应 skill）

每个模块 skill 均为"SKILL.md 导航 + references/ 分域参考"结构：先读其 SKILL.md 的导航表，再按需加载具体 references 文件。

| 任务信号 | 加载 skill |
|---|---|
| Controller / 响应体 / 错误码 / 异常映射 / 校验 / SSE / NDJSON / 分页（**几乎所有 web 任务**） | `spring-plus-web-starter` |
| @RequiresRole / @RequiresPermission / @Principal / 401 403 / SecurityFilterChain | `spring-plus-security-starter` |
| ApiClient / ENC() 配置加密 / 优雅停机 / 通用工具 / SSRF | `spring-plus-boot-starter` |
| @Idempotent / @RepeatSubmit / 幂等存储 | `spring-plus-governor-starter` |
| RedisStringOperation / jacksonRedisTemplate / 批量护栏 | `spring-plus-redis-starter` |
| 内存 SQL（Calcite，模块已冻结待移除） | `spring-plus-calcite-memory` |

不确定时先加载 web 子 skill，其余按 import 出现与否判断。

## 不可跳过的工作流

1. 先确认框架模块引入情况（查 pom 中 `io.github.oatelauser:spring-plus-*`）
2. 新增代码前先搜索既有实现：响应封装、异常映射、校验注解、断言工具在本框架中已有大量存量，优先复用
3. 修改本框架源码（而非业务代码）时：先回答"原设计为什么这样写"（javadoc 与 docs/adr/ 里有答案），再评估影响面（低/中/高）；高影响面需人工确认

## 强制停止条件

出现以下任一情况，停止并请求人工决策：

- 找不到某能力的归属模块（如不确定 Redis 工具在 redis 还是 boot）
- 需要修改 `SimpleResponse` / `Page` 的字段名或 JSON 结构（线上契约；注意 `Page` 总数字段键是 `total`）
- 需要向框架回加业务私有状态码（正确做法：业务项目实现 `ServerStatus` 接口）
- 需要删除或绕过 `META-INF/spring/*.imports` 中注册的自动配置
- hibernate-validator 大版本升级（`ClassValidatorPostProcessor` 反射依赖其内部 API）
- 需要改 `SmartGracefulShutdownHandler` 的停机顺序语义（ Ordered 升序是现状契约）

## 业务错误定义规则

- 可预见的业务失败必须抛类型化异常（`ServiceException`——直接实现 `ServerStatus`，抛出即渲染，无须注解/Mapper；或业务异常类实现 `ServerStatus`），禁止落入全局兜底成"系统异常"
- 错误消息格式：**业务对象 + 动作/状态 + 原因**（如 `活动已发布不可删除`）
- 错误消息脱敏：不含请求值、ID、堆栈、SQL、内部类名；诊断信息只进服务端日志
- 业务异常映射优先级：异常类上贴注解（`@JsonExceptionResponse` 族）> 独立 `ExceptionMapper` Bean > Controller 方法注解
- 条件判断 + 抛异常的样板一律用 `AssertUtils` 收敛

## 变更设计规则

- 最小修改：只动与任务直接相关的代码
- 不在业务项目里另建平行的 `Result` / `ApiResponse` / `PageResult` 封装——统一封装只此一套
- 不绕过 `JsonUtils` 直接 `new JsonMapper()` / `new ObjectMapper()`
- 不为假设的未来复用增加抽象层

## 配置键约定

- 官方有对应的键用官方命名空间：`spring.jackson.*`（其中 `datetime-format` / `time-format` / `long-to-string` 为框架扩展键 ⚑，Boot 官方文档查不到）
- 框架自有键一律 `spring-plus.*` 前缀，禁止再造 `spring.web.*` 这类伪官方前缀
- 新增配置键必须同步更新模块 README 的配置表

## 文档同步约定

模块 README 与模块 skill（SKILL.md + references/）是同一事实的两份呈现：**行为变更必须两边同步改**；skill 中的 API 断言以模块源码为唯一基准，发现不一致以源码为准并修正文档。

## 验证闸门

- 任何修改后跑最小验证：`mvn -pl <模块> compile`；涉及测试的改动跑对应测试类
- 交付前跑 `mvn clean install`（全仓 281 个测试，约 20 秒）

## 交付检查清单

交付报告逐项确认：

- [ ] 使用了框架既有能力而非重复造轮子（响应/异常/分页/断言/幂等）
- [ ] 新增异常都有明确状态码且消息脱敏
- [ ] 没有引入平行封装 / 没有绕过 JsonUtils
- [ ] 配置键使用了正确命名空间
- [ ] 编译与相关测试通过
- [ ] 涉及模块 README / skill 的行为变更已同步两边文档
