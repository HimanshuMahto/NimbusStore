package cloudinary.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResizeDto {
    @NotNull @Min(1) @Max(8000) private Integer width;
    @NotNull @Min(1) @Max(8000) private Integer height;
}