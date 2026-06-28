package cloudinary.project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransformationRequestDto {

    @Valid
    private ResizeDto resize;

    @Valid
    private CropDto crop;

    @Min(-360) @Max(360)
    private Double rotate;

    private FormatType format;

    @Valid
    private CompressDto compress;

    private Boolean flipVertical;

    private Boolean mirror;

    private Boolean grayscale;

    private Boolean sepia;

    @Valid
    private WatermarkDto watermark;
}