package io.github.oatelauser.springplus.web.response;

/**
 * 字段校验错误条目
 * <p>
 * 失败响应 {@code details.violations} 列表中的单条记录。JSON 形态与裸 Map（{@code {"field": ..., "msg": ...}}）
 * 保持一致，仅在 Java 侧提供类型安全载体。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0
 */
public record FieldErrorInfo(String field, String msg) {

    public static FieldErrorInfo of(String field, String msg) {
        return new FieldErrorInfo(field, msg);
    }

}
