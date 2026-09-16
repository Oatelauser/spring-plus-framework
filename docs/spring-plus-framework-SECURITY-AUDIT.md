# spring-plus-framework 上线前安全审计报告

> 审计对象：`https://github.com/Oatelauser/spring-plus-framework`（commit `1d6f449`，2026-09-16）
> 审计方法：白盒源码审计，**按仓库 `skills/` 下全部 6 个 skill 逐域覆盖**（framework 主 skill＋ web / boot / governor / security / calcite-memory 五个子 skill），外加供应链依赖、CI/发布链路、示例工程三个横切面。共审阅 main 源码 226 个 Java 文件、全部 POM、Lua 脚本、workflow 与文档。
> 结论先行：**暂缓发布，先修 P0（8 项）**。框架整体工程质量不错，加解密、Jackson、幂等存储等核心实现是干净的，但存在“生产默认不安全”（异常原文透出）、SSE 响应拆分、SQL 参数绑定 API 缺失等必须在发布前处理的问题。P0 修完并补齐文档红线后可发布；P1 在发布后 1–2 个迭代内跟进。

---

## 一、风险总览

| 级别 | 数量 | 说明 |
|---|---|---|
| 🔴 高 | 4 | 生产默认泄露异常原文、SSE 响应拆分、SQL 拼接注入面、异常回显用户输入 |
| 🟠 中 | 13 | SSRF 面、敏感日志、ENC+Actuator、TLS 误用面、Redis/内存 DoS、鉴权 fail-open 倾向、追踪过滤器 OOM 等 |
| 🟡 低 | 10 | SpEL 上下文收紧、CI pin、gitignore、CLI 习惯等纵深加固 |
| ✅ 已确认安全 | 10 | AES-GCM、Jackson 无多态、ReDoS、Lua 原子性、依赖版本等（见第八节） |

按 skill 域分布：web（9）/ boot（8）/ governor（4）/ security（5）/ calcite-memory（3）/ framework 主 skill·契约文档（3）/ 横切面·供应链与 CI（4）。

---

## 二、P0：拦发布项（必须修）

### V01 🔴 异常原文默认透出给前端 — `showError=true` 默认值生产不安全

- **skill 域**：`spring-plus-web`（全局异常体系）｜CWE-209
- **位置**：
  - `spring-plus-web/.../autoconfigure/GlobalExceptionProperties.java`：`private boolean showError = true;`
  - `spring-plus-web/.../error/mapper/DefaultExceptionMapper.java`：`showError=true` 时 `message = ex.getLocalizedMessage()` 直接透出
- **证据**：
  ```java
  // DefaultExceptionMapper.map()
  if (properties.getShowError()) {
      code = INTERNAL_ERROR.getCode();
      message = StringUtils.hasText(ex.getLocalizedMessage()) ?
              ex.getLocalizedMessage() : ex.getClass().getSimpleName();
  }
  ```
- **危害**：任何未被显式映射的异常（SQL 异常、IO 路径、类名、约束名……）原文直达客户端。攻击者可据此做技术侦察（表名、列名、内部路径、中间件版本），且与 skill 自身“错误消息脱敏：不含请求值、ID、堆栈、SQL、类名”的强制约定**自相矛盾**。框架默认值会被 99% 的消费方原样带上现网。
- **修复**（二选一，推荐 A）：
  - A. 默认值改为 `false`，并在 `application.yml` 示例与 README 中写明“本地排障可开，生产必须关”；
  - B. 保留默认 `true` 但在启动期检测 `spring.profiles.active` 含 `prod` 时 warn＋强制关闭（侵入性大，不推荐）。
  ```java
  /** 是否显示详细错误信息（生产环境建议关闭） */
  private boolean showError = false; // 改前：true
  ```

### V02 🔴 SSE `event` / `id` 未校验换行符 — SSE 响应拆分 / 事件伪造

