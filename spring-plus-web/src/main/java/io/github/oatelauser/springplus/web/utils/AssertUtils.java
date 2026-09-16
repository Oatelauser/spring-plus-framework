package io.github.oatelauser.springplus.web.utils;

import io.github.oatelauser.springplus.web.error.ServiceException;
import io.github.oatelauser.springplus.web.response.ServerStatus;

import java.util.Collection;
import java.util.Map;

/**
 * 运行时业务断言工具
 * <p>
 * 参考 {@code org.springframework.util.Assert} 的形态，断言失败直接抛出 {@link ServiceException}，
 * 替代业务代码中 {@code if (...) throw new ServiceException(...)} 的样板：
 *
 * <pre>{@code
 * User user = userMapper.selectOneById(id);
 * AssertUtils.notNull(user, BusinessStatus.DATA_NOT_EXIST);
 *
 * // 占位符格式化：message = "数据不存在: 42"
 * AssertUtils.notNull(user, BusinessStatus.DATA_NOT_EXIST, id);
 *
 * // 直接覆盖文案
 * AssertUtils.notNull(user, BusinessStatus.DATA_NOT_EXIST, "用户不存在");
 *
 * // 临时码直抛（一次性场景）
 * AssertUtils.isTrue(amount.signum() > 0, "B0102", "金额必须为正数");
 * }</pre>
 *
 * 消息维度说明：带单个 {@code String} 参数的重载视为<b>自定义文案</b>（覆盖状态默认消息）；
 * 占位符格式化请传多个参数或显式 {@code Object} 数组，避免与文案重载产生歧义。
 * <p>
 * 未提供 {@code isNull} / {@code isInstanceOf} 等框架内部状态检查语义的方法——业务断言
 * 高频场景就是空/真/假/空白/空集合/含 null 元素这几类。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0
 */
public final class AssertUtils {

    private AssertUtils() {
    }

    // ────────────────────────── notNull ──────────────────────────

    public static void notNull(Object obj, ServerStatus status) {
        if (obj == null) {
            throw fail(status);
        }
    }

    public static void notNull(Object obj, ServerStatus status, Object... params) {
        if (obj == null) {
            throw fail(status, params);
        }
    }

    public static void notNull(Object obj, ServerStatus status, String message) {
        if (obj == null) {
            throw fail(status, message);
        }
    }

    public static void notNull(Object obj, String code, String message) {
        if (obj == null) {
            throw fail(code, message);
        }
    }

    // ────────────────────────── isTrue / state ──────────────────────────

    /**
     * 表达式为 {@code false} 时失败。
     */
    public static void isTrue(boolean expression, ServerStatus status) {
        if (!expression) {
            throw fail(status);
        }
    }

    public static void isTrue(boolean expression, ServerStatus status, Object... params) {
        if (!expression) {
            throw fail(status, params);
        }
    }

    public static void isTrue(boolean expression, ServerStatus status, String message) {
        if (!expression) {
            throw fail(status, message);
        }
    }

    public static void isTrue(boolean expression, String code, String message) {
        if (!expression) {
            throw fail(code, message);
        }
    }

    /**
     * 表达式为 {@code false} 时失败。语义用于"不应到达的分支"（与 {@link #isTrue} 行为一致）。
     */
    public static void state(boolean expression, ServerStatus status) {
        if (!expression) {
            throw fail(status);
        }
    }

    public static void state(boolean expression, ServerStatus status, Object... params) {
        if (!expression) {
            throw fail(status, params);
        }
    }

    public static void state(boolean expression, ServerStatus status, String message) {
        if (!expression) {
            throw fail(status, message);
        }
    }

    public static void state(boolean expression, String code, String message) {
        if (!expression) {
            throw fail(code, message);
        }
    }

    // ────────────────────────── hasText ──────────────────────────

    /**
     * 字符串为 {@code null} 或空白时失败。
     */
    public static void hasText(String text, ServerStatus status) {
        if (text == null || text.isBlank()) {
            throw fail(status);
        }
    }

