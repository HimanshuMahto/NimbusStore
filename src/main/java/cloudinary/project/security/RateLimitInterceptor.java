package cloudinary.project.security;

import cloudinary.project.entity.UserEntity;
import cloudinary.project.repository.RateLimiterRespository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterRespository rateLimiterRespository;
    public static final int MAX_PER_MINUTE = 60;
    public static final int UPLOAD_MAX = 20;
    public static final int TRANSFORM_MAX = 60;

    public RateLimitInterceptor(RateLimiterRespository rateLimiterRespository) {
        this.rateLimiterRespository = rateLimiterRespository;
    }

    @Override
    @Transactional
    public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object handler) throws Exception {

        if (!"POST".equalsIgnoreCase(httpServletRequest.getMethod())) {
            return true;
        }

        // Authenticate the user whether
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserEntity user)) {
            return true;
        }

        String action;
        int limit;
        if (httpServletRequest.getRequestURI().endsWith("/transformations")) {
            action = "transform";
            limit = TRANSFORM_MAX;
        } else {
            action = "upload";
            limit = UPLOAD_MAX;
        }

        int currentCount = rateLimiterRespository.incrementAndGet(user.getId(), action);
        if (currentCount > limit) {
            httpServletResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); // 429
            httpServletResponse.setHeader("Retry-After", "60");
            httpServletResponse.setContentType("application/json");
            httpServletResponse.getWriter().write(
                    "{\"error\":\"Rate limit exceeded. Max " + MAX_PER_MINUTE + " requests per minute.\"}");
            return false; // short-circuit — controller never runs
        }
        return true;
    }

}