- **skill 域**：`spring-plus-web`（流式写入器＋SSE 异常渲染）｜CWE-113
- **位置**：
  - `spring-plus-web/.../stream/SseStreamWriter.java`：`writeEvent(...)` 系列对 `event`/`id` 直接 `getBytes` 写出，不做 `\r\n` 校验；只有 `data` 做了拆行处理（`writeSseField`）。
  - 同族：`spring-plus-web/.../error/output/SseExceptionProcessor.java`：`frame.append("event: ").append(hint.event())`，`hint.event()` 来自注解/配置/自定义 `bodyCustomizer`，同样未校验（`data` 有 `replace("\n","ndata: ")` 防御，`event` 没有）。
- **危害**：若业务把任何外部可影响的字符串（用户名、订单状态、事件类型枚举的外部映射值……）传入 `event`/`id`，攻击者注入 `\n` 即可伪造任意 SSE 事件帧（`event: app-error\ndata: {...伪造...}`），前端事件路由被劫持；等价于 HTTP 响应拆分在 SSE 协议上的变体。
- **修复**：统一加校验工具，所有 `event`/`id` 入口先验后写：
  ```java
  private static void requireSingleLine(String name, String value) {
      if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
          throw new IllegalArgumentException(name + " 不能包含换行符（SSE 响应拆分防护）: " + value);
      }
  }
  ```
  分别加在 `SseStreamWriter.writeEvent*`（event/id）与 `SseExceptionProcessor.resolveHint`（event）。

### V03 🔴 内存 SQL 无参数绑定 API — skill 承诺的 `query(sql, params)` 不存在，逼用户拼接 SQL

- **skill 域**：`spring-plus-calcite-memory`（内存 SQL 查询）｜CWE-89
- **位置**：`spring-plus-calcite-memory/.../engine/MemoryQuerySession.java` 全类仅有 `public QueryResult query(String sql)`（grep 全模块确认无任何重载），但：
  - `skills/spring-plus-calcite-memory/SKILL.md` 示例写的是 `session.query("SELECT id FROM users WHERE room = ?", "A-101")`；
  - skill“不要这样做”要求“一律参数绑定”。
- **危害**：文档与代码不一致。用户按文档写编译失败，只能退回字符串拼接。一旦 SQL 含外部输入（规则过滤、报表筛选正是本模块主场景），即构成 SQL 注入：虽是内存库、无 RCE，但可跨内存表 `UNION` 窃读其他表数据（多租户/多业务同引擎场景即越权）。`RESTRICTED` 白名单只能拦 DML/DDL，拦不住 SELECT 内的注入。
- **修复**（必须二选一，不能只改文档）：
  - A（推荐）. 补齐 API：`query(String sql, Object... params)` / `query(String sql, List<Object> params)`，内部走 `PreparedStatement` 绑参（`executeWithParameters` 已有雏形，可复用），并补单测；
  - B. 若坚持单参 API，删除 skill 与 README 中的绑参示例，改为强制要求调用方侧转义＋`RESTRICTED`，并明确声明“SQL 不得含外部输入”（会大幅收窄模块适用面，不推荐）。

### V04 🔴 类型不匹配异常回显用户输入原值

- **skill 域**：`spring-plus-web`（全局异常体系）｜CWE-209 / CWE-117
- **位置**：`spring-plus-web/.../autoconfigure/GlobalExceptionAdvice.java`
  ```java
  String message = String.format("参数类型错误: %s (期望类型: %s, 实际值: %s)",
          ex.getPropertyName(), ..., ex.getValue());
  ```
- **危害**：`ex.getValue()` 是攻击者完全可控的输入原样反射进响应 `message`（存储型/反射型信息回显，XSS 需看前端是否转义，但框架侧已违约），同时该 message 会进 `DefaultExceptionLogger` 的日志行（见 V27，CRLF 可伪造日志）。同样违反 skill 脱敏约定。
- **修复**：不再回显原值，或截断＋去换行：
  ```java
  // 推荐：只保留参数名与期望类型
  String message = String.format("参数类型错误: %s (期望类型: %s)",
          ex.getPropertyName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "未知");
  ```

### V05 🟠 幂等匿名主体易碰撞＋内存存储无上限 — 误杀与 OOM DoS