    public static void hasText(String text, ServerStatus status, Object... params) {
        if (text == null || text.isBlank()) {
            throw fail(status, params);
        }
    }

    public static void hasText(String text, ServerStatus status, String message) {
        if (text == null || text.isBlank()) {
            throw fail(status, message);
        }
    }

    public static void hasText(String text, String code, String message) {
        if (text == null || text.isBlank()) {
            throw fail(code, message);
        }
    }

    // ────────────────────────── notEmpty（集合 / 数组 / Map） ──────────────────────────

    public static void notEmpty(Collection<?> collection, ServerStatus status) {
        if (collection == null || collection.isEmpty()) {
            throw fail(status);
        }
    }

    public static void notEmpty(Collection<?> collection, ServerStatus status, Object... params) {
        if (collection == null || collection.isEmpty()) {
            throw fail(status, params);
        }
    }

    public static void notEmpty(Collection<?> collection, ServerStatus status, String message) {
        if (collection == null || collection.isEmpty()) {
            throw fail(status, message);
        }
    }

    public static void notEmpty(Collection<?> collection, String code, String message) {
        if (collection == null || collection.isEmpty()) {
            throw fail(code, message);
        }
    }

    public static void notEmpty(Object[] array, ServerStatus status) {
        if (array == null || array.length == 0) {
            throw fail(status);
        }
    }

    public static void notEmpty(Object[] array, ServerStatus status, Object... params) {
        if (array == null || array.length == 0) {
            throw fail(status, params);
        }
    }

    public static void notEmpty(Object[] array, ServerStatus status, String message) {
        if (array == null || array.length == 0) {
            throw fail(status, message);
        }
    }

    public static void notEmpty(Object[] array, String code, String message) {
        if (array == null || array.length == 0) {
            throw fail(code, message);
        }
    }

    public static void notEmpty(Map<?, ?> map, ServerStatus status) {
        if (map == null || map.isEmpty()) {
            throw fail(status);
        }
    }

    public static void notEmpty(Map<?, ?> map, ServerStatus status, Object... params) {
        if (map == null || map.isEmpty()) {
            throw fail(status, params);
        }
    }

    public static void notEmpty(Map<?, ?> map, ServerStatus status, String message) {
        if (map == null || map.isEmpty()) {
            throw fail(status, message);
        }
    }

    public static void notEmpty(Map<?, ?> map, String code, String message) {
        if (map == null || map.isEmpty()) {
            throw fail(code, message);
        }
    }

    // ────────────────────────── noNullElements ──────────────────────────

    /**
     * 集合为 {@code null} 或含有 {@code null} 元素时失败。
     */
    public static void noNullElements(Collection<?> collection, ServerStatus status) {
        if (collection == null || hasNullElements(collection)) {
            throw fail(status);
        }
    }

    public static void noNullElements(Collection<?> collection, ServerStatus status, Object... params) {
        if (collection == null || hasNullElements(collection)) {
            throw fail(status, params);
        }
    }

    public static void noNullElements(Collection<?> collection, ServerStatus status, String message) {
        if (collection == null || hasNullElements(collection)) {
            throw fail(status, message);
        }
    }

    public static void noNullElements(Collection<?> collection, String code, String message) {
        if (collection == null || hasNullElements(collection)) {
            throw fail(code, message);
        }
    }

    // ────────────────────────── 失败构造 ──────────────────────────

    private static boolean hasNullElements(Collection<?> collection) {
        // JDK 不可变集合（List.of 等）的 contains(null) 会抛 NPE，必须遍历判空
        for (Object element : collection) {
            if (element == null) {
                return true;
            }
        }
        return false;
    }

    private static ServiceException fail(ServerStatus status) {
        return new ServiceException(status);
    }

    private static ServiceException fail(ServerStatus status, Object... params) {
        return new ServiceException(status.getCode(), status.format(params));
    }

    private static ServiceException fail(ServerStatus status, String message) {
        return new ServiceException(status.getCode(), message);
    }

    private static ServiceException fail(String code, String message) {
        return new ServiceException(code, message);
    }

}
