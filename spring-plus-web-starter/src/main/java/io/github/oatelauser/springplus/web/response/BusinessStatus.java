package io.github.oatelauser.springplus.web.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 业务错误状态码（B0xxx）——请求已进入业务逻辑、违反领域规则或状态时抛出。
 * <p>
 * 用户流程（注册/登录/权限）、用户资源（余额/配额）、文件上传大小/类型、版本等
 * 客户端分类已迁至 {@link ClientStatus}；本枚举只保留纯领域/规则/组织/配置/文件业务约束。
 * <ul>
 *   <li>B01xx 数据操作</li>
 *   <li>B02xx 业务规则</li>
 *   <li>B03xx 组织架构</li>
 *   <li>B04xx 配置</li>
 *   <li>B05xx 文件业务约束</li>
 * </ul>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-01-29
 * @since 1.0
 */
@Getter
@RequiredArgsConstructor
public enum BusinessStatus implements ServerStatus {

    // ==================== B01 数据操作 ====================

    DATA_NOT_EXIST("B0101", "数据不存在"),
    DATA_ALREADY_EXISTS("B0102", "数据已存在"),
    DATA_DUPLICATE("B0103", "数据重复"),
    DATA_STATUS_ERROR("B0104", "数据状态异常"),
    DATA_IN_USE("B0105", "数据正在使用中，无法操作"),
    DATA_HAS_CHILDREN("B0106", "存在子级数据，无法删除"),
    DATA_REFERENCED("B0107", "数据关联中，无法删除"),
    OPTIMISTIC_LOCK_ERROR("B0108", "数据已被修改，请刷新后重试"),

    // ==================== B02 业务规则 ====================

    OPERATION_NOT_ALLOWED("B0201", "当前状态不允许此操作"),
    BUSINESS_LIMIT_EXCEEDED("B0202", "业务限制已达上限"),
    TIME_RANGE_ERROR("B0203", "时间范围错误"),
    START_TIME_AFTER_END_TIME("B0204", "开始时间不能晚于结束时间"),
    CONCURRENT_CONFLICT("B0205", "操作冲突，请稍后重试"),
    BATCH_PARTIAL_FAILED("B0206", "批量操作部分失败"),
    OPERATION_FAILED("B0207", "操作失败: {0}"),
    STOCK_INSUFFICIENT("B0208", "库存不足"),
    REPEAT_SUBMIT("B0209", "请勿重复提交"),

    // ==================== B03 组织架构 ====================

    PROJECT_NOT_EXIST("B0301", "项目不存在"),
    PROJECT_ALREADY_EXISTS("B0302", "项目已存在"),
    TENANT_NOT_EXIST("B0303", "租户不存在"),
    TENANT_ALREADY_EXISTS("B0304", "租户已存在"),
    DEPARTMENT_NOT_EXIST("B0305", "部门不存在"),
    ROLE_NOT_EXIST("B0306", "角色不存在"),

    // ==================== B04 配置 ====================

    CONFIG_NOT_EXIST("B0401", "配置项不存在"),
    CONFIG_INVALID("B0402", "配置项无效"),

    // ==================== B05 文件（业务约束） ====================

    FILE_NOT_EXIST("B0501", "文件不存在"),
    FILE_ALREADY_EXISTS("B0502", "文件已存在"),
    ILLEGAL_FILE_PATH("B0503", "非法的文件路径"),
    DIRECTORY_NOT_EMPTY("B0504", "目录非空，无法删除"),
    PARENT_DIRECTORY_NOT_EXIST("B0505", "父目录不存在"),
    FILE_NAME_TOO_LONG("B0506", "文件名过长"),
    FILE_NOT_SUPPORT_VIEW("B0507", "该文件类型不支持预览"),
    FILE_SUFFIX_NOT_ALLOW_CHANGE("B0508", "文件后缀不支持修改"),
    ;

    private final String code;
    private final String message;

}
