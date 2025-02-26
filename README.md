# IP-based Rate Limiter for Spring Boot

A simple IP-based rate limiter implementation for Spring Boot applications.

## Features

- IP-based rate limiting using in-memory cache (Caffeine)
- Configurable request limits through annotation parameters
- Easy to use with a simple annotation-based approach
- Automatically returns HTTP 429 (Too Many Requests) when rate limit is exceeded
- Secure IP resolution using `request.getRemoteAddr()` to avoid header spoofing
- Comprehensive rate limit status information in JSON format for all endpoints

## Usage

### Adding the Rate Limit to a Controller Method

Simply add the `@RateLimit` annotation to any controller method you want to rate limit:

```java
// Default rate limit: 60 requests per minute
@GetMapping("/endpoint")
@RateLimit
public ResponseEntity<String> rateLimitedEndpoint() {
    return ResponseEntity.ok("This endpoint is rate limited");
}

// Custom rate limit: 5 requests per 30 seconds
@GetMapping("/limited")
@RateLimit(limit = 5, timeWindowSeconds = 30)
public ResponseEntity<String> customRateLimitedEndpoint() {
    return ResponseEntity.ok("This endpoint has a custom rate limit");
}
```

### Rate Limit Parameters

The `@RateLimit` annotation supports the following parameters:

- `limit`: The maximum number of requests allowed within the time window (default: 60)
- `timeWindowSeconds`: The time window in seconds for the rate limit (default: 60)

### How it Works

The rate limiter:

1. Extracts the client's IP address from the request using `request.getRemoteAddr()`
2. Automatically registers endpoints when they're first accessed
3. Maintains a counter for each IP address and rate limit configuration
4. Increments the counter with each request
5. Rejects requests if they exceed the configured limit
6. Automatically resets counters after the configured time window
7. Provides detailed status information for all rate limits

### Checking Rate Limit Status

The API provides a status endpoint (`/api/rate-info`) that returns detailed information about all active rate limits for
your IP address in JSON format:

```json
{
  "ip": "127.0.0.1",
  "limits": {
    "/api/hello": {
      "description": "ExampleController.hello (limit: 60 requests per minute)",
      "limit": 60,
      "timeWindowSeconds": 60,
      "current": 12,
      "remaining": 48,
      "resetsInSeconds": 32
    },
    "/api/limited": {
      "description": "ExampleController.limited (limit: 5 requests per 30 seconds)",
      "limit": 5,
      "timeWindowSeconds": 30,
      "current": 3,
      "remaining": 2,
      "resetsInSeconds": 17
    }
  }
}
```

### Configuration

Rate limiting is configured in the `RateLimiterConfig` class:

- Default cache expiry: 1 minute (counters reset after this time)
- Individual endpoints can override the default limits via annotation parameters

## Example Endpoints

The application includes example endpoints:

- `GET /api/hello` - Rate-limited to 60 requests per minute (default)
- `GET /api/limited` - Rate-limited to 5 requests per 30 seconds (custom)
- `GET /api/unlimited` - An endpoint that is not rate limited
- `GET /api/rate-info` - Shows comprehensive rate limit status for all endpoints in JSON format

## Security Considerations

This implementation uses `request.getRemoteAddr()` directly rather than relying on HTTP headers like X-Forwarded-For.
This approach was chosen for security reasons as HTTP headers can be easily spoofed by malicious clients.