- **skill 域**：`spring-plus-governor`（幂等/防重）｜CWE-400 / CWE-770
- **位置**：
  - `governor/.../idempotent/FingerprintKeyStrategy.java`：`subject()` 降级链 `Principal → SessionId → RemoteAddr → anonymous`；
  - `governor/.../idempotent/InMemoryIdempotentStore.java`：`ConcurrentHashMap` 无容量上限，仅每 4096 次操作清过期键；`operations` 非原子 `long` 自增（多线程下清理节拍漂移）。
- **危害**：
  1. 出口 IP/网关后大量用户同 `RemoteAddr`（甚至同 `anonymous`），一人触发 `@RepeatSubmit` 窗口可误杀同 IP 他人；攻击者可主动占住可预测 key 拒绝他人请求（key 含方法签名＋参数指纹，可预测）。
  2. 攻击者刷不同参数生成海量不同指纹 key，内存存储无界增长 → OOM（Redis 模式则打爆 Redis 内存，取决于 TTL 与配额）。
- **修复**：
  - 内存存储加容量上限（如 10 万条）＋超限拒绝/提前逐出最旧，并把 `operations` 改为 `AtomicLong`/`LongAdder`；
  - skill 与注解 javadoc 加红线：匿名/IP 主体只适合单机低风险场景，多租户必须自定义 `IdempotentKeyStrategy` 加入租户维度；集群必须 Redis。

### V06 🟠 `FileResources.getResource` 路径穿越

- **skill 域**：`spring-plus-web`（公共工具）｜CWE-22
- **位置**：`spring-plus-web/.../utils/FileResources.java`
  ```java
  File file = new File(HOME.getDir(), filename);   // filename 含 ../ 即穿越
  if (file.exists()) { return new FileSystemResource(file); }
  return new ClassPathResource(filename);          // classpath 侧同样可用 ../
  ```
- **危害**：若任一消费方把外部输入传给 `filename`（配置中心 key、模板名、导出文件名……），即任意文件读取。工具类本身无校验，把安全责任完全后移。
- **修复**：规范化＋白名单式拒绝：
  ```java
  public static Resource getResource(String filename) {
      Assert.hasText(filename, "filename 不能为空");
      String normalized = Paths.get(filename).normalize().toString().replace('\\', '/');
      if (normalized.contains("..") || normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
          throw new IllegalArgumentException("非法文件名（路径穿越防护）: " + filename);
      }
      // ... 原逻辑，但用 normalized
  }
  ```

### V07 🟠 鉴权 fail-open 倾向：空权限放行＋注解配错运行期才爆 500

- **skill 域**：`spring-plus-security`（声明式鉴权）｜CWE-862
- **位置**：
  - `security/.../authorization/GrantedAuthorityAuthorizer.java`：`requireAuthorities` 为空 → `ALLOW`；
  - `security/.../authorization/RequiresPermissionAuthorizer.java`：`source`/`action` 为空 → `Assert.hasText` 抛 `IllegalArgumentException`（运行期 500，而非启动期失败）；
  - `security/.../authorization/RequiresRoleAuthorizer.java`：`role={}` → `Set.of()` → 上游判空 → `ALLOW`；
  - `security/.../authorization/CompositeAuthorizationManager.java`：循环结束无命中 → `ALLOW`（当前与 pointcut 语义一致故低危，但无纵深）。
- **危害**：`@RequiresRole(role = {})`、`permission = {}` 等“配了等于没配”的写法会被**放行所有已认证用户**；`@RequiresPermission` 留空要到第一次被调用才 500，生产发布前测不到即带病上线。授权体系的默认方向应该是 fail-closed。
- **修复**：
  - 空 `requireAuthorities` 改为 `DENY`（或至少 warn＋DENY）；
  - 增加启动期校验器（`SmartLifecycle`/`BeanPostProcessor` 扫描所有 `@RequiresPermission` 方法，空 source/action 直接启动失败，与 skill“运行期断言直接失败”收紧为“启动期失败”）；
  - `CompositeAuthorizationManager`：pointcut 命中但零 authorizer 命中注解时返回 DENY＋error 日志（防御 pointcut 与注解解析未来漂移）。

### V08 🟠 security 模块“不管认证、无默认拒绝”——消费方极易裸奔上线

