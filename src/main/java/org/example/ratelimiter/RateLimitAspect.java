package org.example.ratelimiter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Aspect
@Component
public class RateLimitAspect {
  private final RateLimiterService rateLimiterService;

  public RateLimitAspect(RateLimiterService rateLimiterService) {
    this.rateLimiterService = rateLimiterService;
  }

  @Around("@annotation(org.example.ratelimiter.RateLimit)")
  public Object checkRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
    MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
    Method method = methodSignature.getMethod();
    RateLimit rateLimitAnnotation = method.getAnnotation(RateLimit.class);

    // Extract limit parameters
    int limit = rateLimitAnnotation.limit();
    int timeWindowSeconds = rateLimitAnnotation.timeWindowSeconds();

    // Get the current request and extract client IP
    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    HttpServletRequest request = attributes.getRequest();
    String ipAddress = request.getRemoteAddr();

    // Register this endpoint for rate limit tracking
    String requestURI = request.getRequestURI();
    String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();
    String description = methodName + " (limit: " + limit + " requests per " +
      (timeWindowSeconds == 60 ? "minute" : timeWindowSeconds + " seconds") + ")";

    rateLimiterService.registerRateLimit(requestURI, limit, timeWindowSeconds, description);

    // Check if the request should be allowed
    if (rateLimiterService.allowRequest(ipAddress, limit, timeWindowSeconds)) {
      return joinPoint.proceed();
    } else {
      // Rate limit exceeded, return 429 Too Many Requests
      HttpServletResponse response = attributes.getResponse();

      if (response != null) {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.getWriter().write("Rate limit exceeded. Try again later.");
        response.getWriter().flush();
      }

      return null;
    }
  }
} 