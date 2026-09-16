package io.github.oatelauser.springplus.boot.client.interceptor;

import io.github.oatelauser.springplus.boot.client.ApiClientSettings;
import io.github.oatelauser.springplus.boot.client.core.ApiRequest;
import io.github.oatelauser.springplus.boot.client.core.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * SSRF 防护过滤器（CWE-918）：请求发出前校验目标主机。
 * <p>
 * 经 {@code spring-plus.client.ssrf.*} 启用（默认关闭保持兼容）。校验两道：
 * <ul>
 *   <li><b>allowlist</b>：配置 {@code allowed-hosts} 后仅允许后缀匹配的主机（例：{@code api.example.com} 放行其子域写全匹配）</li>
 *   <li><b>私网拦截</b>：拒绝环回 / 私有 / 链路本地 / 未指定地址（127/8、10/8、172.16/12、192.168/16、169.254/16、0.0.0.0、::1、fc00::/7、fe80::/10），域名经 DNS 解析后逐地址判定</li>
 * </ul>
 * 目标为相对 URI 时校验 {@code baseUrl} 主机（绝对 URI 可覆盖 baseUrl，是 SSRF 的主注入路径）。
 * DNS 解析失败按 fail-closed 拒绝。
 * <p>
 * 已知局限（纵深而非绝对防护）：DNS rebinding（校验时解析与引擎连接时解析可能不一致）未做 IP pinning；
 * 跨主机重定向的凭据剥离见 HC5 引擎的 redirect 策略（{@code strip-credentials-on-cross-host-redirect}），
 * JDK/SIMPLE 引擎不支持逐跳校验——启用 SSRF 防护的部署建议使用 HTTP_COMPONENTS 引擎。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.1.0
 */
public class SsrfGuardFilter implements ClientFilter {

    private final ApiClientSettings.Ssrf settings;
    private final String baseUrlHost;

    public SsrfGuardFilter(ApiClientSettings.Ssrf settings, String baseUrlHost) {
        this.settings = settings;
        this.baseUrlHost = baseUrlHost;
    }

    @Override
    public ApiResponse<?> doFilter(ApiRequest request, FilterChain chain) {
        String target = resolveTargetHost(request);
        if (target != null) {
            verify(target);
        }
        return chain.doFilter(request);
    }

    @Override
    public int getOrder() {
        // 链最前：非法目标在重试/日志/压缩之前直接拒绝
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    /** 相对 URI 校验 baseUrl 主机；绝对 URI（可覆盖 baseUrl）校验其自身主机 */
    private String resolveTargetHost(ApiRequest request) {
        String uri = request.getUri();
        if (StringUtils.hasText(uri) && URI.create(uri).isAbsolute()) {
            return URI.create(uri).getHost();
        }
        return baseUrlHost;
    }

    /** 包内可见便于单测：主机校验（allowlist + 私网拦截） */
    void verify(String host) {
        if (!StringUtils.hasText(host)) {
            throw new IllegalArgumentException("SSRF 防护：目标主机为空，拒绝请求");
        }
        List<String> allowedHosts = settings.getAllowedHosts();
        if (!allowedHosts.isEmpty() && !matchesAllowlist(host, allowedHosts)) {
            throw new IllegalArgumentException("SSRF 防护：目标主机不在允许列表: " + host);
        }
        if (settings.getDenyPrivateNetwork()) {
            verifyPublicAddress(host);
        }
    }

    private static boolean matchesAllowlist(String host, List<String> allowedHosts) {
        String normalized = host.toLowerCase(Locale.ROOT);
        for (String allowed : allowedHosts) {
            String rule = allowed.toLowerCase(Locale.ROOT);
            if (normalized.equals(rule) || normalized.endsWith("." + rule)) {
                return true;
            }
        }
        return false;
    }

    private static void verifyPublicAddress(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (java.net.UnknownHostException e) {
            // fail-closed：解析失败（含非法主机名）一律拒绝
            throw new IllegalArgumentException("SSRF 防护：目标主机解析失败，拒绝请求: " + host, e);
        }
        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress() || address.isMulticastAddress()) {
                throw new IllegalArgumentException("SSRF 防护：目标主机解析到内网/保留地址，拒绝请求: "
                        + host + " -> " + address.getHostAddress());
            }
        }
    }

}
