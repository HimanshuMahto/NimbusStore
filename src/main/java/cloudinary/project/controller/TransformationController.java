package cloudinary.project.controller;

import cloudinary.project.dto.TransformationRequestDto;
import cloudinary.project.dto.TransformationResponseDto;
import cloudinary.project.entity.UserEntity;
import cloudinary.project.service.TransformationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/transformation")
public class TransformationController {

    private final TransformationService transformationService;

    public TransformationController(TransformationService transformationService) {
        this.transformationService = transformationService;
    }

    @PostMapping(value = "/image/{id}/tranform")
    public ResponseEntity<TransformationResponseDto> changeImageTransform(@PathVariable Long id, @RequestBody TransformationRequestDto metadata, @AuthenticationPrincipal UserEntity currentUser) {
        return ResponseEntity.ok(transformationService.transformImage(id, metadata, currentUser));
    }

}
