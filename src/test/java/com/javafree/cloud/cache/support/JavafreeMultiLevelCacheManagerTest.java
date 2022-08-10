package com.javafree.cloud.cache.support;

import com.javafree.cloud.cache.config.JavafreeMultilevelCacheAutoConfiguration;
import com.javafree.cloud.cache.properties.MultiLevelCacheProperties;
import com.javafree.cloud.cache.support.JavafreeMultiLevelCacheManager.RandomizedLocalExpiryOnWrite;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringRunner;

@Slf4j
@ActiveProfiles("test")
@SpringBootTest(
        classes = {
                JavafreeMultilevelCacheAutoConfiguration.class,
                RedisAutoConfiguration.class,
                CacheAutoConfiguration.class
        })
@RunWith(SpringRunner.class)
@ExtendWith(SpringExtension.class)
class JavafreeMultiLevelCacheManagerTest {
    @Autowired
    JavafreeMultiLevelCacheManager cacheManager;

    @Test
    void cacheNamesTest() {
        final String key = "cacheNamesTest";

        Assertions.assertDoesNotThrow(
                () -> cacheManager.getCache(key), "Cache should be automatically created upon request");
        Assertions.assertTrue(
                cacheManager.getCacheNames().contains(key), "Cache name must be accessible");
    }

    @Nested
    class RandomizedLocalExpiryOnWriteTest {
        @Test
        void negativeTimeToLive() {
            MultiLevelCacheProperties properties =
                    new MultiLevelCacheProperties();
            properties.setTimeToLive(Duration.ofSeconds(1).negated());

            Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new RandomizedLocalExpiryOnWrite(properties),
                    "Negative TTL must throw an exception");
        }

        @Test
        void zeroTimeToLive() {
            MultiLevelCacheProperties properties =
                    new MultiLevelCacheProperties();
            properties.setTimeToLive(Duration.ZERO);

            Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new RandomizedLocalExpiryOnWrite(properties),
                    "Zero TTL must throw an exception");
        }

        @Test
        void negativeExpiryJitter() {
            MultiLevelCacheProperties properties =
                    new MultiLevelCacheProperties();
            properties.getLocal().setExpiryJitter(-1);

            Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new RandomizedLocalExpiryOnWrite(properties),
                    "Negative expiry jitter must throw an exception");
        }

        @Test
        void tooBigExpiryJitter() {
            MultiLevelCacheProperties properties =
                    new MultiLevelCacheProperties();
            properties.getLocal().setExpiryJitter(200);

            Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new RandomizedLocalExpiryOnWrite(properties),
                    "Too big expiry jitter must throw an exception");
        }
    }
}
