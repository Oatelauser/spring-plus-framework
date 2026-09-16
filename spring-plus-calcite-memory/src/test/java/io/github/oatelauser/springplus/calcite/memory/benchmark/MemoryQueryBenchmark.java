package io.github.oatelauser.springplus.calcite.memory.benchmark;

import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQueryEngine;
import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQuerySession;
import io.github.oatelauser.springplus.calcite.memory.result.QueryResult;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * P6-1 JMH 基准：本组件（Calcite）vs Java Stream/手写 Hash Join 横向对比。
 *
 * <p>覆盖场景（{@code 07-performance-benchmark-plan.md}）：</p>
 * <ul>
 *   <li>S1 单表过滤：{@code WHERE} 等值</li>
 *   <li>S3 分组聚合：{@code GROUP BY + SUM/COUNT}</li>
 *   <li>S4 两表 JOIN：{@code INNER JOIN ON}</li>
 * </ul>
 * <p>规模：1,000 / 10,000 / 100,000。Calcite 路径复用同一 Session（S6 重复执行/计划缓存收益）。</p>
 *
 * <p>运行：{@code mvn -f apartment-framework/spring-calcite-memory-framework/pom.xml test-compile}
 * 后执行 {@link BenchmarkRunner#main}。</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
public class MemoryQueryBenchmark {

    /** 注册用窄表 POJO：final 字段（与框架约定一致），包装类型。 */
    static final class Order {
        final Long id;
        final String status;
        final Integer amount;
        final Long itemId;

        Order(Long id, String status, Integer amount, Long itemId) {
            this.id = id;
            this.status = status;
            this.amount = amount;
            this.itemId = itemId;
        }
    }

    static final class Item {
        final Long id;
        final String name;

        Item(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Param({"1000", "10000", "100000"})
    public int scale;

    private MemoryQueryEngine engine;
    private MemoryQuerySession session;
    private List<Order> orders;
    private Map<Long, Item> itemIndex;

    @Setup
    public void setup() {
        int itemCount = Math.max(10, scale / 10);
        List<Item> items = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; i++) {
            items.add(new Item((long) i, "item-" + i));
        }
        itemIndex = new HashMap<>(itemCount * 2);
        for (Item it : items) {
            itemIndex.put(it.id, it);
        }

        orders = new ArrayList<>(scale);
        for (int i = 0; i < scale; i++) {
            orders.add(new Order((long) i,
                    (i & 1) == 0 ? "PAID" : "PENDING",
                    i % 100,
                    (long) (i % itemCount)));
        }

        engine = MemoryQueryEngine.create();
        session = engine.openSession();
        session.register("orders", orders, Order.class);
        session.register("items", items, Item.class);

        // 正确性校验：不以吞吐换正确性
        QueryResult sanity = session.query("SELECT count(*) AS c FROM orders");
        long registered = ((Number) sanity.row(0).getObject(0)).longValue();
        if (registered != scale) {
            throw new IllegalStateException("注册行数校验失败: 期望 " + scale + "，实际 " + registered);
        }
    }

    @TearDown
    public void tearDown() {
        if (session != null) {
            session.close();
        }
        if (engine != null) {
            engine.close();
        }
    }

    // ---------- S1 单表过滤 ----------

    @Benchmark
    public void calciteFilter(Blackhole bh) {
        QueryResult r = session.query("SELECT id, amount FROM orders WHERE status = 'PAID'");
        bh.consume(r.rowCount());
        if (r.rowCount() > 0) {
            bh.consume(r.row(0).getLong("id"));
        }
    }

    @Benchmark
    public void streamFilter(Blackhole bh) {
        List<Order> r = orders.stream()
                .filter(o -> "PAID".equals(o.status))
                .toList();
        bh.consume(r.size());
        if (!r.isEmpty()) {
            bh.consume(r.getFirst().id);
        }
    }

    // ---------- S3 分组聚合 ----------

    @Benchmark
    public void calciteAggregate(Blackhole bh) {
        QueryResult r = session.query(
                "SELECT status, count(*) AS c, sum(amount) AS s FROM orders GROUP BY status");
        bh.consume(r.rowCount());
        for (int i = 0; i < r.rowCount(); i++) {
            bh.consume(r.row(i).getObject(0));
            bh.consume(r.row(i).getObject(2));
        }
    }

    @Benchmark
    public void streamAggregate(Blackhole bh) {
        Map<String, Integer> r = orders.stream().collect(
                Collectors.groupingBy(o -> o.status, Collectors.summingInt(o -> o.amount)));
        bh.consume(r.size());
        r.values().forEach(bh::consume);
    }

    // ---------- S4 两表 JOIN ----------

    @Benchmark
    public void calciteJoin(Blackhole bh) {
        QueryResult r = session.query(
                "SELECT o.id, i.name FROM orders o JOIN items i ON o.item_id = i.id WHERE o.status = 'PAID'");
        bh.consume(r.rowCount());
        if (r.rowCount() > 0) {
            bh.consume(r.row(0).getObject(1));
        }
    }

    @Benchmark
    public void hashJoin(Blackhole bh) {
        long count = 0;
        for (Order o : orders) {
            if ("PAID".equals(o.status)) {
                Item it = itemIndex.get(o.itemId);
                if (it != null) {
                    count++;
                    bh.consume(it.name);
                }
            }
        }
        bh.consume(count);
    }
}
