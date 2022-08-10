package com.javafree.cloud.cache.properties;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * @version V1.0
 * @Description: 用于全局配置
 * @Author gwz  gwz126@126.com
 * @Date 2022/8/2 14:06
 */
@Data
@ConfigurationProperties(prefix = "spring.cache.multilevel")
public class MultiLevelCacheProperties {

  /** redis 缓存对象默认存活时间，单位小时 */
  private Duration timeToLive = Duration.ofHours(1L);

  /** 缓存key的前缀 */
  private String keyPrefix;

  /** 缓存key是否加前缀. */
  private boolean useKeyPrefix = false;

  /** 是否存储空值，默认true，防止缓存穿透 */
   private boolean allowNullValues = true;

  /** 缓存更新时通知其他节点的 redis topic名称 */
  private String topic = "cache:multilevel:topic";


  /** 本地缓存设置部分 */
  @NestedConfigurationProperty
  private LocalCacheProperties local = new LocalCacheProperties();
  @NestedConfigurationProperty
  private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

  public RedisCacheConfiguration toRedisCacheConfiguration() {

    StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
    // 值的序列化工具
    Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<Object>(Object.class);
    ObjectMapper om = new ObjectMapper();
    om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
    om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY);
    jackson2JsonRedisSerializer.setObjectMapper(om);

    RedisCacheConfiguration configuration =RedisCacheConfiguration.defaultCacheConfig()
           .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(stringRedisSerializer))
            //配置值的序列化工具
           .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer))
           .entryTtl(timeToLive);

    if(!allowNullValues) configuration.disableCachingNullValues();
    if (useKeyPrefix) configuration.prefixCacheNameWith(keyPrefix);

    return configuration;
  }

}
