package com.javafree.cloud.cache.properties;

import com.javafree.cloud.cache.enums.ExpireMode;
import lombok.Data;

import java.time.Duration;

/**
 * @version V1.0
 * @Description: 本地Caffeine 配置相关属性
 * @Author gwz  gwz126@126.com
 * @Date 2022/8/2 13:47
 */
@Data
public class LocalCacheProperties {
  /** 最大缓存对象个数，超过此数量时之前放入的缓存将失效 */
  private int maxSize = 4000;

  /**
   * 本地缓存条目过期的时间偏差百分比
   * MultiLevelCacheProperties.timeToLive(redis 缓存对象默认存活时间，单位小时)
   * 计算公式为(MultiLevelCacheProperties.timeToLive / 2) * (1 ± ((expiryJitter / 100) * RNG(0, 1)))
   */
  private int expiryJitter = 50;

  /**  初始的缓存空间大小  设置内部哈希表的最小总大小。
   * 在构建时提供足够大的估计可以避免以后进行昂贵的调整大小操作，
   * 但是将此值设置得不必要的高会浪费内存 */
  private int initialCapacity = 1000;

  /**
   * 缓存失效模式
   */
  private ExpireMode expireMode = ExpireMode.RANDOM;

  /**  最后一次写入或访问后经过固定时间过期 单位秒 */
  private Duration expireAfterAccess=Duration.ofSeconds(1800L);;

  /**  最后一次写入后经过固定时间过期  单位秒 */
  private Duration expireAfterWrite=Duration.ofSeconds(1800L);

}
