package io.github.oatelauser.springplus.governor.idempotent;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InMemoryIdempotentStore} 的占位、过期与读写删语义。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0
 */
class InMemoryIdempotentStoreTest {

    private final InMemoryIdempotentStore store = new InMemoryIdempotentStore();

    @Test
    void setIfAbsentFirstWinsSecondLoses() {
        assertTrue(store.setIfAbsent("k", "1", Duration.ofSeconds(60)));
        assertFalse(store.setIfAbsent("k", "2", Duration.ofSeconds(60)));
        assertEquals("1", store.get("k"));
    }

    @Test
    void entryExpiresAfterTtl() throws InterruptedException {
        assertTrue(store.setIfAbsent("k", "1", Duration.ofMillis(80)));
        Thread.sleep(160);
        assertNull(store.get("k"), "TTL 过期后条目应不可见");
        assertTrue(store.setIfAbsent("k", "1", Duration.ofSeconds(60)), "过期后可重新占位");
    }

    @Test
    void putOverwritesAndDeleteRemoves() {
        store.put("k", "a", Duration.ofSeconds(60));
        assertEquals("a", store.get("k"));

        store.put("k", "b", Duration.ofSeconds(60));
        assertEquals("b", store.get("k"));

        store.delete("k");
        assertNull(store.get("k"));
        assertTrue(store.setIfAbsent("k", "c", Duration.ofSeconds(60)));
    }

}
