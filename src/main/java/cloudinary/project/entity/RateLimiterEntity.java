package cloudinary.project.entity;

import cloudinary.project.keyClass.RateLimiterId;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@IdClass(RateLimiterId.class)
@Table(name = "rate_limits")
public class RateLimiterEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "action")
    private String action;

    @Id
    @Column(name = "window_start_time")
    private LocalDateTime windowStartTime;

    @Column(name = "request_counter")
    @NotNull
    private Integer requestCounter;

}
