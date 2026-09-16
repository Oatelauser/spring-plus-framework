package io.github.oatelauser.springplus.governor.idempotent;

import java.time.Duration;

/** 幂等键存储 SPI。 */
public interface IdempotentStore {

    boolean setIfAbsent(String key, String value, Duration ttl);

    String get(String key);

    void put(String key, String value, Duration ttl);

    void delete(String key);
}
