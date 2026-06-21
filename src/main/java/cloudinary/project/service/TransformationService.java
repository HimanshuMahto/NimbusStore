package cloudinary.project.service;

import cloudinary.project.dto.TransformationRequestDto;
import cloudinary.project.dto.TransformationResponseDto;
import cloudinary.project.dto.TransformedImageDownloadDto;
import cloudinary.project.entity.ImageEntity;
import cloudinary.project.entity.TransformationEntity;
import cloudinary.project.entity.TransformationStatus;
import cloudinary.project.entity.UserEntity;
import cloudinary.project.repository.ImageRepository;
import cloudinary.project.repository.TransformationRepository;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransformationService {

    private final ImageRepository imageRepository;
    private final TransformationRepository transformationRepository;

    @Value("${app.storage.local.root}")
    private String storageRoot;

    @Transactional(readOnly = true)
    public TransformedImageDownloadDto downloadTransformationContentById(Long transformationId, UserEntity currentUser) {
        TransformationEntity transformationEntity = transformationRepository.findById(transformationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transformed Image does not exists"));
        if(!transformationEntity.getImage().getIsPublic() && !Objects.equals(transformationEntity.getImage().getUser().getId(), currentUser.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to access this image");
        Path targetPath = Paths.get(storageRoot, transformationEntity.getOutputStorageKey());
        if(!Files.exists(targetPath))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transformed file missing from the storage");
        FileSystemResource resource = new FileSystemResource(targetPath);
        return new TransformedImageDownloadDto(resource, transformationEntity.getOutputContentType(), transformationEntity.getImage().getFileName());
    }

    private String generateStorageKey() {
        UUID uuid = UUID.randomUUID();
        String uuidString = uuid.toString();
        return uuidString.substring(0, 8) + "/" + uuidString.substring(8, 12) + "/" + uuidString;
    }

    private String getTransformationHashed(TransformationRequestDto metadata) {
        return DigestUtils.md5DigestAsHex(metadata.toString().getBytes());
    }

    public TransformationResponseDto transformImage(Long id, TransformationRequestDto metadata, UserEntity currentUser) {
        ImageEntity imageEntity = imageRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image does not exists"));
        if (!Objects.equals(imageEntity.getUser().getId(), currentUser.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to access this image");
        Path targetPath = Paths.get(storageRoot, imageEntity.getStorageKey());
        if (!Files.exists(targetPath)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Image does not exists");
        }
        String transformationHash = getTransformationHashed(metadata);
        Optional<TransformationEntity> existing = transformationRepository.findByImageIdAndTransformationHash(id, transformationHash);
        if (existing.isPresent()) {
            TransformationEntity t = existing.get();
            return new TransformationResponseDto(
                    t.getId(),                    // transformation id, not image id
                    t.getStatus().name(),         // real status
                    t.getOutputContentType(),     // output's type
                    t.getOutputFileSize(),        // output's size
                    t.getCreatedAt()              // entity's original creation time
            );

        }
        String generatedStorageKey = generateStorageKey();
        TransformationEntity transformationEntity = new TransformationEntity();
        Path transformedPath = Paths.get(storageRoot, generatedStorageKey);
        try {
            Files.createDirectories(transformedPath.getParent());
            Thumbnails.of(targetPath.toFile()).size(metadata.getWidth(), metadata.getHeight()).toFile(transformedPath.toFile());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Transformation failed");
        }
        long outputFileSize;
        try {
            outputFileSize = Files.size(transformedPath);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read output file size");
        }
        transformationEntity.setImage(imageEntity);
        transformationEntity.setTransformationHash(transformationHash);
        transformationEntity.setTransformationConfig(Map.of("width", metadata.getWidth(), "height", metadata.getHeight()));
        transformationEntity.setOutputStorageKey(generatedStorageKey);
        transformationEntity.setOutputContentType(imageEntity.getContentType());
        transformationEntity.setOutputFileSize(outputFileSize);
        transformationEntity.setStatus(TransformationStatus.COMPLETED);
        TransformationEntity saved = transformationRepository.save(transformationEntity);
        return new TransformationResponseDto(saved.getId(), saved.getStatus().name(), saved.getOutputContentType(), saved.getOutputFileSize(), saved.getCreatedAt());
    }

}
