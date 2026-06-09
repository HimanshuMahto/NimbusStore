package cloudinary.project.repository;

import cloudinary.project.entity.TransformationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransformationRepository extends JpaRepository<TransformationEntity, Long> {

}