- **skill 域**：`spring-plus-security`＋`spring-plus-framework` 主 skill（文档契约）
- **位置**：模块设计（skill 已写“认证由业务 Security Filter 链提供”）＋ `examples` 无任何 `SecurityFilterChain` 示例。
- **危害**：这是框架型项目最高发的“文档漏洞”：消费方引入 `spring-plus-security`、贴几个注解即以为安全，实际若未自配 `SecurityFilterChain`（默认放行链/弱默认），所有接口裸奔。审计确认 2026-04 的 Spring Boot 默认安全链 CVE（CVE-2026-40976，4.0.0–4.0.5，4.0.6/4.1.x 已修）正是“以为有默认保护实际没有”一类问题——本框架必须明确立场。
- **修复（文档即安全修复）**：
  - `spring-plus-security/README.md`＋skill 顶部加红线：**必须自配 `SecurityFilterChain` 且默认 `denyAll`**，并给出最小可用配置（含 `AnonymousRequestMatcher` 接入 `@RequiresNonLogin`）；
  - examples 增加 `SecurityExampleConfig`（默认 deny＋白名单放行 `/v2-test/**`），让“对照样例”本身就是安全基线。

---

## 三、P1：发布后 1–2 个迭代跟进

### V09 🟠 ApiClient SSRF 面：自由 uri＋默认跟随重定向＋无内网拦截

- **skill 域**：`spring-plus-boot`（HTTP 客户端）｜CWE-918
- **位置**：`boot/.../client/BaseApiClient.java`（`buildUri` 直接拼 `request.getUri()`，绝对 URL 可覆盖 baseUrl）、`boot/.../client/ApiClientSettings.java`（`followRedirect=true` 默认）、`ClientHttpRequestFactoryProvider`（重定向/代理实现）。
- **危害**：调用方一旦把外部输入拼进 `uri`/`queryParam`（回调地址、Webhook、图片抓取……），即 SSRF：打内网元数据服务、内网管理口；302 跳转可把请求带到内网，且 `Authorization` 头默认跟随重定向存在跨域泄露风险（取决于引擎实现）。
- **现状好的地方**：超时默认 10s/30s、`RetryFilter` 默认仅重试 GET/HEAD。**修复**：不拦发布，但必须做——① skill＋`ApiRequest` javadoc 加 SSRF 红线（uri 禁止拼外部输入，外部 URL 走 allowlist 校验）；② 新增可选配置 `spring-plus.client.ssrf.{enabled,allowed-hosts,deny-private-network}`，默认关闭以保兼容，文档推荐开启；③ 跟随重定向时默认剥离跨域 `Authorization`/`Cookie`（或文档声明行为）。

### V10 🟠 请求/响应 body 日志无敏感字段脱敏

- **skill 域**：`spring-plus-boot`＋`spring-plus-web`（日志拦截器＋请求追踪）｜CWE-532
- **位置**：
  - `boot/.../client/interceptor/LoggingInterceptor.java`：`BODY` 级别记录完整请求/响应体，仅脱敏了 6 个敏感**头**，body 内 `password/token/phone/idCard` 原样落盘；
  - `web/.../trace/LoggingHttpTraceFilter.java`＋`AbstractHttpTraceFilter.java`：`@RecordHttp` 接口的请求/响应体 debug 全量记录，同样无掩码；且示例 `application.yml` 把 `io.github.oatelauser.springplus` 开到 `debug`。
- **修复**：① 增加 `sensitive-field-names`（默认含 password/passwd/token/secret/phone/idCard 等）＋ JSON body 掩码（替换为 `***`），超长截断保留；② 示例 yml 日志级别改回 `info` 并注释说明生产勿开 debug；③ skill 加红线：`BODY`/追踪禁用于含敏感字段接口，除非已配掩码。

### V11 🟠 ENC 解密明文常驻 Environment，`/actuator/env` 可直接看到

- **skill 域**：`spring-plus-boot`（配置加密）｜CWE-214
- **位置**：`boot/.../crypto/EncryptedPropertyEnvironmentPostProcessor.java`（解密值以最高优先级 `MapPropertySource` 注入 Environment）。
- **危害**：功能本身正确（AES-256-GCM 实现干净，见第八节），但副作用是**任何能访问 `/actuator/env`、`/actuator/configprops`、heapdump 的人都能看到明文**。结合 V08 的裸奔风险，危害放大。
- **修复**：① README＋skill 强制要求：暴露 actuator 时必须鉴权＋`management.endpoint.env.show-values=NEVER`（或 sanitize 自定义脱敏）；② 考虑对解密 PropertySource 的 `toString` 做脱敏呈现（不影响注入值）。

