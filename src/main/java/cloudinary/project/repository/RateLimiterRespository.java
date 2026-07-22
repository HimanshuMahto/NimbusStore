package cloudinary.project.repository;

import cloudinary.project.entity.RateLimiterEntity;
import cloudinary.project.keyClass.RateLimiterId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RateLimiterRespository
        extends JpaRepository<RateLimiterEntity, RateLimiterId> {

    @Modifying
    @Query(value = """
            INSERT INTO rate_limits (user_id, action, window_start_time, request_counter)
            VALUES (:userId, :action, date_trunc('minute', now()), 1)
            ON CONFLICT (user_id, action, window_start_time)
            DO UPDATE SET request_counter = rate_limits.request_counter + 1
            RETURNING request_counter
            """, nativeQuery = true)
    int incrementAndGet(@Param("userId") Long userId, @Param("action") String action);

}
