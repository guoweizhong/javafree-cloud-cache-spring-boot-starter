package com.javafree.cloud.cache.support;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.javafree.cloud.cache.enums.ExpireMode;
import com.javafree.cloud.cache.properties.MultiLevelCacheProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.vavr.CheckedFunction0;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @version V1.0
 * @Description: RedisCache 的扩展
 * @Author gwz  gwz126@126.com
 * @Date 2022/8/2 13:43
 */
@Slf4j
public class JavafreeMultiLevelCache extends RedisCache {

    // 报错信息
    private static final String NO_REDIS_CONNECTION =
            "Redis connection factory was not found for RedisCacheWriter";
    private static final String LOCK_WAS_NOT_INITIALIZED = "Lock was not initialized";

    // 为ReentrantLocks 提供本地不可覆盖属性，以保持操作的原子性
    private static final Object CACHE_WIDE_LOCK_OBJECT = new Object();

    protected final MultiLevelCacheProperties properties;
    protected final Cache<Object, Object> localCache;
    protected final Cache<Object, ReentrantLock> locks;

    protected final CircuitBreaker cacheCircuitBreaker;

    private final RedisTemplate<Object, Object> redisTemplate;
    public JavafreeMultiLevelCache(
            String name,
            MultiLevelCacheProperties properties,
            RedisTemplate<Object, Object> redisTemplate,
            Cache<Object, Object> localCache,
            CircuitBreaker cacheCircuitBreaker) {
        this(
                name,
                properties,
                RedisCacheWriter.nonLockingRedisCacheWriter(
                        Objects.requireNonNull(redisTemplate.getConnectionFactory(), NO_REDIS_CONNECTION)),
                redisTemplate,
                localCache,
                cacheCircuitBreaker);
    }


    public JavafreeMultiLevelCache(
            String name,
            MultiLevelCacheProperties properties,
            RedisCacheWriter redisCacheWriter,RedisTemplate<Object, Object> redisTemplate,
            Cache<Object, Object> localCache,
            CircuitBreaker cacheCircuitBreaker) {
        super(name,redisCacheWriter,properties.toRedisCacheConfiguration());
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.localCache = localCache;
        // 根据配置创建Caffeine builder
        Caffeine<Object, Object> builder = Caffeine.newBuilder();
        builder.initialCapacity(properties.getLocal().getInitialCapacity());
        builder.maximumSize(properties.getLocal().getMaxSize());
        //使用软引用存储value,当垃圾收集器需要释放内存时驱逐
        builder.softValues();
        if (ExpireMode.WRITE.equals(properties.getLocal().getExpireMode())) {
            builder.expireAfterWrite(properties.getLocal().getExpireAfterWrite());
        } else if (ExpireMode.ACCESS.equals(properties.getLocal().getExpireMode())) {
            builder.expireAfterAccess(properties.getLocal().getExpireAfterAccess());
        }else {

        }
        // 根据Caffeine builder创建 Cache 对象
        this.locks = builder.build();
        this.cacheCircuitBreaker = cacheCircuitBreaker;

    }

    /**
     * 获得本地Caffeine缓存
     * @return
     */
   public Cache<Object, Object> getLocalCache() {
        return localCache;
    }
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T nativeGet(@NonNull Object key) {
        return (T) callRedis(() -> super.get(key, () -> null)).get();
    }

    public void nativePut(@NonNull Object key, @Nullable Object value) {
        callRedis(() -> super.put(key, value));
    }

    /**
     * 在底层存储中执行实际查找
     * 如果本地缓存没有映射，我们不允许存储 null 值
     * 指定键我们使用断路器和错误处理逻辑查询 Redis。如果 Redis 包含
     * 请求的映射，值将保存在本地缓存中。如果 Redis 不可用 ,将被退回。
     * @param key  关联值的键
     * @return  返回key的值，如果没有，则为null
     */

    @Override
    public Object lookup(@NonNull Object key) {
        final String localKey = convertKey(key);
        Object localValue = localCache.getIfPresent(localKey);

        if (localValue == null) {
            return callRedis(() -> super.lookup(key))
                    .andThen(value -> localCache.put(localKey, value))
                    .recover(e -> null)
                    .get();
        }

        return localValue;
    }

