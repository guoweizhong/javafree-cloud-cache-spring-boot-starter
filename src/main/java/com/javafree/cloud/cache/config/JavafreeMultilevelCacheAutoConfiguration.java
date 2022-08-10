package com.javafree.cloud.cache.config;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.javafree.cloud.cache.properties.CircuitBreakerProperties;
import com.javafree.cloud.cache.properties.MultiLevelCacheProperties;
import com.javafree.cloud.cache.support.CacheEvictMessage;
import com.javafree.cloud.cache.support.CustomKeyGenerator;
import com.javafree.cloud.cache.support.JavafreeMultiLevelCache;
import com.javafree.cloud.cache.support.JavafreeMultiLevelCacheManager;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Duration;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.metrics.cache.CacheMeterBinderProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;
/**
 * @version V1.0
 * @Description:
 * @Author gwz  gwz126@126.com
 * @Date 2022/8/2 9:35
 */

@Slf4j
@Configuration
@AutoConfigureAfter(RedisAutoConfiguration.class)
@AutoConfigureBefore(CacheAutoConfiguration.class)
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
@EnableConfigurationProperties({
        CacheProperties.class,
        MultiLevelCacheProperties.class
})
public class JavafreeMultilevelCacheAutoConfiguration {
    public static final String CACHE_REDIS_TEMPLATE_NAME = "multiLevelCacheRedisTemplate";
    public static final String CIRCUIT_BREAKER_NAME = "multiLevelCacheCircuitBreaker";
    public static final String CIRCUIT_BREAKER_CONFIGURATION_NAME =
            "multiLevelCacheCircuitBreakerConfiguration";

    /**
     * 实例化   RedisTemplate  以用于发送  CacheEvictMessage
     * @param connectionFactory
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(name = CACHE_REDIS_TEMPLATE_NAME)
    public RedisTemplate<Object, Object> multiLevelCacheRedisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<Object,Object> template = new RedisTemplate();
        template.setConnectionFactory(connectionFactory);
        // 序列化工具
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<Object>(Object.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        //解决jackson-databind 序列化漏洞
        //om.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        //改为下面
        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY);
        jackson2JsonRedisSerializer.setObjectMapper(om);

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringRedisSerializer);
        template.setValueSerializer(jackson2JsonRedisSerializer);

        template.setHashKeySerializer(jackson2JsonRedisSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);

        template.afterPropertiesSet();

        return template;
    }

    /**
     * multiLevelCacheRedisTemplate 发送删除条目的消息
     * @param highLevelCacheProperties
     * @param cacheProperties
     * @param multiLevelCacheRedisTemplate
     * @return
     */
    @Bean
    public JavafreeMultiLevelCacheManager cacheManager(
            ObjectProvider<CacheProperties> highLevelCacheProperties,
            MultiLevelCacheProperties cacheProperties,
            RedisTemplate<Object, Object> multiLevelCacheRedisTemplate) {
        CircuitBreaker circuitBreaker = cacheCircuitBreaker(cacheProperties);
        return new JavafreeMultiLevelCacheManager(
                highLevelCacheProperties, cacheProperties, multiLevelCacheRedisTemplate, circuitBreaker);
    }

    /**
     * 用于多级缓存的本地级别的缓存计绑定器
     * @return
     */
    @Bean
    @ConditionalOnBean(JavafreeMultiLevelCacheManager.class)
    @ConditionalOnClass({MeterBinder.class, CacheMeterBinderProvider.class})
    public CacheMeterBinderProvider<JavafreeMultiLevelCache> multiLevelCacheCacheMeterBinderProvider() {
        return (cache, tags) -> new CaffeineCacheMetrics(cache.getLocalCache(), cache.getName(), tags);
    }

    @Bean("customKeyGenerator")
    public KeyGenerator keyGenerator() {
        return new CustomKeyGenerator();
    }


