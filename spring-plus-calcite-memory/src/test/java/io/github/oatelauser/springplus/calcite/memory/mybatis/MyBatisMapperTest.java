package io.github.oatelauser.springplus.calcite.memory.mybatis;

import io.github.oatelauser.springplus.calcite.memory.convert.TypeConverterRegistry;
import io.github.oatelauser.springplus.calcite.memory.engine.TableBuilder;
import io.github.oatelauser.springplus.calcite.memory.registry.TableRegistry;
import io.github.oatelauser.springplus.calcite.memory.schema.MetadataAdapterRegistry;
import io.github.oatelauser.springplus.calcite.memory.table.MemorySchema;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 5.2 MyBatis Mapper 集成测试：在独立 SqlSessionFactory（{@link CalciteDataSource}）上验证
 * 注解 {@code @Select}+{@code #{}}、XML {@code <select>}+{@code <resultMap>}、动态 {@code <if>}、
 * {@code <foreach>} IN、{@code mapUnderscoreToCamelCase} 列名映射，以及与生产 Mapper 扫描的隔离。
 */
class MyBatisMapperTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() {
        MemorySchema schema = new MemorySchema();
        TableBuilder tableBuilder =
                new TableBuilder(TypeConverterRegistry.withDefaults(), MetadataAdapterRegistry.withDefaults());
        TableRegistry registry = new TableRegistry(schema, tableBuilder);
        registry.publishSnapshot("orders", orders());

        CalciteDataSource dataSource = new CalciteDataSource(schema);
        sqlSessionFactory = IsolatedSqlSessionFactoryFactory.over(dataSource)
                .addMapper(OrderMapper.class)
                .addMapperXml("mapper/OrderXmlMapper.xml")
                .build();
    }

    private static List<Map<String, Object>> orders() {
        return List.of(
            Map.of("id", 1, "user_id", 10, "amount", 100, "status", "PAID"),
            Map.of("id", 2, "user_id", 10, "amount", 200, "status", "PAID"),
            Map.of("id", 3, "user_id", 20, "amount", 50, "status", "PENDING"),
            Map.of("id", 4, "user_id", 20, "amount", 300, "status", "PAID"),
            Map.of("id", 5, "user_id", 30, "amount", 150, "status", "PAID"));
    }

    @Test
    void annotationSelectAndCamelCaseMapping() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            OrderMapper mapper = session.getMapper(OrderMapper.class);
            // status='PAID' 命中订单 1,2,4,5
            List<OrderView> paid = mapper.findByStatus("PAID");
            assertEquals(4, paid.size());

            // mapUnderscoreToCamelCase：user_id -> userId 自动映射
            OrderView first = paid.get(0);
            assertEquals(Integer.valueOf(1), first.getId());
            assertEquals(Integer.valueOf(10), first.getUserId());
            assertEquals(Integer.valueOf(100), first.getAmount());

            OrderView byId = mapper.findById(3);
            assertEquals("PENDING", byId.getStatus());
            assertEquals(Integer.valueOf(20), byId.getUserId());
        }
    }

    @Test
    void xmlResultMapAndDynamicIf() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            OrderXmlMapper mapper = session.getMapper(OrderXmlMapper.class);
            // amount 在 [100,200]：订单 1(100)、2(200)、5(150)
            List<OrderView> between = mapper.findByConditions(100, 200);
            assertEquals(3, between.size());
            assertEquals(Integer.valueOf(1), between.get(0).getId());

            // 仅下界：amount >= 250 -> 订单 4(300)
            List<OrderView> minOnly = mapper.findByConditions(250, null);
            assertEquals(1, minOnly.size());
            assertEquals(Integer.valueOf(4), minOnly.get(0).getId());

            // 无条件 -> 全部 5 行（<where> 自动省略空 WHERE）
            List<OrderView> all = mapper.findByConditions(null, null);
            assertEquals(5, all.size());
        }
    }

    @Test
    void xmlForeachInList() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            OrderXmlMapper mapper = session.getMapper(OrderXmlMapper.class);
            List<OrderView> r = mapper.findByIds(List.of(2, 4));
            assertEquals(2, r.size());
            assertEquals(Integer.valueOf(2), r.get(0).getId());
            assertEquals(Integer.valueOf(4), r.get(1).getId());
        }
    }

    @Test
    void isolatedFromProductionMapperScan() {
        Configuration cfg = sqlSessionFactory.getConfiguration();
        assertEquals(IsolatedSqlSessionFactoryFactory.ENVIRONMENT_ID, cfg.getEnvironment().getId());
        assertTrue(cfg.hasMapper(OrderMapper.class));
        assertTrue(cfg.hasMapper(OrderXmlMapper.class));

        // MyBatis 同时注册全限定名（namespace.id）与短名别名（仅 id），故按全限定名做归属断言。
        // 断言一：无任何语句落到生产 mapper 包 io.github.oatelauser.infra.** —— 证明与生产扫描隔离
        Collection<String> stmtIds = cfg.getMappedStatementNames();
        assertTrue(stmtIds.stream().noneMatch(id -> id.startsWith("io.github.oatelauser.infra.")),
                () -> "生产 mapper 泄漏: " + stmtIds);
        // 断言二：全限定名归属本测试包的语句恰好 4 条（OrderMapper 2 + OrderXmlMapper 2）
        long ours = stmtIds.stream()
                .filter(id -> id.startsWith("io.github.oatelauser.springplus.calcite.memory.mybatis."))
                .count();
        assertEquals(4L, ours);
    }
}