### V12 🟠 trust-all 能力公开面过大：`InsecureTlsHelper` 位于 web 公共包

- **skill 域**：`spring-plus-boot`＋`spring-plus-web`｜CWE-295
- **位置**：`web/.../utils/InsecureTlsHelper.java`（`public` trust-all Context/Verifier，被所有模块传递依赖）；消费路径 `ClientHttpRequestFactoryProvider` 有双开关（`verify=false` 必须配 `allow-insecure=true`，否则启动失败）＋ warn 日志——**这部分设计是好的**。
- **危害**：双开关只卡得住走 `ApiClient` 的路径；任何消费方（或其传递依赖）可直接调 `InsecureTlsHelper.trustAllContext()` 关闭校验，无任何确认门槛。生产误用即中间人。
- **修复**：① 类/javadoc 标 `@Deprecated`＋“仅测试联调，生产使用视同漏洞”；② 考虑移到 boot `client.internal` 包并降为包可见（大版本可做）；③ skill 现有“不要开启 allow-insecure 用于生产”保留，并追加“禁止直接引用 InsecureTlsHelper”。

### V13 🟠 Redis 通配批量接口可被打成全库遍历/全库删除

- **skill 域**：`spring-plus-boot`（Redis 工具）｜CWE-400
- **位置**：`boot/.../redis/RedisStringOperation.java`（`batchGet/batchDelete(pattern)`）＋`resources/lua/bget.lua`、`bdel.lua`（`SCAN MATCH ARGV[1]`，COUNT 100 循环，`bget` 把全部匹配值一次性装 List 返回）。
- **危害**：`key` 一旦可达外部输入（或误传 `*`），即全库 SCAN（CPU 上升）＋`bget` 全量值进堆内存（OOM）＋`bdel` 全库删除（数据事故）。Lua 本身无注入（pattern 走 ARGV，注释已说明 Cluster CROSSSLOT 考量——好的），问题在**调用面无护栏**。
- **修复**：① 拒绝纯通配 pattern（如 `*`、`*:*`）或要求显式二次确认参数；② `bget` 加上限（COUNT 可配＋最多返回 N 条，超限抛异常）；③ skill＋javadoc 红线：pattern 禁止拼外部输入。

### V14 🟠 超管短路只看 authorities 第一个元素且限定 List

- **skill 域**：`spring-plus-security`｜功能正确性（fail-closed，非可利用漏洞，但权限模型不可靠必须修）
- **位置**：`security/.../authorization/Authorizer.java#determineAdminRole`
  ```java
  grantedAuthorities instanceof List<GrantedAuthority> authorities
      && ROLE_SUPER_ADMIN.equals(authorities.getFirst().getAuthority())
  ```
- **危害**：Spring Security 常规 `User` 的 authorities 是 `Set`（`Collections.unmodifiableSet`），`instanceof List` 恒为 false → 超管短路**实际永不触发**（fail-closed：超管走正常校验，不会越权，但与文档语义相悖）；若某天换成 List 且超管不在首位，同样失效。顺序敏感的权限判断是定时炸弹。
- **修复**：改为 `stream().anyMatch(a -> ROLE_SUPER_ADMIN.equals(a.getAuthority()))`，并补单测（Set/List/乱序三态）。

### V15 🟡 SpEL 求值用 `StandardEvaluationContext` 且每请求 new parser

- **skill 域**：`spring-plus-governor`｜CWE-94（当前不可利用＋性能）
- **位置**：`governor/.../idempotent/RepeatSubmitInterceptor.java`、`IdempotentInterceptor.java`（`new StandardEvaluationContext()`＋`new SpelExpressionParser().parseExpression(spel)`，spel 来源＝注解属性＝开发者可信输入）。
- **结论**：当前无 RCE（攻击者碰不到表达式），**不拦发布**。但 `StandardEvaluationContext` 支持 `T()`/`new`/反射，未来若 spel 来源扩展（如配置中心、请求参数）即 RCE；且每请求解析无缓存是性能浪费。
- **修复**：换 `SimpleEvaluationContext`（仅属性/方法，白名单语义）＋按 `Method→Expression` 缓存解析结果（注意 `express`/`spel` 两注解属性名不统一：`@Idempotent` 用 `express()`、`@RepeatSubmit` 用 `spel()`，建议统一为 `spel` 并保留兼容）。

