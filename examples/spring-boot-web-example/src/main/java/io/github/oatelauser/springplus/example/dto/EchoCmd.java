package io.github.oatelauser.springplus.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 测试用 DTO（v2.0 验收，UC-1 / UC-2）。
 * <p>
 * 故意保留多个字段校验规则，让一次 {@code @Valid} 失败可以同时产出多条 violation，
 * 验证 {@code SimpleResponse.details} 的 {@code violations} 列表结构是否完整。
 *
 * <h3>阿里规约对齐</h3>
 * <ul>
 *   <li>属性全部用包装类型（{@link Integer}），未赋值 = null，与 0 区分。</li>
 *   <li>布尔字段命名不以 {@code is} 开头。</li>
 * </ul>
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
@Data
@Schema(description = "v2.0 测试 DTO（验证 @Valid + details）")
public class EchoCmd {

    @NotBlank(message = "name 不能为空")
    @Schema(description = "名称（非空）")
    private String name;

    @NotNull(message = "age 不能为空")
    @Min(value = 0, message = "age 不能小于 0")
    @Schema(description = "年龄（非负整数）")
    private Integer age;
}
