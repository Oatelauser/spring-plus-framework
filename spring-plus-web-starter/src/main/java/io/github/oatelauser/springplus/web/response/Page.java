package io.github.oatelauser.springplus.web.response;

import lombok.Data;

import java.util.List;

/**
 * 分页数据载体
 * <p>
 * 字段命名约定：{@code item}（当前页记录列表，单数命名是项目约定）、{@code total}（总记录数，字段即 JSON 键）、
 * {@code pageNum}（当前页码）、{@code pageSize}（每页条数）、{@code totalPage}（总页数，工厂内自动计算）。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0
 */
@Data
public class Page<T> {

    /**
     * 当前页记录列表
     */
    private List<T> item;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 当前页码
     */
    private long pageNum;

    /**
     * 每页条数
     */
    private long pageSize;

    /**
     * 总页数
     */
    private long totalPage;

    public static <T> Page<T> of(long pageNum, long pageSize, long total, List<T> item) {
        Page<T> page = new Page<>();
        page.item = item;
        page.total = total;
        page.pageNum = pageNum;
        page.pageSize = pageSize;
        page.totalPage = pageSize <= 0 ? 0 : (total + pageSize - 1) / pageSize;
        return page;
    }

}
