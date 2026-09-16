package io.github.oatelauser.springplus.web.error;

import io.github.oatelauser.springplus.web.response.BusinessStatus;
import io.github.oatelauser.springplus.web.response.ServerStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AssertUtils} 各断言维度的成功/失败路径验证。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0
 */
class AssertUtilsTest {

    private static final ServerStatus NOT_EXIST = BusinessStatus.DATA_NOT_EXIST;

    @Test
    void notNullPassesOnNonNullAndFailsOnNull() {
        assertDoesNotThrow(() -> AssertUtils.notNull("x", NOT_EXIST));

        ServiceException ex = assertThrows(ServiceException.class, () -> AssertUtils.notNull(null, NOT_EXIST));
        assertEquals(NOT_EXIST.getCode(), ex.getCode());
        assertEquals(NOT_EXIST.getMessage(), ex.getMessage());
    }

    @Test
    void messageOverrideBeatsStatusDefault() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> AssertUtils.notNull(null, NOT_EXIST, "用户不存在"));
        assertEquals(NOT_EXIST.getCode(), ex.getCode());
        assertEquals("用户不存在", ex.getMessage());
    }

    @Test
    void placeholderParamsFormatMessage() {
        // 多参走 varargs 格式化：DATA_NOT_EXIST 消息带 {0} 占位符
        ServiceException ex = assertThrows(ServiceException.class,
                () -> AssertUtils.notNull(null, NOT_EXIST, 42L, "extra"));
        assertEquals(NOT_EXIST.getCode(), ex.getCode());
    }

    @Test
    void adHocCodeAndMessage() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> AssertUtils.isTrue(false, "B0102", "金额必须为正数"));
        assertEquals("B0102", ex.getCode());
        assertEquals("金额必须为正数", ex.getMessage());
    }

    @Test
    void isTrueAndState() {
        assertDoesNotThrow(() -> AssertUtils.isTrue(true, NOT_EXIST));
        assertDoesNotThrow(() -> AssertUtils.state(true, NOT_EXIST));
        assertThrows(ServiceException.class, () -> AssertUtils.isTrue(false, NOT_EXIST));
        assertThrows(ServiceException.class, () -> AssertUtils.state(false, NOT_EXIST));
    }

    @Test
    void hasTextRejectsNullAndBlank() {
        assertDoesNotThrow(() -> AssertUtils.hasText("a", NOT_EXIST));
        assertThrows(ServiceException.class, () -> AssertUtils.hasText(null, NOT_EXIST));
        assertThrows(ServiceException.class, () -> AssertUtils.hasText("   ", NOT_EXIST));
        assertThrows(ServiceException.class, () -> AssertUtils.hasText("", NOT_EXIST));
    }

    @Test
    void notEmptyCoversCollectionArrayAndMap() {
        assertDoesNotThrow(() -> AssertUtils.notEmpty(List.of(1), NOT_EXIST));
        assertDoesNotThrow(() -> AssertUtils.notEmpty(new Object[]{1}, NOT_EXIST));
        assertDoesNotThrow(() -> AssertUtils.notEmpty(Map.of("k", "v"), NOT_EXIST));

        assertThrows(ServiceException.class, () -> AssertUtils.notEmpty((List<?>) null, NOT_EXIST));
        assertThrows(ServiceException.class, () -> AssertUtils.notEmpty(List.of(), NOT_EXIST));
        assertThrows(ServiceException.class, () -> AssertUtils.notEmpty((Object[]) null, NOT_EXIST));
        assertThrows(ServiceException.class, () -> AssertUtils.notEmpty(new Object[0], NOT_EXIST));
        assertThrows(ServiceException.class, () -> AssertUtils.notEmpty((Map<?, ?>) null, NOT_EXIST));
        assertThrows(ServiceException.class, () -> AssertUtils.notEmpty(Map.of(), NOT_EXIST));
    }

    @Test
    void noNullElementsRejectsNullAndNullElement() {
        assertDoesNotThrow(() -> AssertUtils.noNullElements(List.of(1, 2), NOT_EXIST));

        assertThrows(ServiceException.class, () -> AssertUtils.noNullElements((List<?>) null, NOT_EXIST));
        // List.of 不允许 null 元素，含 null 场景用 Arrays.asList 构造
        assertThrows(ServiceException.class,
                () -> AssertUtils.noNullElements(java.util.Arrays.asList(1, null), NOT_EXIST));
    }

}
