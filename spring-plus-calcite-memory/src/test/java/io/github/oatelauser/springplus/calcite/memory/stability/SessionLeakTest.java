package io.github.oatelauser.springplus.calcite.memory.stability;

import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQueryEngine;
import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQuerySession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * P6-3 泄漏测试：10,000 次 Session 创建/关闭，GC 后首个 Session 应被回收（无强引用残留）。
 *
 * <p>对应 {@code 08-test-plan.md} 第 7 节「10,000 次 Session 创建/关闭 | GC 后堆无增长」。</p>
 *
 * <p>测试仅做 open + close（不执行查询，避免 QueryCanceller 句柄路径引入额外引用），
 * 聚焦 CalciteConnection 的生命周期回收。若存在连接/Session 泄漏，10,000 次迭代会 OOM 或首个 Session 无法回收。</p>
 */
@Tag("stress")
class SessionLeakTest {

    /** 10,000 次创建/关闭，无异常完成即证明资源受控；首个 Session 关闭后应可被 GC 回收。 */
    @Test
    void tenThousandSessionsCreateCloseNoLeak() throws InterruptedException {
        WeakReference<MemoryQuerySession> firstSessionRef = new WeakReference<>(null);
        try (MemoryQueryEngine engine = MemoryQueryEngine.create()) {
            for (int i = 0; i < 10_000; i++) {
                MemoryQuerySession session = engine.openSession();
                if (i == 0) {
                    firstSessionRef = new WeakReference<>(session);
                }
                session.close();
            }
        }
        // 引擎与所有临时强引用已释放，触发 GC 期望首个 Session 被回收
        for (int attempt = 0; attempt < 6 && firstSessionRef.get() != null; attempt++) {
            System.gc();
            Thread.sleep(50L);
        }
        assertNull(firstSessionRef.get(),
                "首个 Session 关闭后应被 GC 回收，疑似存在引用泄漏（QueryCanceller / 连接未释放）");
    }
}
