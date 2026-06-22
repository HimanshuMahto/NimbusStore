package cloudinary.project.service;

import cloudinary.project.dto.*;
import cloudinary.project.entity.ImageEntity;
import cloudinary.project.entity.TransformationEntity;
import cloudinary.project.entity.UserEntity;
import cloudinary.project.repository.ImageRepository;
import cloudinary.project.repository.TransformationRepository;
import cloudinary.project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final UserRepository userRepository;
    private final ImageRepository imageRepository;
    private final TransformationRepository transformationRepository;

    @Value("${app.storage.local.root}")
    private String storageRoot;

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    private String generateStorageKey() {
        UUID uuid = UUID.randomUUID();
        String uuidString = uuid.toString();
        return uuidString.substring(0, 8) + "/" + uuidString.substring(8, 12) + "/" + uuidString;
    }

    public String calculateMD5(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return DigestUtils.md5DigestAsHex(is);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to interpret file content");
        }
    }

    public ImageResponseDto uploadImage(ImageUploadRequestDto metadata, MultipartFile file, Long userId) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if(user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User does not exists");
        String generatedStorageKey = generateStorageKey();
        ImageEntity entity = new ImageEntity();
        if(file.getContentType() == null || !ALLOWED_IMAGE_TYPES.contains(file.getContentType())){
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Uploaded file is not an image");
        }
        String checkSum = calculateMD5(file);
        entity.setUser(user);
        entity.setFileName(file.getOriginalFilename());
        entity.setFileSize(file.getSize());
        entity.setContentType(file.getContentType());
        entity.setStorageKey(generatedStorageKey);
        entity.setChecksum(checkSum);
        try (InputStream is = file.getInputStream()){
            BufferedImage image = ImageIO.read(is);
            if (image == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file is corrupted or not a valid image");
            }
            entity.setWidth(image.getWidth());
            entity.setHeight(image.getHeight());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to interpret file content");
        }
        entity.setIsPublic(metadata != null ? metadata.getIsPublic() : false);
        try{
            Path targetPath = Paths.get(storageRoot, generatedStorageKey);
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save file");
        }
        ImageEntity savedImage = imageRepository.save(entity);
        return new ImageResponseDto(savedImage.getId(), new UserSummaryDto(user.getId(), user.getUsername()), savedImage.getFileName(), savedImage.getFileSize(), savedImage.getContentType(), savedImage.getWidth(), savedImage.getHeight(), savedImage.getChecksum(), savedImage.getIsPublic(), savedImage.getCreatedAt(), savedImage.getUpdatedAt());
    }

    public Page<ImageResponseDto> getAllImages(UserEntity currentUser, Pageable pageable) {
        Page<ImageEntity> imageEntity = imageRepository.findByUserId(currentUser.getId(), pageable);
        return imageEntity.map(image -> new ImageResponseDto(image.getId(), new UserSummaryDto(currentUser.getId(), currentUser.getUsername()), image.getFileName(), image.getFileSize(), image.getContentType(), image.getWidth(), image.getHeight(), image.getChecksum(), image.getIsPublic(), image.getCreatedAt(), image.getUpdatedAt()));
    }

    public ImageDownloadDto downloadImageContentById(Long imageId, UserEntity currentUser) {
        ImageEntity imageEntity = imageRepository.findById(imageId).orElse(null);
        if(imageEntity == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image does not exists");
        if(imageEntity.getIsPublic() == false && !Objects.equals(imageEntity.getUser().getId(), currentUser.getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to access this image");
        Path targetPath = Paths.get(storageRoot, imageEntity.getStorageKey());
        if(!Files.exists(targetPath)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Image does not exists");
        }
        // Converting file's bytes into the HTTP response.
        FileSystemResource resource = new FileSystemResource(targetPath);
        return new ImageDownloadDto(resource, imageEntity.getContentType(), imageEntity.getFileName());
    }

    @Transactional(readOnly = true)
    public ImageResponseDto getImageMetaDataById(Long imageId, UserEntity currentUser) {
        ImageEntity imageEntity = imageRepository.findById(imageId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image does not exists"));
        if(!imageEntity.getIsPublic() && !Objects.equals(imageEntity.getUser().getId(), currentUser.getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to access this image");
        return new ImageResponseDto(imageEntity.getId(), new UserSummaryDto(imageEntity.getUser().getId(), imageEntity.getUser().getUsername()), imageEntity.getFileName(), imageEntity.getFileSize(), imageEntity.getContentType(), imageEntity.getWidth(), imageEntity.getHeight(), imageEntity.getChecksum(), imageEntity.getIsPublic(), imageEntity.getCreatedAt(), imageEntity.getUpdatedAt());
    }

    @Transactional
    public void deleteImageById(Long imageId, UserEntity currentUser) {
        ImageEntity imageEntity = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image does not exists"));

        if (!Objects.equals(imageEntity.getUser().getId(), currentUser.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to delete this image");

        //Deletes every transformation's output file from disk.
        List<TransformationEntity> transformations = transformationRepository.findByImageId(imageId);
        for (TransformationEntity t : transformations) {
            deleteFileQuietly(Paths.get(storageRoot, t.getOutputStorageKey()));
        }
        //Deletes all transformation rows.
        transformationRepository.deleteAll(transformations);
        //Deletes the original image file from disk.
        deleteFileQuietly(Paths.get(storageRoot, imageEntity.getStorageKey()));
        //Deletes the image row.
        imageRepository.deleteById(imageId);
    }

    private void deleteFileQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
