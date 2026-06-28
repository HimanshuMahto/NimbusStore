package cloudinary.project.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatermarkDto {

    private Long imageId;

    private String text;

    @NotNull
    private Position position;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Float opacity;

}