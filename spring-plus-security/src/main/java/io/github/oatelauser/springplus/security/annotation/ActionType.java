package io.github.oatelauser.springplus.security.annotation;

/**
 * 资源的操作权限
 *
 * @author <a href="mailto:545896770@qq.com">DearYang</a>
 * @date 2023-05-08
 * @since 1.0
 */
public interface ActionType {

    /**
     * 读权限：资源列表
     */
    String LIST = "list";

    /**
     * 读权限：单个资源
     */
    String GET = "get";

    /**
     * 写权限：新增资源
     */
    String CREATE = "create";

    /**
     * 写权限：更新资源
     */
    String UPDATE = "update";

    /**
     * 写权限：删除
     */
    String DELETE = "delete";

    /**
     * 允许访问
     */
    String ALLOW = "allow";

}
