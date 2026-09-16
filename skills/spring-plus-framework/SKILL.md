---
name: spring-plus-framework
description: 在已引入 spring-plus-framework（io.github.oatelauser:spring-plus-*）的 Spring Boot 项目中进行任何业务代码新增、修改、重构或问题修复时优先使用。这类项目的开发会直接或间接依赖本框架的统一响应（SimpleResponse）、状态码体系（ServerStatus）、全局异常处理、流式响应、分页、幂等防重、声明式鉴权与内存 SQL 查询能力，本 skill 定义使用这些能力的强制约定与检查清单。
---

# spring-plus-framework

这个 skill 直接给 AI 使用。

## 强制伴随 Skill

处理任何涉及本框架的任务时，按涉及模块同时加载对应子 skill（位于本仓库 `skills/` 目录）：

- `spring-plus-web` —— 统一响应 / 异常 / 流式 / 校验 / 断言（几乎所有任务都要加载）
- `spring-plus-boot` —— HTTP 客户端 / Redis 工具 / 配置加密 / 优雅停机
- `spring-plus-governor` —— 幂等 / 防重复提交
- `spring-plus-security` —— 注解式鉴权
- `spring-plus-calcite-memory` —— 内存 SQL 查询

不确定涉及哪个模块时，全部加载 web 子 skill，其余按 import 出现与否判断。

## 不可跳过的工作流

1. 先读项目 README 与本 skill，确认框架模块引入情况（查 pom 中 `io.github.oatelauser:spring-plus-*`）
2. 新增代码前先搜索既有实现：响应封装、异常映射、校验注解、断言工具在本框架中已有大量存量，优先复用
3. 修改本框架源码（而非业务代码）时：先回答"原设计为什么这样写"（javadoc 与 ADR 里有答案），再评估影响面（低/中/高）；高影响面需人工确认

## 强制停止条件

出现以下任一情况，停止并请求人工决策：

- 找不到某能力的归属模块（例如不确定 Redis 工具在 web 还是 boot）
- 需要修改 `SimpleResponse` / `Page` 的字段名或 JSON 结构（线上契约，见 ADR）
- 需要向框架回加业务私有状态码（正确做法：业务项目实现 `ServerStatus` 接口）
- 需要删除或绕过 `META-INF/spring/*.imports` 中注册的自动配置
- hibernate-validator 大版本升级（`ClassValidatorPostProcessor` 反射依赖其内部 API）

## 业务错误定义规则

- 可预见的业务失败必须抛类型化异常（`ServiceException` 或业务异常类），禁止落入全局处理器兜底成"系统异常"
- 错误消息格式：**业务对象 + 动作/状态 + 原因**（如 `活动已发布不可删除`）
- 错误消息脱敏：不含请求值、ID、堆栈、SQL、内部类名；诊断信息只进服务端日志
- 业务异常映射优先用注解（`@JsonExceptionResponse` 族）声明在异常类上，其次独立 `ExceptionMapper` Bean，最后才是 Controller 方法注解
- 条件判断 + 抛异常的样板一律用 `AssertUtils` 收敛

## 变更设计规则

- 最小修改：只动与任务直接相关的代码
- 不要在业务项目里另建平行的 `Result` / `ApiResponse` / `PageResult` 封装——统一封装只此一套
- 不要绕过 `JsonUtils` 直接 `new JsonMapper()` / `new ObjectMapper()`
- 不要为假设的未来复用增加抽象层

## 配置键约定

- 官方有对应的键用官方命名空间：`spring.jackson.*`（其中 `datetime-format` / `time-format` / `long-to-string` 为框架扩展键，文档标注 ⚑）
- 框架自有键一律 `spring-plus.*` 前缀，禁止再造 `spring.web.*` 这类伪官方前缀
- 新增配置键必须同步更新模块 README 的配置表

## 验证闸门

- 任何修改后跑最小验证：`mvn -pl <模块> compile`；涉及测试的改动跑对应测试类
- 交付前跑 `mvn clean install`（全仓 192 个测试，约 20 秒）

## 交付检查清单

交付报告逐项确认：

- [ ] 使用了框架既有能力而非重复造轮子（响应/异常/分页/断言/幂等）
- [ ] 新增异常都有明确状态码且消息脱敏
- [ ] 没有引入平行封装 / 没有绕过 JsonUtils
- [ ] 配置键使用了正确命名空间
- [ ] 编译与相关测试通过
- [ ] 涉及模块 README / skill 的行为变更已同步文档
