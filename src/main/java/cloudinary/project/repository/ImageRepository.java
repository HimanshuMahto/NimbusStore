package cloudinary.project.repository;

import cloudinary.project.entity.ImageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImageRepository extends JpaRepository<ImageEntity, Long> {

    Page<ImageEntity> findByUserId(Long userId, Pageable pageable);
}
