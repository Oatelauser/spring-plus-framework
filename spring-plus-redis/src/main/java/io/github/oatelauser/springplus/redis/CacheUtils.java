package io.github.oatelauser.springplus.redis;


/**
 * 缓存工具类
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-10-12
 * @since 1.0
 */
public abstract class CacheUtils {

    /**
     * 缓存关键字连接符号:冒号
     */
    public static final String COLON = ":";

    public static String getCacheKey(KeyValue kv, String key) {
        return kv.getPrefix() + COLON + key;
    }

    public static String getCacheKey(KeyValue kv, String key1, String key2) {
        return kv.getPrefix() + COLON + key1 + COLON + key2;
    }

}
