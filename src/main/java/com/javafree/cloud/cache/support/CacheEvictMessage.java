package com.javafree.cloud.cache.support;

/**
 * @version V1.0
 * @Description: 用于删除缓存的消息对象
 * @Author gwz  gwz126@126.com
 * @Date 2022/8/2 17:10
 */

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheEvictMessage implements Serializable {
  private String cacheName;
  private String entryKey;
}