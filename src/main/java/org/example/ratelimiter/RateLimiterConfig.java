package org.example.ratelimiter;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class RateLimiterConfig {
  // Cache name constant to match the one used in RateLimiterService
  public static final String CACHE_NAME = "ipRateLimitCache";

  @Bean
  public CacheManager cacheManager() {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager(CACHE_NAME);

    cacheManager.setCaffeine(Caffeine.newBuilder()
      // We don't need expiry here since we handle it manually in the service
      // for more precise control over the time windows
      .maximumSize(10000));

    return cacheManager;
  }
} 