package cloudinary.project.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CropDto {
    @NotNull @Min(0) private Integer x;
    @NotNull @Min(0) private Integer y;
    @NotNull @Min(1) private Integer width;
    @NotNull @Min(1) private Integer height;
}