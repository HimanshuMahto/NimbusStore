package cloudinary.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageResponseDto {

    private Long id;
    private UserSummaryDto owner;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private Integer width;
    private Integer height;
    private String checksum;
    private Boolean isPublic;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
