package io.github.oatelauser.springplus.web.response;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link PageResponse} 工厂方法：全字段、请求字段 null 回退、简写。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0
 */
class PageResponseTest {

    @Test
    void okWithRequestCarriesPagingFields() {
        BasePageRequest request = new BasePageRequest();
        request.setPageNum(2);
        request.setPageSize(20);

        PageResponse<String> response = PageResponse.ok(request, 57, List.of("a"));

        assertEquals(CommonStatus.SUCCESS.getCode(), response.getCode());
        assertEquals(CommonStatus.SUCCESS.getMessage(), response.getMessage());
        Page<String> page = response.getData();
        assertEquals(List.of("a"), page.getItem());
        assertEquals(57, page.getTotal());
        assertEquals(2, page.getPageNum());
        assertEquals(20, page.getPageSize());
        assertEquals(3, page.getTotalPage());
    }

    @Test
    void nullRequestFieldsFallBackToDefaults() {
        BasePageRequest request = new BasePageRequest();
        request.setPageNum(null);
        request.setPageSize(null);

        PageResponse<String> response = PageResponse.ok(request, 0, List.of());

        assertEquals(1, response.getData().getPageNum(), "null 回落默认页码 1");
        assertEquals(10, response.getData().getPageSize(), "null 回落默认页大小 10");
    }

    @Test
    void shorthandOkZeroesPagingFields() {
        PageResponse<Integer> response = PageResponse.ok(List.of(1, 2), 2);
        assertEquals(0, response.getData().getPageNum());
        assertEquals(0, response.getData().getPageSize());
        assertEquals(0, response.getData().getTotalPage(),
                "pageSize=0 时防除零保护令 totalPage=0（简写工厂的有意行为）");
        assertEquals(2, response.getData().getTotal());
    }

}
