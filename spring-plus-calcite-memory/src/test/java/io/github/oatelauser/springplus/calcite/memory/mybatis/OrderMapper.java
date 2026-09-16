package io.github.oatelauser.springplus.calcite.memory.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 注解式 Mapper：演示 {@code @Select} + {@code #{}} 参数绑定与 {@code mapUnderscoreToCamelCase} 自动映射。
 */
public interface OrderMapper {

    @Select("SELECT id, user_id, amount, status FROM orders WHERE status = #{status} ORDER BY id")
    List<OrderView> findByStatus(@Param("status") String status);

    @Select("SELECT id, user_id, amount, status FROM orders WHERE id = #{id}")
    OrderView findById(@Param("id") Integer id);
}