    /**
     * 返回Redis 主题监听器，用来协调条目删除
     * @param cacheProperties
     * @param multiLevelCacheRedisTemplate
     * @param cacheManager
     * @return
     */
    @Bean
    public RedisMessageListenerContainer multiLevelCacheRedisMessageListenerContainer(
            MultiLevelCacheProperties cacheProperties,
            RedisTemplate<Object, Object> multiLevelCacheRedisTemplate,
            JavafreeMultiLevelCacheManager cacheManager) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(
                Objects.requireNonNull(multiLevelCacheRedisTemplate.getConnectionFactory()));
        container.addMessageListener(
                createMessageListener(multiLevelCacheRedisTemplate, cacheManager),
                new ChannelTopic(cacheProperties.getTopic()));
        return container;
    }

    /**
     * 用于处理 Redis 连接异常和回退以使用本地缓存的断路器
     * @param cacheProperties
     * @return
     */
    static CircuitBreaker cacheCircuitBreaker(
            MultiLevelCacheProperties cacheProperties) {
        CircuitBreakerRegistry cbr = CircuitBreakerRegistry.ofDefaults();

        if (!cbr.getConfiguration(CIRCUIT_BREAKER_CONFIGURATION_NAME).isPresent()) {
            CircuitBreakerProperties props = cacheProperties.getCircuitBreaker();
            CircuitBreakerConfig.Builder cbc = CircuitBreakerConfig.custom();
            cbc.failureRateThreshold(props.getFailureRateThreshold());
            cbc.slowCallRateThreshold(props.getSlowCallRateThreshold());
            cbc.slowCallDurationThreshold(props.getSlowCallDurationThreshold());
            cbc.permittedNumberOfCallsInHalfOpenState(props.getPermittedNumberOfCallsInHalfOpenState());
            cbc.maxWaitDurationInHalfOpenState(props.getMaxWaitDurationInHalfOpenState());
            cbc.slidingWindowType(props.getSlidingWindowType());
            cbc.slidingWindowSize(props.getSlidingWindowSize());
            cbc.minimumNumberOfCalls(props.getMinimumNumberOfCalls());
            cbc.waitDurationInOpenState(props.getWaitDurationInOpenState());

            Duration recommendedMaxDurationInOpenState =
                    cacheProperties
                            .getTimeToLive()
                            .multipliedBy(cacheProperties.getLocal().getExpiryJitter() - 100L)
                            .dividedBy(200);

            if (props.getWaitDurationInOpenState().compareTo(recommendedMaxDurationInOpenState) <= 0) {
                log.warn(
                        "Cache circuit breaker wait duration in open state {} is more than recommended value of {}, "
                                + "this can result in local cache expiry while circuit breaker is still in OPEN state.",
                        props.getWaitDurationInOpenState(),
                        recommendedMaxDurationInOpenState);
            }

            cbr.addConfiguration(CIRCUIT_BREAKER_CONFIGURATION_NAME, cbc.build());
        }

        CircuitBreaker cb =
                cbr.circuitBreaker(CIRCUIT_BREAKER_NAME, CIRCUIT_BREAKER_CONFIGURATION_NAME);
        cb.getEventPublisher()
                .onError(
                        event ->
                                log.trace(
                                        "Cache circuit breaker error occurred in " + event.getElapsedDuration(),
                                        event.getThrowable()))
                .onSlowCallRateExceeded(
                        event ->
                                log.trace(
                                        "Cache circuit breaker {} calls were slow, rate exceeded",
                                        event.getSlowCallRate()))
                .onFailureRateExceeded(
                        event ->
                                log.trace(
                                        "Cache circuit breaker {} calls failed, rate exceeded", event.getFailureRate()))
                .onStateTransition(
                        event ->
                                log.trace(
                                        "Cache circuit breaker {} state transitioned from {} to {}",
                                        event.getCircuitBreakerName(),
                                        event.getStateTransition().getFromState(),
                                        event.getStateTransition().getToState()));
        return cb;
    }


    /**
     * Redis 主题消息侦听器,用来协调条目删除
     * @param multiLevelCacheRedisTemplate
     * @param cacheManager
     * @return
     */
    private static MessageListener createMessageListener(
            RedisTemplate<Object, Object> multiLevelCacheRedisTemplate,
            JavafreeMultiLevelCacheManager cacheManager) {
        return (message, pattern) -> {
            try {
                CacheEvictMessage request =
                        (CacheEvictMessage)
                                multiLevelCacheRedisTemplate.getValueSerializer().deserialize(message.getBody());

                if (request == null) return;

                String cacheName = request.getCacheName();
                String entryKey = request.getEntryKey();

                if (!StringUtils.hasText(cacheName)) return;

                JavafreeMultiLevelCache cache = (JavafreeMultiLevelCache) cacheManager.getCache(cacheName);

                if (cache == null) return;
                log.trace("Received Redis message to evict key {} from cache {}", entryKey, cacheName);
                if (entryKey == null) cache.localClear();
                else cache.localEvict(entryKey);
            } catch (ClassCastException e) {
                log.error(
                        "Cannot cast cache instance returned by cache manager to "
                                + JavafreeMultiLevelCache.class.getName(),
                        e);
            } catch (Exception e) {
                log.debug("Unknown Redis message", e);
            }
        };
    }

}
