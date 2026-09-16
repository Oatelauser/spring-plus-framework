package io.github.oatelauser.springplus.web.response;

import java.util.List;

import static io.github.oatelauser.springplus.web.response.CommonStatus.SUCCESS;

/**
 * 分页响应
 *
 * <pre>{@code
 * PageResponse.ok(request, list, total)   // 全字段：页信息来自 BasePageRequest
 * PageResponse.ok(list, total)            // 简写：pageNum/pageSize 置 0，由调用方上下文补
 * }</pre>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0
 */
public class PageResponse<T> extends SimpleResponse<Page<T>> {

    /**
     * 分页响应（页信息来自分页请求；请求字段为 null 时回落默认值 1/10）
     */
    public static <T> PageResponse<T> ok(BasePageRequest request, List<T> item, long totalCount) {
        int pageNum = request.getPageNum() != null ? request.getPageNum() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;
        return ok(pageNum, pageSize, item, totalCount);
    }

    /**
     * 分页响应（全字段）
     */
    public static <T> PageResponse<T> ok(long pageNum, long pageSize, List<T> item, long totalCount) {
        PageResponse<T> response = new PageResponse<>();
        response.setCode(SUCCESS.code);
        response.setMessage(SUCCESS.message);
        response.setData(Page.of(pageNum, pageSize, item, totalCount));
        return response;
    }

    /**
     * 分页响应（简写：pageNum/pageSize 置 0）
     */
    public static <T> PageResponse<T> ok(List<T> item, long totalCount) {
        return ok(0, 0, item, totalCount);
    }

}
