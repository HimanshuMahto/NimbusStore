package cloudinary.project.controller;

import cloudinary.project.dto.*;
import cloudinary.project.entity.UserEntity;
import cloudinary.project.service.ImageService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping(value = "/uploadImage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageResponseDto> uploadImage(@RequestPart("file") MultipartFile file,
                                                        @RequestPart(value = "metadata", required = false)
                                                        @Valid ImageUploadRequestDto metadata, @AuthenticationPrincipal UserEntity currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(imageService.uploadImage(metadata, file, currentUser.getId()));
    }

    @GetMapping(value="/images")
    public ResponseEntity<Page<ImageResponseDto>> getAllImages(@AuthenticationPrincipal UserEntity currentUser, @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable){
        return ResponseEntity.ok(imageService.getAllImages(currentUser, pageable));
    }

    @GetMapping(value = "/image/{imageId}/content")
    public ResponseEntity<Resource> downloadImageById(@PathVariable Long imageId, @AuthenticationPrincipal UserEntity currentUser){
        ImageDownloadDto download = imageService.downloadImageContentById(imageId, currentUser);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.getFileName())
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.getResource());
    }
}