### V16 🟠 请求追踪过滤器响应缓存无上限，大响应接口误标即 OOM

- **skill 域**：`spring-plus-web`（请求追踪）｜CWE-400
- **位置**：`web/.../trace/AbstractHttpTraceFilter.java`：请求侧 `ContentCachingRequestWrapper(request, maxPayloadSize)` 有上限（512KB，好），响应侧 `new ContentCachingResponseWrapper(response)` **无上限**，全量缓存在内存再 `copyBodyToResponse`。
- **危害**：文件下载/大导出接口若误标 `@RecordHttp`，响应全进堆内存 → OOM；且大 body 全量进日志（与 V10 叠加）。
- **修复**：响应侧同样限大小（超限记 `[payload too large]` 且仍透传）；默认跳过 `multipart/*` 与 `Content-Disposition: attachment`；skill 加红线：追踪禁标下载/导出/大文件接口。

### V17 🟡 `writeRawLine` 不校验换行，破坏 NDJSON 逐行协议

- **skill 域**：`spring-plus-web`（流式写入器）
- **位置**：`web/.../stream/NdjsonStreamWriter.java#writeRawLine`（javadoc 已声明“调用方保证单行”，但无校验；`writeLine(Object)` 经 JSON 转义是安全的）。
- **修复**：`writeRawLine` 内校验含 `\n/\r` 直接抛 `IllegalArgumentException`（fail-fast 比下游解析错位好一万倍）。

---

## 四、P2：纵深加固与工程卫生（建议项）

| 编号 | skill 域 | 内容 | 建议 |
|---|---|---|---|
| V18 | examples | `UsernameDuplicatedException` 把用户名拼进异常 message（`super(username)`＋模板 `{exception}`），**示例亲手违反自家脱敏规范**；另：示例开 debug 日志、`sseValidateHandshake` 每请求 `newVirtualThreadPerTaskExecutor()` 不关闭（资源泄漏） | 示例改用占位 ID/脱敏；日志改 info；executor 复用或 try-with-resources。示例＝消费方的复制源，必须最干净 |
| V19 | governor | `RepeatSubmitInterceptor` 业务异常时 `delete(key)` 释放窗口，攻击者故意触发业务异常可绕防重刷接口 | 增加 `releaseOnFailure` 属性（默认保持兼容），防刷场景设 false |
| V20 | governor | 匿名/IP 主体 key 可预测占用（见 V05 第 1 点） | 文档＋租户维度策略；敏感接口强制登录主体 |
| V21 | web | `DefaultExceptionLogger` 用 SLF4J 参数化（好）但不对 message 做 CRLF 转义，恶意 message 可伪造日志行 | 日志前 `message.replace('\n',' ').replace('\r',' ')`＋截断 |
| V22 | calcite | `RESTRICTED` 白名单只禁语句 kind，未禁 XML 函数名（`EXISTS_NODE/EXTRACT_XML/XML_TRANSFORM/EXTRACT_VALUE`） | Calcite ≥1.32 已默认禁 DTD/外部实体（本项目 1.42，见第八节），此为纵深：白名单追加函数名黑名单 |
| V23 | boot | `ConfigEncryptor` CLI 支持 `encrypt <key> <明文>`，密钥进 shell 历史/进程表 | 文档主推 env 模式，CLI 显式传 key 时打 stderr 警告 |
| V24 | web | `ClassValidatorPostProcessor` 反射 HV 内部 API（`beanMetaDataManagers`＋`setTargetValidator`），HV 升级即炸；非 `LocalValidatorFactoryBean` 时静默 `return` | 已锁定 HV 9.1.0（好）＋主 skill 已设停止条件（好）；补启动期版本断言＋静默分支打 warn 日志 |
| V25 | repo | `.gitignore` 缺 `*.asc/*.gpg/secring* /.env/`，GPG 导出文件易被 `git add -A` 误提交（docs 口头要求不足） | 补规则 |
| V26 | repo CI | `actions/checkout@v4`、`setup-java@v4` 未 pin commit SHA（tag 可被劫持）；`v*` tag 推送即发布，无 environment 审批 | pin SHA＋启用 GitHub Environment 保护（required reviewers） |
| V27 | web | `OutputProtocolResolver.acceptFallback` 用 `Accept` 头子串匹配选协议 | 仅影响错误渲染形态，不影响状态码/鉴权，极低；可保持现状，记录即可 |

