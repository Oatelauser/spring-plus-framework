package io.github.oatelauser.springplus.web.response;

import java.util.List;

/**
 * 分页数据载体
 * <p>
 * 字段命名约定：{@code item}（当前页记录列表，单数命名是项目约定）、{@code totalCount}（总记录数）、
 * {@code pageNum}（当前页码）、{@code pageSize}（每页条数）、{@code totalPage}（总页数，工厂内自动计算）。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0
 */
public class Page<T> {

    /**
     * 当前页记录列表
     */
    private List<T> item;

    /**
     * 总记录数
     */
    private long totalCount;

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

    public static <T> Page<T> of(long pageNum, long pageSize, List<T> item, long totalCount) {
        Page<T> page = new Page<>();
        page.item = item;
        page.totalCount = totalCount;
        page.pageNum = pageNum;
        page.pageSize = pageSize;
        page.totalPage = pageSize <= 0 ? 0 : (totalCount + pageSize - 1) / pageSize;
        return page;
    }

    public List<T> getItem() {
        return item;
    }

    public void setItem(List<T> item) {
        this.item = item;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public long getPageNum() {
        return pageNum;
    }

    public void setPageNum(long pageNum) {
        this.pageNum = pageNum;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    public long getTotalPage() {
        return totalPage;
    }

    public void setTotalPage(long totalPage) {
        this.totalPage = totalPage;
    }

}
