package org.example.ratelimiter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to apply rate limiting to a controller method.
 * Methods annotated with @RateLimit will have IP-based rate limiting applied.
 *
 * @param limit             The maximum number of requests allowed within the time window
 * @param timeWindowSeconds The time window in seconds for the rate limit
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
  /**
   * The maximum number of requests allowed within the time window.
   * Default is 60 requests.
   */
  int limit() default 60;

  /**
   * The time window in seconds during which the limit applies.
   * Default is 60 seconds (1 minute).
   */
  int timeWindowSeconds() default 60;
} 