---

## 五、分 skill 结论（一句话版，供对照 skill 检查清单）

- **spring-plus-framework（主 skill）**：契约本身（`SimpleResponse` 字段、`Page.item` 单数、配置键命名空间）源码与 skill 一致，无漂移；但 skill 的“脱敏/绑参”强制约定被自家 `V01/V03/V04/V18` 违反——先做到自洽。
- **spring-plus-web**：异常三协议与流式写入器设计成熟，问题集中在**输出侧净化**（V01/V02/V04/V16/V17/V21）与**校验器供应链反射**（V24）。
- **spring-plus-boot**：客户端/Redis/加密功能完整，问题集中在**调用面护栏与日志**（V09/V10/V11/V13）与 **TLS 误用面**（V12）。
- **spring-plus-governor**：拦截器＋双存储逻辑正确，原子语义干净；补**容量上限/主体维度/SpEL 收紧**（V05/V15/V19/V20）。
- **spring-plus-security**：无可利用的越权 bypass（已逐条验证：`@RequiresAdminRole` 经元注解可被 `findMergedMethodAnnotation` 正确归并；超管短路 fail-closed）；方向是**把 fail-open 倾向全部掰成 fail-closed**（V07/V08/V14）＋补启动期校验。
- **spring-plus-calcite-memory**：引擎/会话隔离/资源限额/超时看门狗/语句缓存设计扎实；**参数绑定 API 缺失是最大缺口**（V03），函数黑名单为纵深（V22）。

---

## 六、供应链与依赖结论

| 依赖 | 版本 | 结论 |
|---|---|---|
| Spring Boot BOM | 4.1.0 | ✅ 不在已知 CVE 影响范围。2026-04 披露的 Boot 4.0 系列 Critical（CVE-2026-40976 默认安全链绕过，影响 4.0.0–4.0.5，修于 4.0.6）与 2026-03 的 Actuator 鉴权绕过（CVE-2026-22731/22733，修于 4.0.3/4.0.4/3.5.11+）均已包含在 4.1.0 之前。**要求**：建立 Dependabot/Renovate，Boot 安全补丁 30 天内跟进 |
| Apache Calcite | 1.42.0 | ✅ XXE CVE-2022-39135（CVSS 9.8，影响 <1.32.0）已修，本项目远高于修复线 |
| mybatis-flex-core | 1.11.8（optional，不传递） | ✅ 无针对该组件的高危公开 CVE（公开的是 mybatis-plus 旧版租户插件注入，与本组件无关）；optional 依赖不传递，面小 |
| hibernate-validator | 9.1.0.Final（锁定） | ⚠️ 无已知高危，但框架反射其**内部** API，大版本升级＝破坏性变更；已锁定版本＋skill 停止条件，见 V24 |
| httpclient5 / Jackson / Tomcat | 随 Boot 4.1.0 BOM | ✅ 由 BOM 统一管理，未发现私定老版本 |
| swagger-annotations | 2.2.53（仅 examples） | ✅ 注解包，无运行时风险，且示例模块不发布 |
| GPG/发布插件 | central 0.8.0、gpg 3.2.7 | ✅ 版本 pin 明确；`excludeArtifacts` 按 artifactId 排除示例（注释与字节码实证一致） |

git 历史检查：11 个提交，无密钥/证书/`.env` 入库记录；Secrets 均经 GitHub Secrets 占位符注入，未见明文。

---

## 七、发布前 Checklist（建议逐项打勾）

