package io.github.oatelauser.springplus.calcite.memory.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * XML Mapper 接口：SQL 定义在 {@code OrderXmlMapper.xml}，namespace 与本接口全限定名一致以自动绑定。
 * 演示 {@code <resultMap>}、{@code <if>} 动态 SQL、{@code <foreach>} IN 列表。
 */
public interface OrderXmlMapper {

    /** 按金额区间过滤（动态 {@code <if>}）。min/max 均可为 null。 */
    List<OrderView> findByConditions(@Param("minAmount") Integer minAmount, @Param("maxAmount") Integer maxAmount);

    /** 按 id 列表 IN 查询（{@code <foreach>}）。 */
    List<OrderView> findByIds(@Param("ids") List<Integer> ids);
}
