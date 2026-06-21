package cloudinary.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.core.io.Resource;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransformedImageDownloadDto {

    private Resource resource;
    private String contentType;
    private String fileName;

}
