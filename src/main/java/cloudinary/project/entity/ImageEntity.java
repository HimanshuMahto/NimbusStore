package cloudinary.project.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(
        name = "images",
        indexes = {
                @Index(name = "idx_images_user_id", columnList = "user_id"),
                @Index(name = "idx_images_checksum", columnList = "checksum")
        }
)
public class ImageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @NotBlank
    @Column(nullable = false)
    private String fileName;

    @NotNull
    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String storageKey;

    @NotBlank
    @Column(nullable = false)
    private String contentType;

    private Integer width;

    private Integer height;

    private String checksum;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isPublic == null) {
            this.isPublic = false;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}