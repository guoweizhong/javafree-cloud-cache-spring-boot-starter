package com.javafree.cloud.cache.support;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.javafree.cloud.cache.enums.ExpireMode;
import com.javafree.cloud.cache.properties.MultiLevelCacheProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @version V1.0
 * @Description: 缓存管理器
 * @Author gwz  gwz126@126.com
 * @Date 2022/8/2 17:25
 */
@Slf4j
public class JavafreeMultiLevelCacheManager implements CacheManager {
    private final Set<String> requestedCacheNames;
    private final MultiLevelCacheProperties properties;
    private final RedisTemplate<Object, Object> redisTemplate;
    private final CircuitBreaker circuitBreaker;

    private final Map<String, Cache> availableCaches;

    public JavafreeMultiLevelCacheManager(
            ObjectProvider<CacheProperties> highLevelProperties,
            MultiLevelCacheProperties properties,
            RedisTemplate<Object, Object> redisTemplate,
            CircuitBreaker circuitBreaker) {
        CacheProperties hlp = highLevelProperties.getIfAvailable();
        this.requestedCacheNames =
                hlp == null
                        ? Collections.emptySet()
                        : Collections.unmodifiableSet(new HashSet<>(hlp.getCacheNames()));

        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.circuitBreaker = circuitBreaker;

        this.availableCaches = new ConcurrentHashMap<>();

        this.requestedCacheNames.forEach(this::getCache);
    }

    MultiLevelCacheProperties getProperties() {
        return properties;
    }

    CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }


    /**
     * 获取或创建与给定名称关联的缓存
     *
     * @param name
     * @return
     */

    @Override
    public Cache getCache(@NonNull String name) {
        if (!requestedCacheNames.isEmpty() && !requestedCacheNames.contains(name)) {
            return null;
        }
        return availableCaches.computeIfAbsent(
                name,
                key -> {
                    // 根据配置创建Caffeine builder
                    Caffeine<Object, Object> builder = Caffeine.newBuilder();
                    builder.initialCapacity(properties.getLocal().getInitialCapacity());
                    builder.maximumSize(properties.getLocal().getMaxSize());
                    builder.softValues();
                    if (ExpireMode.WRITE.equals(properties.getLocal().getExpireMode())) {
                        builder.expireAfterWrite(properties.getLocal().getExpireAfterWrite());
                    } else if (ExpireMode.ACCESS.equals(properties.getLocal().getExpireMode())) {
                        builder.expireAfterAccess(properties.getLocal().getExpireAfterAccess());
                    }else {
                        //expireAfter 允许复杂的表达式，过期时间可以通过RandomizedLocalExpiryOnWrite 计算获得。
                        builder.expireAfter(new RandomizedLocalExpiryOnWrite(properties));
                    }
                    return new JavafreeMultiLevelCache(
                            key,
                            properties,
                            redisTemplate,
                            builder.build(),
                            circuitBreaker);
                });
    }

    /**
     * 获取此管理器已知的缓存名称的集合
     *
     * @return
     */
    @Override
    public @NonNull Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(availableCaches.keySet());
    }

    /**
     * 为本地实体启用写入时随机到期的到期策略
     */
    static class RandomizedLocalExpiryOnWrite implements Expiry<Object, Object> {

        private final Random random;
        private final Duration timeToLive;
        private final double expiryJitter;

        public RandomizedLocalExpiryOnWrite(
                @NonNull MultiLevelCacheProperties properties) {
            this.random = new Random(System.currentTimeMillis());
            this.timeToLive = properties.getTimeToLive();
            this.expiryJitter = properties.getLocal().getExpiryJitter();

            if (timeToLive.isNegative()) {
                throw new IllegalArgumentException("Time to live duration must be positive");
            }

            if (timeToLive.isZero()) {
                throw new IllegalArgumentException("Time to live duration must not be zero");
            }

            if (expiryJitter < 0) {
                throw new IllegalArgumentException("Expiry jitter must be positive");
            }

            if (expiryJitter >= 100) {
                throw new IllegalArgumentException("Expiry jitter must not exceed 100 percents");
            }
        }

        @Override
        public long expireAfterCreate(@NonNull Object key, @NonNull Object value, long currentTime) {
            int jitterSign = random.nextBoolean() ? 1 : -1;
            double randomJitter = 1 + (jitterSign * (expiryJitter / 100) * random.nextDouble());
            Duration expiry = timeToLive.multipliedBy((long) (100 * randomJitter)).dividedBy(200);
            log.trace("Key {} will expire in {}", key, expiry);
            return expiry.toNanos();
        }

        @Override
        public long expireAfterUpdate(
                @NonNull Object key,
                @NonNull Object value,
                long currentTime,
                @NonNegative long currentDuration) {
            return currentDuration;
        }

        @Override
        public long expireAfterRead(
                @NonNull Object key,
                @NonNull Object value,
                long currentTime,
                @NonNegative long currentDuration) {
            return currentDuration;
        }
    }


}
