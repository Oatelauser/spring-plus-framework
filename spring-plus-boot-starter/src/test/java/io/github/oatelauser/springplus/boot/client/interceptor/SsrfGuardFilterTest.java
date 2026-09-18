package io.github.oatelauser.springplus.boot.client.interceptor;

import io.github.oatelauser.springplus.boot.client.ApiClientSettings;
import io.github.oatelauser.springplus.boot.client.core.ApiRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SSRF 防护（V09/SEC-004，CWE-918）：allowlist 后缀匹配、私网/环回拦截、解析失败 fail-closed。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.1.0
 */
class SsrfGuardFilterTest {

    private ApiClientSettings.Ssrf ssrf(boolean allowlist, boolean denyPrivate) {
        ApiClientSettings.Ssrf s = new ApiClientSettings.Ssrf();
        if (allowlist) {
            s.setAllowedHosts(java.util.List.of("api.example.com"));
        }
        s.setDenyPrivateNetwork(denyPrivate);
        return s;
    }

    private SsrfGuardFilter filter(ApiClientSettings.Ssrf s, String baseUrlHost) {
        return new SsrfGuardFilter(s, baseUrlHost);
    }

    // ───────────── 私网 / 保留地址拦截 ─────────────

    @Test
    void privateNetworkTargetsRejected() {
        SsrfGuardFilter guard = filter(ssrf(false, true), "api.example.com");
        for (String host : new String[]{
                "127.0.0.1", "127.0.0.8", "localhost", "10.1.2.3", "192.168.1.1",
                "172.16.0.9", "169.254.169.254", "0.0.0.0", "[::1]", "[fe80::1]"}) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> guard.verify(host), host + " 应被拒绝");
            assertTrue(ex.getMessage().contains("SSRF"), ex.getMessage());
        }
    }

    @Test
    void publicHostPassesPrivateCheck() {
        SsrfGuardFilter guard = filter(ssrf(false, true), "api.example.com");
        // example.com 是 IANA 保留文档域但解析到公网地址；不触发 allowlist（未配置）
        assertDoesNotThrow(() -> guard.verify("example.com"));
    }

    // ───────────── allowlist ─────────────

    @Test
    void allowlistSuffixMatching() {
        SsrfGuardFilter guard = filter(ssrf(true, false), "api.example.com");
        assertDoesNotThrow(() -> guard.verify("api.example.com"));
        assertDoesNotThrow(() -> guard.verify("v2.api.example.com"));
        // 后缀匹配不吃伪域名
        assertThrows(IllegalArgumentException.class, () -> guard.verify("evilapi.example.com.attacker.io"));
        assertThrows(IllegalArgumentException.class, () -> guard.verify("notexample.com"));
    }

    // ───────────── fail-closed ─────────────

    @Test
    void unresolvableHostRejected() {
        SsrfGuardFilter guard = filter(ssrf(false, true), "api.example.com");
        assertThrows(IllegalArgumentException.class,
                () -> guard.verify("this-host-does-not-exist.invalid"));
        assertThrows(IllegalArgumentException.class, () -> guard.verify(""));
    }

    // ───────────── 请求级解析：绝对 URI 覆盖 baseUrl 的主注入路径 ─────────────

    @Test
    void absoluteUriOverridesBaseUrlAndGetsChecked() {
        SsrfGuardFilter guard = filter(ssrf(false, true), "api.example.com");
        ApiRequest attack = ApiRequest.get("http://169.254.169.254/latest/meta-data").build();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guard.doFilter(attack, request -> {
                    throw new AssertionError("不应到达链下游");
                }));
        assertTrue(ex.getMessage().contains("SSRF"));
    }

    @Test
    void relativeUriFallsBackToBaseUrlHost() {
        SsrfGuardFilter guard = filter(ssrf(false, true), "localhost");
        ApiRequest relative = ApiRequest.get("/users/1").build();
        assertThrows(IllegalArgumentException.class,
                () -> guard.doFilter(relative, request -> {
                    throw new AssertionError("baseUrl 为 localhost 也应被拦");
                }));
    }

}
