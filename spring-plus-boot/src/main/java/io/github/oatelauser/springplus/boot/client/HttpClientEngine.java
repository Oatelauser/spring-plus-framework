package io.github.oatelauser.springplus.boot.client;

/**
 * HTTP 客户端引擎
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-19
 * @since 1.0
 */
public enum HttpClientEngine {

    /**
     * 自动检测：Apache HC5 → JDK（按优先级）
     */
    AUTO,

    /**
     * Apache HttpClient 5（支持精细连接池 / 连接驱逐）
     */
    HTTP_COMPONENTS,

    /**
     * JDK 11+ HttpClient（零依赖 / 支持 HTTP/2）
     */
    JDK,

    /**
     * java.net.HttpURLConnection — 零依赖、零连接池
     * <p>适合低频调用或对依赖体积敏感的场景</p>
     */
    SIMPLE,

}
