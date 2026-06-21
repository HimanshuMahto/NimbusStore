package cloudinary.project.controller;

import cloudinary.project.dto.TransformationRequestDto;
import cloudinary.project.dto.TransformationResponseDto;
import cloudinary.project.entity.UserEntity;
import cloudinary.project.service.TransformationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TransformationController {

    private final TransformationService transformationService;

    public TransformationController(TransformationService transformationService) {
        this.transformationService = transformationService;
    }

    @PostMapping("/images/{imageId}/transformations")
    public ResponseEntity<TransformationResponseDto> createTransformation(
            @PathVariable Long imageId,
            @RequestBody TransformationRequestDto metadata,
            @AuthenticationPrincipal UserEntity currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transformationService.transformImage(imageId, metadata, currentUser));
    }
}
