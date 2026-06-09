package cloudinary.project.error;

import lombok.Data;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;

@Data
public class ApiError {

    private HttpStatus statusCode;
    private String error;
    private LocalDate timestamp;

    public ApiError() {
        this.timestamp = LocalDate.now();
    }

    public ApiError(HttpStatus statusCode, String error) {
        this();
        this.error = error;
        this.timestamp = LocalDate.now();
    }
}
