package io.github.oatelauser.springplus.web.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 系统错误状态码（C0xxx）——服务端自身故障或依赖（数据库/缓存/MQ/第三方/分布式）异常。
 * <p>
 * 采用自定义段位（阿里 C 表语义为「第三方/中间件」，与本项目「系统超集」不同，故不强套阿里段号）：
 * <ul>
 *   <li>C01xx 服务内部 / 超时 / 容灾 / 资源</li>
 *   <li>C02xx 数据库</li>
 *   <li>C03xx 缓存</li>
 *   <li>C04xx 消息队列</li>
 *   <li>C05xx 第三方服务</li>
 *   <li>C06xx 分布式</li>
 * </ul>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-01-29
 * @since 1.0
 */
@Getter
@RequiredArgsConstructor
public enum SystemStatus implements ServerStatus {

    // ==================== C01 服务内部 / 超时 / 容灾 / 资源 ====================

    INTERNAL_ERROR(SERVER_INTERNAL_CODE, SERVER_INTERNAL_MSG),
    SERVICE_UNAVAILABLE("C0102", "服务暂不可用"),
    SERVICE_TIMEOUT("C0103", "服务调用超时"),
    SERVICE_DEGRADED("C0104", "服务降级，请稍后再试"),
    SYSTEM_BUSY("C0105", "系统繁忙，请稍后再试"),
    SYSTEM_MAINTENANCE("C0106", "系统维护中，请稍后访问"),
    UNKNOWN_ERROR("C0107", "未知错误: {0}"),
    SYSTEM_RATE_LIMIT("C0108", "系统限流"),
    DISK_EXHAUSTED("C0110", "系统磁盘空间耗尽"),
    MEMORY_EXHAUSTED("C0111", "系统内存耗尽"),
    THREAD_POOL_EXHAUSTED("C0112", "系统线程池耗尽"),
    CONNECTION_POOL_EXHAUSTED("C0113", "系统连接池耗尽"),

    // ==================== C02 数据库 ====================

    DATABASE_ERROR("C0201", "数据库异常"),
    DATABASE_CONNECTION_ERROR("C0202", "数据库连接异常"),
    DATABASE_QUERY_ERROR("C0203", "数据库查询异常"),
    DATABASE_EXECUTE_ERROR("C0204", "数据库执行异常"),
    DATABASE_TRANSACTION_ERROR("C0205", "数据库事务异常"),
    DATABASE_DEADLOCK("C0206", "数据库死锁"),

    // ==================== C03 缓存 ====================

    CACHE_ERROR("C0301", "缓存服务异常"),
    CACHE_NOT_EXIST("C0302", "缓存不存在"),
    CACHE_OPERATION_ERROR("C0303", "缓存操作失败"),
    CACHE_CONNECTION_ERROR("C0304", "缓存连接异常"),

    // ==================== C04 消息队列 ====================

    MQ_SEND_ERROR("C0401", "消息发送失败"),
    MQ_CONSUME_ERROR("C0402", "消息消费失败"),
    MQ_CONNECTION_ERROR("C0403", "消息队列连接异常"),

    // ==================== C05 第三方服务 ====================

    THIRD_PARTY_ERROR("C0501", "第三方服务异常: {0}"),
    REMOTE_SERVICE_ERROR("C0502", "远程服务调用异常"),
    GATEWAY_ERROR("C0503", "网关异常"),
    SMS_SERVICE_ERROR("C0504", "短信服务异常"),
    EMAIL_SERVICE_ERROR("C0505", "邮件服务异常"),
    PAYMENT_SERVICE_ERROR("C0506", "支付服务异常"),
    STORAGE_SERVICE_ERROR("C0507", "存储服务异常"),

    // ==================== C06 分布式 ====================

    DISTRIBUTED_LOCK_ERROR("C0601", "分布式锁获取失败"),
    DISTRIBUTED_TRANSACTION_ERROR("C0602", "分布式事务异常"),
    SERVICE_REGISTRY_ERROR("C0603", "服务注册异常"),
    CONFIG_CENTER_ERROR("C0604", "配置中心异常"),
    ;

    private final String code;
    private final String message;

}
