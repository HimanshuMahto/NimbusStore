package cloudinary.project.service;

import cloudinary.project.config.GrayScaleFilter;
import cloudinary.project.config.SepiaFilter;
import cloudinary.project.dto.*;
import cloudinary.project.entity.ImageEntity;
import cloudinary.project.entity.TransformationEntity;
import cloudinary.project.entity.TransformationStatus;
import cloudinary.project.entity.UserEntity;
import cloudinary.project.repository.ImageRepository;
import cloudinary.project.repository.TransformationRepository;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.filters.Flip;
import net.coobird.thumbnailator.geometry.Positions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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
        if (!transformationEntity.getImage().getIsPublic() && !Objects.equals(transformationEntity.getImage().getUser().getId(), currentUser.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to access this image");
        Path targetPath = Paths.get(storageRoot, transformationEntity.getOutputStorageKey());
        if (!Files.exists(targetPath))
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

    //helper method to get the file format from the enum
    private String mimeForFormat(FormatType f) {
        return switch (f) {
            case JPEG, JPG -> "image/jpeg";
            case PNG -> "image/png";
            case WEBP -> "image/webp";
            case GIF -> "image/gif";
            default -> null;
        };
    }

    // Returns Thumbnailator format name: "webp", "jpeg", "png", "gif"
    private String thumbnailatorFormat(FormatType f) {
        return switch (f) {
            case JPEG, JPG -> "jpeg";
            case PNG -> "png";
            case WEBP -> "webp";
            case GIF -> "gif";
        };
    }

    //helper method to convert watermark position to Thumbnailator position
    private Positions toThumbnailatorPosition(Position p) {
        return switch (p) {
            case TOP_LEFT -> Positions.TOP_LEFT;
            case TOP_CENTER -> Positions.TOP_CENTER;
            case TOP_RIGHT -> Positions.TOP_RIGHT;
            case CENTER_LEFT -> Positions.CENTER_LEFT;
            case CENTER_RIGHT -> Positions.CENTER_RIGHT;
            case BOTTOM_LEFT -> Positions.BOTTOM_LEFT;
            case BOTTOM_CENTER -> Positions.BOTTOM_CENTER;
            case BOTTOM_RIGHT -> Positions.BOTTOM_RIGHT;
            default -> Positions.CENTER;
        };
    }

    //helper method to resolve watermark image
    private BufferedImage resolveWatermarkImage(WatermarkDto dto, UserEntity currentUser) {
        if (dto.getImageId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "watermark imageId is required");
        ImageEntity wm = imageRepository.findById(dto.getImageId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "watermark image not found"));

        if (!wm.getIsPublic() && !Objects.equals(wm.getUser().getId(), currentUser.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not allowed to use that image as watermark");

        Path wmPath = Paths.get(storageRoot, wm.getStorageKey());
        if (!Files.exists(wmPath))
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "watermark file missing from storage");
        try {
            BufferedImage img = ImageIO.read(wmPath.toFile());
            if (img == null)
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "could not read watermark image");
            return img;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to read watermark image");
        }
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
            return toResponseDto(t);

        }
        String generatedStorageKey = generateStorageKey();
        TransformationEntity transformationEntity = new TransformationEntity();
        Path transformedPath = Paths.get(storageRoot, generatedStorageKey);
        try {
            Files.createDirectories(transformedPath.getParent());
            Thumbnails.Builder<File> builder = Thumbnails.of(targetPath.toFile());
            if (metadata.getResize() != null && metadata.getResize().getWidth() != null && metadata.getResize().getHeight() != null)
                builder.size(metadata.getResize().getWidth(), metadata.getResize().getHeight());
            else {
                builder.scale(1.0);
            }
            if (metadata.getRotate() != null) builder.rotate(metadata.getRotate());
            if (metadata.getCrop() != null && metadata.getCrop().getX() != null && metadata.getCrop().getY() != null && metadata.getCrop().getWidth() != null && metadata.getCrop().getHeight() != null)
                builder.sourceRegion(metadata.getCrop().getX(), metadata.getCrop().getY(), metadata.getCrop().getWidth(), metadata.getCrop().getHeight());
            if (metadata.getFormat() != null) {
                builder.outputFormat(thumbnailatorFormat(metadata.getFormat()));
            } else {
                builder.outputFormat(imageEntity.getContentType().substring("image/".length()));
            }
            if (metadata.getCompress() != null && metadata.getCompress().getQuality() != null)
                builder.outputQuality(metadata.getCompress().getQuality() / 100.0f);
            if (Boolean.TRUE.equals(metadata.getGrayscale())) builder.addFilter(new GrayScaleFilter());
            if (Boolean.TRUE.equals(metadata.getSepia())) builder.addFilter(new SepiaFilter());
            if (Boolean.TRUE.equals(metadata.getFlipVertical())) builder.addFilter(Flip.VERTICAL);
            if (Boolean.TRUE.equals(metadata.getMirror())) builder.addFilter(Flip.HORIZONTAL);
            if (metadata.getWatermark() != null) {
                BufferedImage watermarkImage = resolveWatermarkImage(metadata.getWatermark(), currentUser);
                builder.watermark(
                        toThumbnailatorPosition(metadata.getWatermark().getPosition()),
                        watermarkImage,
                        metadata.getWatermark().getOpacity()
                );
            }
            try (OutputStream os = Files.newOutputStream(transformedPath)) {
                builder.toOutputStream(os);
            }
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
        Map<String, Object> config = new HashMap<>();
        if (metadata.getResize() != null) config.put("resize", metadata.getResize());
        if (metadata.getRotate() != null) config.put("rotate", metadata.getRotate());
        if (metadata.getCrop() != null) config.put("crop", metadata.getCrop());
        if (metadata.getFormat() != null) config.put("format", metadata.getFormat());
        if (metadata.getCompress() != null) config.put("compress", metadata.getCompress());
        if (metadata.getGrayscale() != null) config.put("grayscale", metadata.getGrayscale());
        if (metadata.getSepia() != null) config.put("sepia", metadata.getSepia());
        if (metadata.getFlipVertical() != null) config.put("flipVertical", metadata.getFlipVertical());
        if (metadata.getMirror() != null) config.put("mirror", metadata.getMirror());
        if (metadata.getWatermark() != null) config.put("watermark", metadata.getWatermark());
        transformationEntity.setTransformationConfig(config);
        transformationEntity.setOutputStorageKey(generatedStorageKey);
        transformationEntity.setOutputContentType(metadata.getFormat() != null ? mimeForFormat(metadata.getFormat()) : imageEntity.getContentType());
        transformationEntity.setOutputFileSize(outputFileSize);
        transformationEntity.setStatus(TransformationStatus.COMPLETED);
        TransformationEntity saved = transformationRepository.save(transformationEntity);
        return toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public TransformationResponseDto getTransformedImageMetaDataById(Long transformationId, UserEntity currentUser) {
        TransformationEntity transformationEntity = transformationRepository.findById(transformationId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transformation does not exist"));
        if (!transformationEntity.getImage().getIsPublic() && !Objects.equals(transformationEntity.getImage().getUser().getId(), currentUser.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to access this image");
        return toResponseDto(transformationEntity);
    }

    @Transactional(readOnly = true)
    public Page<TransformationResponseDto> getAllTransformations(UserEntity currentUser, Pageable pageable) {
        Page<TransformationEntity> page = transformationRepository.findByImage_UserId(currentUser.getId(), pageable);
        return page.map(this::toResponseDto);
    }

    @Transactional(readOnly = true)
    public Page<TransformationResponseDto> getAllTransformationsByImageId(Long imageId, UserEntity currentUser, Pageable pageable) {
        ImageEntity image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image does not exist"));

        if (!image.getIsPublic() && !Objects.equals(image.getUser().getId(), currentUser.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to view this image's transformations");

        Page<TransformationEntity> page = transformationRepository.findByImageId(imageId, pageable);
        return page.map(this::toResponseDto);
    }

    private TransformationResponseDto toResponseDto(TransformationEntity t) {
        return new TransformationResponseDto(
                t.getId(),
                t.getStatus().name(),
                t.getOutputContentType(),
                t.getOutputFileSize(),
                t.getCreatedAt()
        );
    }

    @Transactional
    public boolean deleteTransformedImageById(Long transformationId, UserEntity currentUser) {
        TransformationEntity transformationEntity = transformationRepository.findById(transformationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transformed Image does not exists"));
        if (!Objects.equals(transformationEntity.getImage().getUser().getId(), currentUser.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to delete this image");
        if (Files.exists(Paths.get(storageRoot, transformationEntity.getOutputStorageKey()))) {
            try {
                Files.deleteIfExists(Paths.get(storageRoot, transformationEntity.getOutputStorageKey()));
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete transformed image");
            }
        }
        transformationRepository.deleteById(transformationId);
        return true;
    }
}
