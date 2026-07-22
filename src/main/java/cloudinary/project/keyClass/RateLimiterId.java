package cloudinary.project.keyClass;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
public class RateLimiterId implements Serializable {

    private Long userId;
    private String action;
    private LocalDateTime windowStartTime;

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof RateLimiterId)) {
            return false;
        }
        RateLimiterId other = (RateLimiterId) obj;
        return this.userId.equals(other.userId) && this.action.equals(other.action) && this.windowStartTime.equals(other.windowStartTime);
    }

    @Override
    public int hashCode() {
        return this.userId.hashCode() + this.action.hashCode() + this.windowStartTime.hashCode();
    }

}
