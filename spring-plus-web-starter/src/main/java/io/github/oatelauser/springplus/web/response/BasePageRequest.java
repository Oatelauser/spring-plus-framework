package io.github.oatelauser.springplus.web.response;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页请求基类
 * <p>
 * 分页接口的入参继承本类即获得标准分页参数（{@code pageNum} / {@code pageSize}）。
 * {@code pageSize} 上限 500 用于防止深分页与大结果集查询。
 *
 * <pre>{@code
 * class UserPageRequest extends BasePageRequest {
 *     private String keyword;
 * }
 * }</pre>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0
 */
@Data
public class BasePageRequest {

    /**
     * 页码，从 1 开始
     */
    @Min(value = 1, message = "pageNum 必须大于等于 1")
    private Integer pageNum = 1;

    /**
     * 每页条数
     */
    @Min(value = 1, message = "pageSize 必须大于等于 1")
    @Max(value = 500, message = "pageSize 不能超过 500")
    private Integer pageSize = 10;

}
