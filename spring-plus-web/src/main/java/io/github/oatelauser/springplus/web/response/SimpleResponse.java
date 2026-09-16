package io.github.oatelauser.springplus.web.response;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

import static io.github.oatelauser.springplus.web.response.CommonStatus.SUCCESS;


/**
 * 统一响应对象
 *
 * @author <a href="mailto:545896770@qq.com">DearYang</a>
 * @date 2024-03-18
 * @since 1.0
 */
@Data
public class SimpleResponse<T> {

    /**
     * 响应码，成功返回00000
     */
    private String code;

    /**
     * 响应描述信息
     */
    private String message;

    /**
     * 数据实体
     */
    private T data;

    /**
     * 错误场景下的结构化补充信息（v2.0 新增，设计文档 4.3）。
     * <p>
     * 承载 {@code @Valid} 字段错误清单、限流剩余配额等机器可读的补充数据，来自
     * {@code ErrorDescriptor.getDetails()}。成功场景为 {@code null}，
     * 配合 {@link JsonInclude.Include#NON_NULL} 序列化时不输出，保证成功响应 JSON 与 v1.0 完全一致。
     * <p>
     * 字段语义（设计 1.2 原则 4）：{@code data} 错误场景永远 null；{@code details} 成功场景永远 null，
     * 两者互斥，前端只看 {@code code} 是否为 {@code 00000} 即可区分成败。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Object> details;

    public boolean getSuccess() {
        return SUCCESS.code.equals(code);
    }

    /**
     * 成功响应（无数据）
     */
    public static SimpleResponse<Void> ok() {
        return ok(null);
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> SimpleResponse<T> ok(T data) {
        SimpleResponse<T> serverResponse = new SimpleResponse<>();
        serverResponse.code = SUCCESS.code;
        serverResponse.message = SUCCESS.message;
        serverResponse.data = data;
        return serverResponse;
    }

    /**
     * 成功响应（带数据 + 自定义消息）
     */
    public static <T> SimpleResponse<T> ok(String message, T data) {
        SimpleResponse<T> serverResponse = new SimpleResponse<>();
        serverResponse.code = SUCCESS.code;
        serverResponse.message = message;
        serverResponse.data = data;
        return serverResponse;
    }

    /**
     * 失败响应（自定义 code 和 message）
     */
    public static <T> SimpleResponse<T> fail(String code, String message) {
        SimpleResponse<T> serverResponse = new SimpleResponse<>();
        serverResponse.code = code;
        serverResponse.message = message;
        return serverResponse;
    }

    /**
     * 失败响应（自定义 code + message + 结构化补充信息）（v2.0 新增，设计文档 4.3）。
     * <p>
     * 由默认 {@code ExceptionRenderer} 在最后一刻从 {@code ErrorDescriptor} 构造响应体时调用，
     * 把 {@code details}（字段错误清单 / 限流配额等）一并输出给前端。{@code details} 为 null 时
     * 与 {@link #fail(String, String)} 行为完全一致。
     *
     * @param code    错误码
     * @param message 用户可见错误文案
     * @param details 结构化补充信息，可为 null
     * @return 失败响应
     */
    public static <T> SimpleResponse<T> fail(String code, String message, Map<String, Object> details) {
        SimpleResponse<T> serverResponse = new SimpleResponse<>();
        serverResponse.code = code;
        serverResponse.message = message;
        serverResponse.details = details;
        return serverResponse;
    }

    /**
     * 失败响应（状态码枚举）
     */
    public static <T> SimpleResponse<T> fail(ServerStatus status) {
        return fail(status.getCode(), status.getMessage());
    }

    /**
     * 失败响应（状态码枚举）
     */
    public static <T> SimpleResponse<T> fail(ServerStatusProvider statusProvider) {
        return fail(statusProvider.getServerStatus());
    }

    /**
     * 失败响应（状态码枚举 + 自定义消息）
     */
    public static <T> SimpleResponse<T> fail(String message, ServerStatus status) {
        return fail(status.getCode(), message);
    }

    /**
     * 失败响应（带占位符参数）
     * <pre>
     * SimpleResponse.fail(ClientStatus.PARAMETER_MISSING, "userId")
     * => code: "A0101", message: "必填参数缺失: userId"
     * </pre>
     */
    public static <T> SimpleResponse<T> fail(ServerStatus status, Object... params) {
        return fail(status.getCode(), status.format(params));
    }

}
