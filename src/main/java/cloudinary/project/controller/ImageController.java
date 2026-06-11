package cloudinary.project.controller;

import cloudinary.project.dto.ImageResponseDto;
import cloudinary.project.dto.ImageUploadRequestDto;
import cloudinary.project.entity.UserEntity;
import cloudinary.project.service.ImageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/images")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageResponseDto> uploadImage(@RequestPart("file") MultipartFile file,
                                                        @RequestPart(value = "metadata", required = false)
                                                        @Valid ImageUploadRequestDto metadata, @AuthenticationPrincipal UserEntity currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(imageService.uploadImage(metadata, file, currentUser.getId()));
    }

}
