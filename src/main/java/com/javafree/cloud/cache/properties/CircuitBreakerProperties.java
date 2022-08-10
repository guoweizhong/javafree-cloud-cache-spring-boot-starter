package com.javafree.cloud.cache.properties;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import lombok.Data;

import java.time.Duration;
/**
 * @version V1.0
 * @Description: 熔断器相关参数配置
 * @Author gwz  gwz126@126.com
 * @Date 2022/8/2 18:08
 */
@Data
public class CircuitBreakerProperties {
  /** 禁止进一步调用 Redis 的调用失败百分比 */
  private int failureRateThreshold = 25;

  /** 禁止进一步调用 Redis 的慢速调用百分比 */
  private int slowCallRateThreshold = 25;

  /** 定义 Redis 调用被认为是慢的持续时间  单位毫秒 */
  private Duration slowCallDurationThreshold = Duration.ofMillis(250);

  /** 用于连通性分析的滑动窗口类型 */
  private SlidingWindowType slidingWindowType = SlidingWindowType.COUNT_BASED;

  /** 用于测试断路器关闭时后端是否响应的 Redis 调用量 */
  private int permittedNumberOfCallsInHalfOpenState =
          (int) (Duration.ofSeconds(5).toNanos() / slowCallDurationThreshold.toNanos());

  /** 在关闭断路器之前等待的时间量，0 - 等待所有允许的调用。*/
  private Duration maxWaitDurationInHalfOpenState =
          slowCallDurationThreshold.multipliedBy(permittedNumberOfCallsInHalfOpenState);

  /** Redis调用分析的滑动窗口大小（调用/秒） */
  private int slidingWindowSize = permittedNumberOfCallsInHalfOpenState * 2;

  /** 在计算错误或慢速调用率之前所需的最小调用次数 */
  private int minimumNumberOfCalls = permittedNumberOfCallsInHalfOpenState / 2;

  /** 在允许 Redis 调用测试后端连接之前需要等待时间。 */
  private Duration waitDurationInOpenState =
          slowCallDurationThreshold.multipliedBy(minimumNumberOfCalls);
}
