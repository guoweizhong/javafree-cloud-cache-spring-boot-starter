package com.javafree.cloud.cache.enums;

/**
 * @version V1.0
 * @Description: caffein缓存过期算法配置
 * ACCESS对应expireAfterAccess 表示上次读写超过一定时间后过期，
 * WRITE 对应expireAfterWrite 表示上次创建或更新超过一定时间后过期
 * @Author gwz  gwz126@126.com
 * @Date 2022/8/2 15:34
 */

public enum ExpireMode {
    /**
     * RANDOM expireAfter 允许复杂的表达式，过期时间可以通过自定义的随机算法获得
     */
    RANDOM("过期时间随机算法"),
    /**
     * 最后一次写入后经过固定时间过期
     */
    WRITE("最后一次写入后到期失效"),

    /**
     * 最后一次写入或访问后经过固定时间过期
     */
    ACCESS("最后一次访问后到期失效");

    private String label;

    ExpireMode(String label) {
        this.label = label;
    }
}
