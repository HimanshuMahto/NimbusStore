package cloudinary.project.controller;

import cloudinary.project.dto.TransformationRequestDto;
import cloudinary.project.dto.TransformationResponseDto;
import cloudinary.project.dto.TransformedImageDownloadDto;
import cloudinary.project.entity.UserEntity;
import cloudinary.project.service.TransformationService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TransformationController {

    private final TransformationService transformationService;

    public TransformationController(TransformationService transformationService) {
        this.transformationService = transformationService;
    }

    @PostMapping("/{imageId}/transformations")
    public ResponseEntity<TransformationResponseDto> createTransformation(
            @PathVariable Long imageId,
            @RequestBody TransformationRequestDto metadata,
            @AuthenticationPrincipal UserEntity currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transformationService.transformImage(imageId, metadata, currentUser));
    }

    @GetMapping("/transformations/{transformationId}/content")
    public ResponseEntity<Resource> downloadTransformedImageById(
            @PathVariable Long transformationId,
            @AuthenticationPrincipal UserEntity currentUser ) {
        TransformedImageDownloadDto transformedImageDownloadDto = transformationService.downloadTransformationContentById(transformationId, currentUser);
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(transformedImageDownloadDto.getFileName())
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(transformedImageDownloadDto.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(transformedImageDownloadDto.getResource());
    }
}
