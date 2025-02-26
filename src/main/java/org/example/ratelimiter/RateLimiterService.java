package org.example.ratelimiter;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service responsible for enforcing rate limits
 */
@Service
public class RateLimiterService {
  private final CacheManager cacheManager;
  private static final int DEFAULT_LIMIT = 60;
  private static final int DEFAULT_TIME_WINDOW_SECONDS = 60;

  // Map to store expiry times for different rate limits
  private final ConcurrentHashMap<String, Long> expiryTimes = new ConcurrentHashMap<>();

  // Map to store all registered rate limits (endpointPath -> RateLimitInfo)
  private final ConcurrentHashMap<String, RateLimitInfo> rateLimitRegistry = new ConcurrentHashMap<>();

  public RateLimiterService(CacheManager cacheManager) {
    this.cacheManager = cacheManager;
  }

  /**
   * Registers a rate-limited endpoint in the registry
   */
  public void registerRateLimit(String endpointPath, int limit, int timeWindowSeconds, String description) {
    rateLimitRegistry.put(endpointPath, new RateLimitInfo(limit, timeWindowSeconds, description));
  }

  /**
   * Checks if the request from the given IP should be allowed based on default rate limits
   */
  public boolean allowRequest(String ipAddress) {
    return allowRequest(ipAddress, DEFAULT_LIMIT, DEFAULT_TIME_WINDOW_SECONDS);
  }

  /**
   * Checks if the request from the given IP should be allowed based on specified rate limits
   */
  public boolean allowRequest(String ipAddress, int limit, int timeWindowSeconds) {
    Cache cache = cacheManager.getCache(RateLimiterConfig.CACHE_NAME);

    if (cache == null) {
      return true;
    }

    String cacheKey = buildCacheKey(ipAddress, limit, timeWindowSeconds);
    checkAndResetExpiredCounter(cache, cacheKey);

    Cache.ValueWrapper valueWrapper = cache.get(cacheKey);
    AtomicInteger counter;

    if (valueWrapper == null) {
      // First request in this time window
      counter = new AtomicInteger(1);
      cache.put(cacheKey, counter);
      updateExpiryTime(cacheKey, timeWindowSeconds);

      return true;
    } else {
      // Subsequent request in this time window
      counter = (AtomicInteger) valueWrapper.get();
      int currentCount = counter.incrementAndGet();

      // If this is a new time window, update the expiry time
      if (!expiryTimes.containsKey(cacheKey)) {
        updateExpiryTime(cacheKey, timeWindowSeconds);
      }

      return currentCount <= limit;
    }
  }

  /**
   * Gets the current count of requests for the given IP address
   */
  public int getCurrentCount(String ipAddress, int limit, int timeWindowSeconds) {
    Cache cache = cacheManager.getCache(RateLimiterConfig.CACHE_NAME);

    if (cache == null) {
      return 0;
    }

    String cacheKey = buildCacheKey(ipAddress, limit, timeWindowSeconds);
    Cache.ValueWrapper valueWrapper = cache.get(cacheKey);

    return valueWrapper == null ? 0 : ((AtomicInteger) valueWrapper.get()).get();
  }

  /**
   * Gets the current count using default limits
   */
  public int getCurrentCount(String ipAddress) {
    return getCurrentCount(ipAddress, DEFAULT_LIMIT, DEFAULT_TIME_WINDOW_SECONDS);
  }

  /**
   * Gets the registry of all rate-limited endpoints
   */
  public Map<String, RateLimitInfo> getRateLimitRegistry() {
    return Collections.unmodifiableMap(rateLimitRegistry);
  }

  /**
   * Gets information about all active rate limits for a given IP address
   */
  public List<RateLimitStatus> getRateLimitStatus(String ipAddress) {
    List<RateLimitStatus> statusList = new ArrayList<>();

    // Check each registered rate limit
    for (Map.Entry<String, RateLimitInfo> entry : rateLimitRegistry.entrySet()) {
      String endpoint = entry.getKey();
      RateLimitInfo info = entry.getValue();

      int currentCount = getCurrentCount(ipAddress, info.getLimit(), info.getTimeWindowSeconds());
      long remainingRequests = Math.max(0, info.getLimit() - currentCount);

      // Calculate time remaining in the current window
      String cacheKey = buildCacheKey(ipAddress, info.getLimit(), info.getTimeWindowSeconds());
      Long expiryTime = expiryTimes.get(cacheKey);
      long timeRemainingMs = expiryTime != null ? Math.max(0, expiryTime - System.currentTimeMillis()) : 0;

      statusList.add(new RateLimitStatus(
        endpoint,
        info.getDescription(),
        info.getLimit(),
        info.getTimeWindowSeconds(),
        currentCount,
        remainingRequests,
        timeRemainingMs / 1000 // Convert to seconds
      ));
    }

    return statusList;
  }

  // Helper methods

  private String buildCacheKey(String ipAddress, int limit, int timeWindowSeconds) {
    return ipAddress + ":" + limit + ":" + timeWindowSeconds;
  }

  private void updateExpiryTime(String cacheKey, int timeWindowSeconds) {
    expiryTimes.put(cacheKey, System.currentTimeMillis() + (timeWindowSeconds * 1000L));
  }

  private void checkAndResetExpiredCounter(Cache cache, String cacheKey) {
    Long expiryTime = expiryTimes.get(cacheKey);
    long currentTime = System.currentTimeMillis();

    if (expiryTime != null && currentTime > expiryTime) {
      // Time window has expired, reset the counter
      cache.evict(cacheKey);
      expiryTimes.remove(cacheKey);
    }
  }

  /**
   * Value class to store information about a rate limit configuration
   */
  public static class RateLimitInfo {
    private final int limit;
    private final int timeWindowSeconds;
    private final String description;

    public RateLimitInfo(int limit, int timeWindowSeconds, String description) {
      this.limit = limit;
      this.timeWindowSeconds = timeWindowSeconds;
      this.description = description;
    }

    public int getLimit() {
      return limit;
    }

    public int getTimeWindowSeconds() {
      return timeWindowSeconds;
    }

    public String getDescription() {
      return description;
    }
  }

  /**
   * Value class to represent the status of a rate limit for an IP address
   */
  public static class RateLimitStatus {
    private final String endpoint;
    private final String description;
    private final int limit;
    private final int timeWindowSeconds;
    private final int currentCount;
    private final long remainingRequests;
    private final long timeRemainingSeconds;

    public RateLimitStatus(String endpoint, String description, int limit, int timeWindowSeconds,
                           int currentCount, long remainingRequests, long timeRemainingSeconds) {
      this.endpoint = endpoint;
      this.description = description;
      this.limit = limit;
      this.timeWindowSeconds = timeWindowSeconds;
      this.currentCount = currentCount;
      this.remainingRequests = remainingRequests;
      this.timeRemainingSeconds = timeRemainingSeconds;
    }

    public String getEndpoint() {
      return endpoint;
    }

    public String getDescription() {
      return description;
    }

    public int getLimit() {
      return limit;
    }

    public int getTimeWindowSeconds() {
      return timeWindowSeconds;
    }

    public int getCurrentCount() {
      return currentCount;
    }

    public long getRemainingRequests() {
      return remainingRequests;
    }

    public long getTimeRemainingSeconds() {
      return timeRemainingSeconds;
    }

    @Override
    public String toString() {
      return String.format("%s (%s): %d/%d requests, %d seconds remaining",
        endpoint, description, currentCount, limit, timeRemainingSeconds);
    }
  }
} 