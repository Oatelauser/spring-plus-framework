package io.github.oatelauser.springplus.governor.idempotent;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 容量防护（V05 / CWE-400）：内存存储满载后拒绝新 key（fail-closed），既有 key 不受影响。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0.1
 */
class InMemoryIdempotentStoreCapacityTest {

    @Test
    void fullStoreRejectsNewKeysButKeepsExisting() {
        InMemoryIdempotentStore store = new InMemoryIdempotentStore(2);
        assertTrue(store.setIfAbsent("k1", "1", Duration.ofSeconds(60)));
        assertTrue(store.setIfAbsent("k2", "2", Duration.ofSeconds(60)));

        // 满载：新 key 占位失败（等价重复提交语义，fail-closed 而非异常）
        assertFalse(store.setIfAbsent("k3", "3", Duration.ofSeconds(60)));

        // 既有 key 语义不变
        assertEquals("1", store.get("k1"));
        assertFalse(store.setIfAbsent("k1", "9", Duration.ofSeconds(60)));

        // put 新 key 在满载时明确抛出
        assertThrows(InMemoryIdempotentStore.StoreFullException.class,
                () -> store.put("k4", "4", Duration.ofSeconds(60)));

        // 释放后恢复接受
        store.delete("k2");
        assertTrue(store.setIfAbsent("k5", "5", Duration.ofSeconds(60)));
    }

    @Test
    void expiredEntriesAreSweptBeforeRejecting() throws InterruptedException {
        InMemoryIdempotentStore store = new InMemoryIdempotentStore(1);
        assertTrue(store.setIfAbsent("old", "1", Duration.ofMillis(60)));
        Thread.sleep(140);

        // 过期条目在满载判定时被清理腾挪，新 key 可写入
        assertTrue(store.setIfAbsent("new", "2", Duration.ofSeconds(60)));
        assertEquals("2", store.get("new"));
    }

}
