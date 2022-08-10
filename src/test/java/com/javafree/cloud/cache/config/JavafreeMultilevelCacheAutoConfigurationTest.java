package com.javafree.cloud.cache.config;


import java.util.Arrays;
import java.util.stream.Stream;

import com.javafree.cloud.cache.support.JavafreeMultiLevelCacheManager;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheType;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

class JavafreeMultilevelCacheAutoConfigurationTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            UserConfigurations.of(
                                    JavafreeMultilevelCacheAutoConfiguration.class,
                                    RedisAutoConfiguration.class,
                                    CacheAutoConfiguration.class));

    @Test
    void instantiationTest() {
        runner
                .withPropertyValues("spring.cache.type=" + CacheType.REDIS.name().toLowerCase())
                .run(
                        context -> {
                            Assertions.assertThat(context)
                                    .hasBean(JavafreeMultilevelCacheAutoConfiguration.CACHE_REDIS_TEMPLATE_NAME);
                            Assertions.assertThat(context).hasSingleBean(JavafreeMultiLevelCacheManager.class);
                            Assertions.assertThat(context).hasSingleBean(RedisMessageListenerContainer.class);
                        });
    }


    @Test
    void instantiationTestWithCacheNames() {
        final String cache1 = "cache1";
        final String cache2 = "cache2";

        runner
                .withPropertyValues("spring.cache.type=" + CacheType.REDIS.name().toLowerCase())
                .withPropertyValues("spring.cache.cache-names=" + cache1)
                .run(
                        context -> {
                            Assertions.assertThat(context)
                                    .hasBean(JavafreeMultilevelCacheAutoConfiguration.CACHE_REDIS_TEMPLATE_NAME);
                            Assertions.assertThat(context).hasSingleBean(JavafreeMultiLevelCacheManager.class);
                            Assertions.assertThat(context).hasSingleBean(RedisMessageListenerContainer.class);

                            JavafreeMultiLevelCacheManager cacheManager = context.getBean(JavafreeMultiLevelCacheManager.class);
                            Assertions.assertThat(cacheManager.getCacheNames()).contains(cache1);
                            Assertions.assertThat(cacheManager.getCacheNames()).doesNotContain(cache2);

                            Assertions.assertThat(cacheManager.getCache(cache2)).isNull();
                        });
    }

    @ParameterizedTest
    @MethodSource("incorrectCacheTypes")
    void instantiationTestWithDifferentCacheTypes(CacheType cacheType) {
        runner
                .withPropertyValues("spring.cache.type=" + cacheType.name().toLowerCase())
                .run(
                        context -> {
                            Assertions.assertThat(context)
                                    .doesNotHaveBean(JavafreeMultilevelCacheAutoConfiguration.CACHE_REDIS_TEMPLATE_NAME);
                            Assertions.assertThat(context).doesNotHaveBean(JavafreeMultiLevelCacheManager.class);
                            Assertions.assertThat(context).doesNotHaveBean(RedisMessageListenerContainer.class);
                        });
    }

    static Stream<Arguments> incorrectCacheTypes() {
        return Arrays.stream(CacheType.values())
                .filter(cacheType -> !CacheType.REDIS.equals(cacheType))
                .map(Arguments::of);
    }
}