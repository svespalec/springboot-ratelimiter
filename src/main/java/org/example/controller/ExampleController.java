package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.ratelimiter.RateLimit;
import org.example.ratelimiter.RateLimiterService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ExampleController {
  private final RateLimiterService rateLimiterService;

  public ExampleController(RateLimiterService rateLimiterService) {
    this.rateLimiterService = rateLimiterService;
  }

  /**
   * A rate-limited endpoint that returns a simple message.
   * Uses default rate limit (60 requests per minute).
   */
  @GetMapping("/hello")
  @RateLimit
  public ResponseEntity<String> hello() {
    return ResponseEntity.ok("Hello! This endpoint is rate limited to 60 requests per minute.");
  }

  /**
   * A rate-limited endpoint with custom configuration.
   * Limited to 5 requests per 30 seconds.
   */
  @GetMapping("/limited")
  @RateLimit(limit = 5, timeWindowSeconds = 30)
  public ResponseEntity<String> limited() {
    return ResponseEntity.ok("Hello! This endpoint is rate limited to 5 requests per 30 seconds.");
  }

  /**
   * An endpoint that is not rate limited
   */
  @GetMapping("/unlimited")
  public ResponseEntity<String> unlimited() {
    return ResponseEntity.ok("Hello! This endpoint is not rate limited.");
  }

  /**
   * Get the current rate limit status for all endpoints for the client IP
   */
  @GetMapping(value = "/rate-info", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> getRateInfo(HttpServletRequest request) {
    String ipAddress = request.getRemoteAddr();
    List<RateLimiterService.RateLimitStatus> statuses = rateLimiterService.getRateLimitStatus(ipAddress);

    Map<String, Object> response = new HashMap<>();

    response.put("ip", ipAddress);

    if (statuses.isEmpty()) {
      response.put("message", "No rate limits are currently active for this IP address");
      response.put("limits", new HashMap<>());

      return response;
    }

    // Convert status list to a map of endpoint -> status details
    Map<String, Object> limitsMap = new HashMap<>();

    for (RateLimiterService.RateLimitStatus status : statuses) {
      Map<String, Object> limitInfo = new HashMap<>();

      limitInfo.put("description", status.getDescription());
      limitInfo.put("limit", status.getLimit());
      limitInfo.put("timeWindowSeconds", status.getTimeWindowSeconds());
      limitInfo.put("current", status.getCurrentCount());
      limitInfo.put("remaining", status.getRemainingRequests());
      limitInfo.put("resetsInSeconds", status.getTimeRemainingSeconds());

      limitsMap.put(status.getEndpoint(), limitInfo);
    }

    response.put("limits", limitsMap);

    return response;
  }
} 