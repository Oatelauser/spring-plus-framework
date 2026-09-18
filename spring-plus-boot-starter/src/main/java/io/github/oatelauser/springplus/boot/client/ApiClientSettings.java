package io.github.oatelauser.springplus.boot.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesSource;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API 客户端统一配置
 * <p>
 * 默认绑定前缀 {@code spring-plus.client}（base-url 配置时自动装配默认 {@code ApiClient} Bean）；
 * 多客户端场景仍建议走 {@code ApiClient.builder()}，自行 {@code @Bean + @ConfigurationProperties}
 * 绑定多个前缀实例。
 *
 * <p>扁平化设计：高频通用字段直接放顶层，引擎独占配置各一个内部类、不再嵌套</p>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-03-03
 * @since 1.1
 */
@Data
@ConfigurationProperties("spring-plus.client")
@ConfigurationPropertiesSource
public class ApiClientSettings {


    // ========================= 基本信息 =========================

    /**
     * 服务基础 URL（必填）
     */
    private String baseUrl;

    /**
     * HTTP 引擎选择
     */
    private HttpClientEngine engine = HttpClientEngine.AUTO;

    // ========================= 通用配置 =========================

    private Duration connectTimeout = Duration.ofSeconds(10);

    private Duration readTimeout = Duration.ofSeconds(30);

    private boolean followRedirect = true;

    /**
     * 跨主机重定向时剥离 Authorization/Cookie 凭据头（防凭据经 30x 泄露给第三方/内网），
     * 默认 true；仅 HTTP_COMPONENTS 引擎生效。
     */
    private boolean stripCredentialsOnCrossHostRedirect = true;

    /**
     * 默认请求头（设到 RestClient 层，不经过 Factory Builder）
     */
    private Map<String, String> defaultHeaders = new LinkedHashMap<>();

    // ========================= SSL 配置（verify + bundle 合一） =========================

    @NestedConfigurationProperty
    private Ssl ssl = new Ssl();

    // ========================= 代理 =========================

    @NestedConfigurationProperty
    private Proxy proxy;

    // ========================= 日志拦截器 =========================

    @NestedConfigurationProperty
    private Logging logging = new Logging();

    // ========================= SSRF 防护（默认关闭） =========================

    /**
     * SSRF 防护（CWE-918）：启用后每个请求发出前校验目标主机（allowlist + 私网拦截）。
     * <p>
     * 默认关闭保持兼容；面向公网的回调/Webhook/抓取类调用建议开启。
     */
    @NestedConfigurationProperty
    private Ssrf ssrf = new Ssrf();

    @NestedConfigurationProperty
    private Compression compression = new Compression();

    // ========================= 引擎独占 =========================

    @NestedConfigurationProperty
    private ApacheHc5 apacheHc5 = new ApacheHc5();

    @NestedConfigurationProperty
    private Jdk jdk = new Jdk();

    // ========================= 便捷方法 =========================

    public String getFullBaseUrl() {
        if (baseUrl == null) return null;
        return baseUrl.replaceAll("/+$", "");
    }

    // ========================================================================
    //                          内部配置类
    // ========================================================================

    /**
     * SSL / TLS 配置
     *
     * <ul>
     *   <li>{@code bundle} 已配置 → 使用该 SslBundle（优先级最高）</li>
     *   <li>{@code bundle} 未配置 且 {@code verify=false} → 信任所有证书（仅开发/测试）</li>
     *   <li>{@code bundle} 未配置 且 {@code verify=true}  → JVM 默认信任库</li>
     * </ul>
     */
    @Data
    public static class Ssl {
        /**
         * 是否校验服务端证书（false = 信任所有，仅限 dev/test）
         */
        private boolean verify = true;
        /**
         * trust-all 显式确认开关：verify=false 时必须同时置为 true 才生效，
         * 否则启动直接失败——防止误配置把证书校验关掉
         */
        private boolean allowInsecure = false;
        /**
         * 引用 spring.ssl.bundle.* 中已定义的 bundle 名称
         */
        private String bundle;

        /**
         * bundle 已配置时优先走 bundle，忽略 verify
         */
        public boolean hasBundleName() {
            return bundle != null && !bundle.isBlank();
        }
    }

    @Data
    public static class Proxy {
        private String host;
        private int port;
        private String username;
        private String password;

        public boolean isConfigured() {
            return host != null && !host.isBlank() && port > 0;
        }

        public boolean hasCredentials() {
            return username != null && !username.isBlank();
        }
    }

    @Data
    public static class Logging {
        private boolean enabled = false;
        private LogLevel level = LogLevel.BASIC;
        private int maxBodyLogSize = 4096;
        /**
         * false = 敏感请求头显示 [REDACTED]
         */
        private boolean sensitiveHeaders = false;

        public enum LogLevel {NONE, BASIC, HEADERS, BODY}
    }

    @Data
    public static class Compression {
        private boolean enabled = false;
        private long minSize = 1024; // bytes
    }

    /**
     * Apache HC5 独占 — 扁平化，不再嵌套 Pool
     */
    @Data
    public static class ApacheHc5 {
        /**
         * 连接池总容量
         */
        private int maxConnections = 100;
        /**
         * 单路由最大连接数
         */
        private int maxConnectionsPerRoute = 20;
        /**
         * 连接最大存活时间
         */
        private Duration timeToLive = Duration.ofMinutes(5);
        /**
         * 空闲连接取出前先验证的间隔
         */
        private Duration validateAfterInactivity = Duration.ofSeconds(2);
        /**
         * 自动驱逐过期连接
         */
        private boolean evictExpiredConnections = true;
        /**
         * 空闲连接驱逐超时
         */
        private Duration evictIdleConnections = Duration.ofMinutes(5);
    }

    /**
     * JDK HttpClient 独占
     */
    @Data
    public static class Jdk {
        /**
         * HTTP 协议版本
         */
        private HttpVersion version = HttpVersion.HTTP_2;
        /**
         * 线程池大小（0 = JDK 默认）
         */
        private int executorThreads = 0;

        public enum HttpVersion {HTTP_1_1, HTTP_2}
    }

    /**
     * SSRF 防护配置（spring-plus.client.ssrf.*）
     */
    @Data
    public static class Ssrf {

        /**
         * 是否启用 SSRF 防护，默认 false（兼容）。
         */
        private boolean enabled = false;

        /**
         * 目标主机允许列表（后缀匹配：api.example.com 放行其自身与子域）。
         * 为空时不做 allowlist 校验（仅私网拦截）。
         */
        private java.util.List<String> allowedHosts = new java.util.ArrayList<>();

        /**
         * 拒绝内网/保留地址（环回、私有、链路本地、未指定、组播），默认 true。
         * 域名经 DNS 解析后逐地址判定；解析失败 fail-closed 拒绝。
         */
        private boolean denyPrivateNetwork = true;
    }

}
