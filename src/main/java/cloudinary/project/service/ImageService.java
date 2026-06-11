package cloudinary.project.service;

import cloudinary.project.dto.ImageResponseDto;
import cloudinary.project.dto.ImageUploadRequestDto;
import cloudinary.project.dto.UserSummaryDto;
import cloudinary.project.entity.ImageEntity;
import cloudinary.project.entity.UserEntity;
import cloudinary.project.repository.ImageRepository;
import cloudinary.project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final UserRepository userRepository;
    private final ImageRepository imageRepository;

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
        if(user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
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

}
