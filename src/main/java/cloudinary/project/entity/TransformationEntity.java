package cloudinary.project.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(
        name = "transformations",
        indexes = {
                @Index(name = "idx_transformations_image_id", columnList = "image_id"),
                @Index(name = "idx_transformations_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_transformations_image_hash",
                        columnNames = {"image_id", "transformation_hash"}
                )
        }
)
public class TransformationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "image_id", nullable = false)
    private ImageEntity image;

    @NotBlank
    @Column(name = "transformation_hash", nullable = false)
    private String transformationHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> transformationConfig;

    @Column(unique = true)
    private String outputStorageKey;

    private String outputContentType;

    private Long outputFileSize;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransformationStatus status;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = TransformationStatus.PENDING;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}