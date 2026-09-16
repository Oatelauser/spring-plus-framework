package io.github.oatelauser.springplus.boot.redis;

/**
 * 缓存定义
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-10-12
 * @since 1.0
 */
public interface KeyValue {

    /**
     * 缓存前缀
     */
    String getPrefix();

    /**
     * 缓存名称
     */
    String getName();

    /**
     * 缓存过期时间
     */
    default long getExpire() {
        return 0L;
    }

    /**
     * 缓存对象
     */
    default Class<?> getClazz() {
        return null;
    }

    /**
     * 缓存描述
     */
    default String desc() {
        return "";
    }

}
