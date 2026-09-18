# 参数校验注解

包：`io.github.oatelauser.springplus.web.validation`（field / collection / clazz 三层）。

## 字段级（field）

```java
public class CreateUserCmd {

    @NotBlank                                       // 必填叠加：@Phone 空值默认通过！
    @Phone
    private String phone;

    @EnumValue(enumClass = UserStatus.class)        // 值必须是枚举成员；method 默认 "name"（可用 "getCode" 等）
    private String status;

    @ListValues({"male", "female"})                 // 字面量白名单
    private String gender;
}
```

- `@Phone` / `@EnumValue` / `@ListValues` 空值（null/空串）一律**默认通过**——它们只管"有值时值必须合法"；必填约束叠加 `@NotBlank` / `@NotNull`
- `@EnumValue` 属性：`enumClass`（目标枚举类）、`method`（取值方法名，默认 `name`）

## 集合元素级（collection）

```java
@NoNullElement        // 元素不可为 null
@UniqueElement        // 元素不可重复
private List<Long> tenantIds;
```

与容器自身的 `@NotEmpty`（集合非空）区分：这两个约束的是**元素**。

## 类级（clazz）：@ClassValidator

跨字段校验走类级约束，`@ClassValidator`（可 `@Repeatable`）挂 `Validatable` 实现类：

```java
@ClassValidator(TimeRangeCheck.class)
public class ActivityCmd implements Validatable {
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Override
    public ValidationResult validate() {
        // 通过返回 ValidationResult.success()；失败用 failure(...) 族（单字段/清单）
    }
}
```

`spring-plus.web.validation.fail-fast` 默认 `true`（类级校验首错即停）。

## 编程式校验：ValidationUtils

不经过 `@Valid` 注解、在代码里手动触发校验时用（`web.validation` 包）：

```java
BindingResult result = ValidationUtils.validate(cmd);                    // 全量
BindingResult result = ValidationUtils.validate(cmd, "phone");           // 单字段
Validator validator = ValidationUtils.obtainValidator();                 // 取底层 Validator 自由组合
ExecutableValidator ev = ValidationUtils.obtainExecutableValidator();    // 方法参数级校验
```

返回标准 `BindingResult`，可自行决定抛 `ServiceException` 还是聚合进响应。

## 校验失败的输出形态（不用自己处理）

- `@Valid @RequestBody` 失败 → `A0430` + `details.violations`（**Spring 7 是 fail-fast，只有单条**；多字段并发失败时字段顺序不定，验收测试须用单失败字段保证确定性）
- 非 body 参数约束（`@RequestParam @Min(1)` 等方法级校验）→ `HandlerMethodValidationException` 被翻译为同构 `A0430`，聚合**全部**参数结果
- SSE 接口握手期失败 → `app-error` 事件（不是 JSON）

## 陷阱

- `ClassValidatorPostProcessor` 反射 hibernate-validator 内部 API，HV 版本由根 POM 锁定 9.1.0.Final；HV 大版本升级（9.x → 10.x）可能破坏该集成，升级须回归 validation 域测试
- 别用 `@Validated` 包裹 + 分组校验的旧范式去模拟类级校验——`@ClassValidator` 就是为此而生
