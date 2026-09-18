# spring-plus-framework

面向 Maven 中央仓库发布的 Java 公共库：为 Spring Web 与 Spring Boot 应用提供统一响应、全局异常处理、服务治理、声明式鉴权与内存 SQL 查询能力。

## Language

### 模块（能力域）

**spring-plus-web-starter**:
Spring Web（非 Boot）能力拓展域：统一响应、全局异常体系、流式写入器、校验注解、请求追踪、公共工具。
_Avoid_: web-core、spring-web-framework

**spring-plus-boot-starter**:
Spring Boot 生态能力拓展域：HTTP 客户端、Redis 工具、配置加密、优雅停机，及本域自动装配。
_Avoid_: spring-plus-web-boot（本项目不做「核心库 + starter 装配层」模式，按能力域拆分）

**spring-plus-governor-starter**:
服务治理域：幂等、防重复提交；限流、熔断等治理能力未来也归此域。
_Avoid_: 把幂等视为 web 域能力

**spring-plus-security-starter**:
声明式鉴权域：以注解表达角色/权限要求的鉴权模型。

**spring-plus-calcite-memory**:
内存 SQL 查询域：把内存对象注册为表并用 SQL 查询，独立于其他域。

### 响应体系

**SimpleResponse**:
统一响应对象，字段 code / message / data / details / success。全项目唯一响应封装，业务项目不得自建平行封装。
_Avoid_: ServerResponse、Result、ApiResponse、PageResult

**ok() / fail()**:
响应工厂动词约定：ok 家族构造成功响应，fail 家族构造失败响应。
_Avoid_: success() 作为工厂方法名

**00000**:
唯一成功码。成功只有一档，不存在第二成功码（如 CREATED）。

**状态码段**:
错误码分段规范：A0xxx 客户端错误、B0xxx 业务错误、C0xxx 系统错误。
_Avoid_: HTTP 状态码数字充当业务码

**details**:
失败响应中专有的结构化补充信息（字段错误清单、限流配额等）；成功响应永远为 null。data 与 details 互斥。

**Page**:
分页数据载体，字段 item（当前页记录列表，单数命名是项目约定）/ total（总记录数）/ pageNum（当前页码）/ pageSize（每页条数）/ totalPage（总页数）。
_Avoid_: list、records、total、totalItem、startPage、pages

**PageResponse**:
分页响应，继承 SimpleResponse<Page<T>>；BasePageRequest 是其标准入参（pageNum / pageSize）。

**FieldErrorInfo**:
details.violations 中的字段错误条目（field + msg），JSON 形态与裸 Map 保持一致。