    /**
     * 返回此缓存映射指定键的值，从 valueLoader 获取该值
     * 如果 Redis 无法查询，{@code valueLoader} 仍然会被执行并且 value 会被
     * 存储在本地缓存中。
     * @param key  关联值的键
     * @param valueLoader
     * @param <T>
     * @return  返回指定键映射到的值
     */
    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public synchronized <T> T get(@NonNull Object key, @NonNull Callable<T> valueLoader) {
        Object result = lookup(key);
        if (result != null) return (T) result;

        final String localKey = convertKey(key);
        return callRedis(() -> super.get(key, valueLoader))
                .andThen(value -> localCache.put(localKey, value))
                .recover(
                        e -> {
                            try {
                                T value = valueLoader.call();
                                localCache.put(localKey, value);
                                return value;
                            } catch (Exception recoverException) {
                                throw new ValueRetrievalException(key, valueLoader, recoverException);
                            }
                        })
                .get();
    }

    /**
     * 如果值为 {@code null} 指定的键将被删除
     * @param key
     * @param value
     */
    @Override
    public void put(@NonNull Object key, @Nullable Object value) {
        if (value == null) {
            evict(key);
            return;
        }

        localCache.put(convertKey(key), value);
        callRedis(() -> super.put(key, value));
    }

    /**
     * 如果未设置，则以原子方式将指定值与此缓存中的指定键关联
     * 如果值为 {@code null} 指定的键将被删除。
     * @param key
     * @param value
     * @return
     */

    @Override
    public ValueWrapper putIfAbsent(@NonNull Object key, @Nullable Object value) {
        if (value == null) {
            evict(key);
            return null;
        }

        final ReentrantLock lock = makeLock(key);

        try {
            lock.lock();

            Object existingValue = lookup(key);
            if (existingValue == null) {
                localCache.put(convertKey(key), value);
                callRedis(() -> super.putIfAbsent(key, value));
                return null;
            } else {
                return new SimpleValueWrapper(existingValue);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 如果此缓存存在，则从此缓存中逐出此键的映射。
     * @param key
     */
    @Override
    public void evict(@NonNull Object key) {
        sendViaRedis(localEvict(key));
    }

    /**
     * Redis Pub/Sub 监听器的 {@link #evict(Object)} 方法的本地副本以避免无限循环消息
     * @param key
     * @return
     */
    public String localEvict(@NonNull Object key) {
        final String localKey = convertKey(key);
        localCache.invalidate(localKey);
        callRedis(() -> super.evict(key));
        return localKey;
    }

    /**
     * 如果此缓存存在，则从此缓存中逐出此键的映射
     * @param key
     * @return
     */
    @Override
    public boolean evictIfPresent(@NonNull Object key) {
        final ReentrantLock lock = makeLock(key);

        try {
            lock.lock();

            final String localKey = convertKey(key);
            boolean haveLocalMapping = localCache.getIfPresent(localKey) != null;

            localCache.invalidate(localKey);
            callRedis(() -> super.evict(key));
            sendViaRedis(localKey);

            return haveLocalMapping;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 通过删除所有映射来清除缓存
     */
    @Override
    public void clear() {
        localClear();
        sendViaRedis(null);
    }

    /**
     * 用于 Redis Pub/Sub 侦听器的 {@link #clear()} 方法的本地副本，以避免无限消息循环
     */
    public void localClear() {
        localCache.invalidateAll();
        callRedis(super::clear);
    }

    /**
     * 通过删除所有映射使缓存无效
     * @return
     */
    @Override
    public boolean invalidate() {
        final ReentrantLock lock = makeLock(CACHE_WIDE_LOCK_OBJECT);

        try {
            lock.lock();

            boolean hadLocalMappings = localCache.estimatedSize() > 0;

            localCache.invalidateAll();
            callRedis(super::clear);
            sendViaRedis(null);

            return hadLocalMappings;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 执行Redis操作方法
     * @param call
     */
    private void callRedis(@NonNull Runnable call) {
        Try.runRunnable(cacheCircuitBreaker.decorateRunnable(call));
    }

    /**
     * 执行Redis操作方法
     * @param call
     * @param <T>
     * @return
     */
    private <T> Try<T> callRedis(@NonNull CheckedFunction0<T> call) {
        return Try.of(cacheCircuitBreaker.decorateCheckedSupplier(call));
    }

    private void sendViaRedis(@Nullable String key) {
        Try.runRunnable(
                cacheCircuitBreaker.decorateRunnable(
                        () ->
                                redisTemplate.convertAndSend(
                                        properties.getTopic(), new CacheEvictMessage(getName(), key))));
    }

    /**
     * 获得一个用于同步操作对象的锁
     * @param key
     * @return
     */
    @NonNull
    private ReentrantLock makeLock(@NonNull Object key) {
        return Objects.requireNonNull(
                locks.get(key, o -> new ReentrantLock()), LOCK_WAS_NOT_INITIALIZED);
    }
}