- [ ] V01：`showError` 默认改为 `false`（或等效生产保护）
- [ ] V02：SSE `event`/`id` 换行校验（写入器＋异常渲染器两处）
- [ ] V03：补齐 `query(sql, params)` 绑参 API＋单测，或收敛文档并声明 SQL 不得含外部输入
- [ ] V04：`TypeMismatch` 不再回显 `ex.getValue()`
- [ ] V05：内存幂等存储容量上限＋原子计数；文档加入主体/IP 风险说明
- [ ] V06：`FileResources` 路径穿越校验
- [ ] V07：空权限 DENY＋`@RequiresPermission` 启动期校验＋manager 兜底 DENY
- [ ] V08：security README/skill 红线（必须自配 FilterChain＋默认 deny）＋examples 安全基线配置
- [ ] V10/V16：示例 `application.yml` 日志改 `info`；全仓跑 `mvn clean install`（192 测试）确认无回归
- [ ] V26：CI actions pin SHA（发布链路供应链）
- [ ] 跑一遍 `mvn -P release deploy` 干跑（`-DskipTests=false`），确认 bundle 校验通过后再打正式 tag

---

## 八、已确认安全的点（审计覆盖、可放心）

1. **AES-256-GCM 配置加密实现正确**：每次随机 12 字节 IV、128-bit tag 认证、密钥 32 字节 Base64 严格校验、失败 fail-fast 不静默降级（`ConfigCipher`＋`EncryptedPropertyEnvironmentPostProcessor`）。
2. **Jackson 无反序列化 RCE 面**：未启用 `enableDefaultTyping`/多态，幂等结果缓存反序列化目标为方法返回类型（无多态 gadget 链）；`FAIL_ON_UNKNOWN_PROPERTIES` 关闭仅影响容错。
3. **幂等/Redis Lua 原子语义正确**：`expire_increment.lua`（EXISTS＋INCR＋EXPIRE 同脚本原子）、`setIfAbsent` 用 Redis 原子语义；`bget/bdel` pattern 走 ARGV 规避 Cluster CROSSSLOT。
4. **文件下载头安全**：`Content-Disposition` 经 Spring `ContentDisposition` 构造（RFC 6266/5987），无手拼注入；`NDJSON` 数据行经 JSON 转义物理单行。
5. **手机号/URL 正则无 ReDoS**：`PhoneValidator` 四分支 alternation 无嵌套量词；`baseUrl` 尾斜杠正则线性。
6. **重试默认安全**：`RetryFilter` 默认仅 GET/HEAD，指数退避＋抖动，中断恢复标记。
7. **流式内部标记不外泄**：`StreamingHintInterceptor` 无条件末位清除内部头；`BufferedClientHttpResponse` 有界缓冲，下游 body 完整。
8. **`@RequiresAdminRole` 无越权 bypass**：经 `@RequiresRole` 元注解＋`findMergedAnnotation` 归并，实测语义链完整（建议补回归单测锁定）。
9. **Calcite XXE 已修**：1.42.0 ≥ 1.32.0 修复线；`CalciteDialectAdapter.quoteIdentifier` 双引号转义正确；分页参数 `long` 拼接无注入（类型约束）。
10. **发布链路基线良好**：tag 触发隔离、Secrets 打码、`excludeArtifacts` 正确排除示例、GPG 签名＋2 年有效期密钥。

---

## 九、修复优先级与工作量估计

| 批次 | 内容 | 估计 |
|---|---|---|
| P0（发布前，约 2–4 人天） | V01–V08：默认值、SSE 校验、绑参 API、回显、容量上限、路径穿越、鉴权 fail-closed、文档红线＋示例基线 | 必须 |
| P1（发布后 1–2 迭代） | V09–V17：SSRF 选项、日志掩码、actuator 文档、TLS 收紧、Redis 上限、超管修复、SpEL 收紧、追踪上限 | 强烈建议 |
| P2（持续） | V18–V27：示例净化、CI pin、gitignore、HV 断言、纵深项 | 建议 |

*报告生成方式：AI 白盒审计＋公开 CVE 情报交叉验证。建议 P0 修复后做一次目标复测（re-test），重点回归异常渲染、SSE、鉴权三条链路。*
