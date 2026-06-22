package cloudinary.project.repository;

import cloudinary.project.entity.TransformationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransformationRepository extends JpaRepository<TransformationEntity, Long> {

    Optional<TransformationEntity> findByImageIdAndTransformationHash(Long imageId, String transformationHash);

    List<TransformationEntity> findByImageId(Long imageId);

    Page<TransformationEntity> findByImageId(Long imageId, Pageable pageable);

    Page<TransformationEntity> findByImage_UserId(Long userId, Pageable pageable);

}
