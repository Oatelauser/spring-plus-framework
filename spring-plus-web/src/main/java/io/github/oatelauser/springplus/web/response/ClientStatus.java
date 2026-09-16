package io.github.oatelauser.springplus.web.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 客户端错误状态码（A0xxx），码段对齐阿里规范：
 * <ul>
 *   <li>A01xx 用户注册</li>
 *   <li>A02xx 用户登录</li>
 *   <li>A03xx 访问权限</li>
 *   <li>A04xx 请求参数（含方法/Content-Type/路由等请求级错误）</li>
 *   <li>A05xx 请求服务异常（限流/并发/重复请求/超时）</li>
 *   <li>A06xx 用户资源（余额/配额）</li>
 *   <li>A07xx 用户上传文件</li>
 *   <li>A08xx 当前版本</li>
 * </ul>
 * 跳过移动端专用的 A09 隐私、A10 设备及生物识别条目。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-01-29
 * @since 1.0
 */
@Getter
@RequiredArgsConstructor
public enum ClientStatus implements ServerStatus {

    // ==================== A01 用户注册 ====================

    USERNAME_VALIDATE_FAILED("A0110", "用户名校验失败"),
    USERNAME_ALREADY_EXISTS("A0111", "用户名已存在"),
    USERNAME_CONTAIN_SENSITIVE("A0112", "用户名包含敏感词"),
    USERNAME_CONTAIN_SPECIAL_CHAR("A0113", "用户名包含特殊字符"),
    PASSWORD_VALIDATE_FAILED("A0120", "密码校验失败"),
    PASSWORD_LENGTH_NOT_ENOUGH("A0121", "密码长度不符合要求"),
    PASSWORD_STRENGTH_NOT_ENOUGH("A0122", "密码强度不够"),
    CAPTCHA_INPUT_ERROR("A0130", "校验码输入错误"),
    SMS_CAPTCHA_ERROR("A0131", "短信校验码输入错误"),
    EMAIL_CAPTCHA_ERROR("A0132", "邮件校验码输入错误"),
    ID_CARD_TYPE_NOT_SELECTED("A0141", "用户证件类型未选择"),
    ID_CARD_INVALID("A0142", "大陆身份证编号校验非法"),
    PHONE_FORMAT_ERROR("A0151", "手机格式校验失败"),
    ADDRESS_FORMAT_ERROR("A0152", "地址格式校验失败"),
    EMAIL_FORMAT_ERROR("A0153", "邮箱格式校验失败"),

    // ==================== A02 用户登录 ====================

    ACCOUNT_NOT_EXIST("A0201", "用户账户不存在"),
    ACCOUNT_FROZEN("A0202", "用户账户被冻结"),
    ACCOUNT_DISABLED("A0203", "用户账户已作废"),
    PASSWORD_ERROR("A0210", "用户密码错误"),
    PASSWORD_ERROR_EXCEED_LIMIT("A0211", "用户输入密码错误次数超限"),
    LOGIN_EXPIRED("A0230", "用户登录已过期"),
    VERIFY_CODE_ERROR("A0240", "用户验证码错误"),
    VERIFY_CODE_EXCEED_LIMIT("A0241", "用户验证码尝试次数超限"),

    // ==================== A03 访问权限 ====================

    UNAUTHORIZED("A0301", "访问未授权，请先登录"),
    ACCESS_DENIED("A0312", "无权限使用此 API"),
    AUTH_EXPIRED("A0311", "授权已过期，请重新登录"),
    ACCESS_BLOCKED("A0320", "用户访问被拦截"),
    BLACKLIST_USER("A0321", "黑名单用户"),
    ILLEGAL_IP("A0323", "非法 IP 地址"),
    SERVICE_IN_ARREARS("A0330", "服务已欠费"),
    SIGNATURE_ERROR("A0340", "用户签名异常"),
    RSA_SIGNATURE_ERROR("A0341", "RSA 签名错误"),

    // ==================== A04 请求参数 ====================

    INVALID_INPUT("A0402", "无效的用户输入"),
    PARAMETER_MISSING("A0410", "必填参数缺失: {0}"),
    TIMESTAMP_MISSING("A0413", "缺少时间戳参数"),
    TIMESTAMP_INVALID("A0414", "非法的时间戳参数"),
    PARAMETER_RANGE_ERROR("A0420", "请求参数值超出允许的范围"),
    PARAMETER_TYPE_ERROR("A0421", "参数格式不匹配"),
    JSON_PARSE_ERROR("A0427", "请求 JSON 解析失败"),
    PARAMETER_VALIDATION_FAILED("A0430", "参数校验失败: {0}"),
    HEADER_MISSING("A0415", "请求头缺失: {0}"),
    METHOD_NOT_SUPPORTED("A0490", "不支持的请求方法"),
    CONTENT_TYPE_NOT_SUPPORTED("A0491", "不支持的 Content-Type"),
    REQUEST_BODY_MISSING("A0492", "请求体不能为空"),
    NOT_FOUND("A0493", "请求的资源不存在"),

    // ==================== A05 请求服务异常 ====================

    TOO_MANY_REQUESTS("A0501", "请求次数超出限制"),
    CONCURRENT_LIMIT("A0502", "请求并发数超出限制"),
    OPERATION_WAITING("A0503", "用户操作请等待"),
    DUPLICATE_REQUEST("A0506", "用户重复请求，请勿重复提交"),
    REQUEST_TIMEOUT("A0507", "请求超时"),

    // ==================== A06 用户资源 ====================

    BALANCE_INSUFFICIENT("A0601", "账户余额不足"),
    QUOTA_EXHAUSTED("A0605", "用户配额已用光"),

    // ==================== A07 用户上传文件 ====================

    FILE_TYPE_NOT_MATCH("A0701", "用户上传文件类型不匹配"),
    FILE_SIZE_EXCEED("A0702", "用户上传文件太大"),
    IMAGE_SIZE_EXCEED("A0703", "用户上传图片太大"),
    VIDEO_SIZE_EXCEED("A0704", "用户上传视频太大"),
    ARCHIVE_SIZE_EXCEED("A0705", "用户上传压缩文件太大"),

    // ==================== A08 当前版本 ====================

    VERSION_TOO_LOW("A0802", "用户安装版本过低"),
    API_VERSION_MISMATCH("A0805", "用户 API 请求版本不匹配"),
    API_VERSION_TOO_HIGH("A0806", "用户 API 请求版本过高"),
    API_VERSION_TOO_LOW("A0807", "用户 API 请求版本过低"),
    ;

    private final String code;
    private final String message;

}