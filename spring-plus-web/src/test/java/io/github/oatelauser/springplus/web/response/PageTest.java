package io.github.oatelauser.springplus.web.response;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link Page} 的 totalPage 计算与边界。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0
 */
class PageTest {

    @Test
    void totalPageRoundsUp() {
        Page<String> page = Page.of(1, 10, List.of("a"), 57);
        assertEquals(6, page.getTotalPage(), "57 条 / 每页 10 → 6 页（向上取整）");
    }

    @Test
    void totalPageExactDivision() {
        assertEquals(2, Page.of(1, 10, List.of(), 20).getTotalPage());
        assertEquals(1, Page.of(1, 10, List.of(), 10).getTotalPage());
    }

    @Test
    void zeroTotalCountYieldsZeroPages() {
        Page<String> page = Page.of(1, 10, List.of(), 0);
        assertEquals(0, page.getTotalPage());
        assertEquals(0, page.getTotalCount());
    }

    @Test
    void nonPositivePageSizeGuarded() {
        assertEquals(0, Page.of(1, 0, List.of(), 57).getTotalPage(), "pageSize=0 防除零");
        assertEquals(0, Page.of(1, -1, List.of(), 57).getTotalPage());
    }

    @Test
    void fieldsRoundTrip() {
        Page<Integer> page = Page.of(3, 20, List.of(1, 2), 41);
        assertEquals(List.of(1, 2), page.getItem());
        assertEquals(41, page.getTotalCount());
        assertEquals(3, page.getPageNum());
        assertEquals(20, page.getPageSize());
        assertEquals(3, page.getTotalPage());
    }

}
