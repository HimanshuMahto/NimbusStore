-- V1__init.sql
-- Initial schema: users, images, transformations

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(30)  NOT NULL UNIQUE,
    password_hashed VARCHAR(255) NOT NULL,
    email           VARCHAR(254) NOT NULL UNIQUE,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL
);

CREATE TABLE images (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id),
    file_name     VARCHAR(255) NOT NULL,
    file_size     BIGINT       NOT NULL,
    storage_key   VARCHAR(255) NOT NULL UNIQUE,
    content_type  VARCHAR(127) NOT NULL,
    width         INTEGER,
    height        INTEGER,
    checksum      VARCHAR(128),
    is_public     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);

CREATE INDEX idx_images_user_id  ON images(user_id);
CREATE INDEX idx_images_checksum ON images(checksum);

CREATE TABLE transformations (
    id                    BIGSERIAL PRIMARY KEY,
    image_id              BIGINT       NOT NULL REFERENCES images(id),
    transformation_hash   VARCHAR(128) NOT NULL,
    transformation_config JSONB        NOT NULL,
    output_storage_key    VARCHAR(255) UNIQUE,
    output_content_type   VARCHAR(127),
    output_file_size      BIGINT,
    status                VARCHAR(20)  NOT NULL,
    error_message         TEXT,
    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP    NOT NULL,
    CONSTRAINT uq_transformations_image_hash UNIQUE (image_id, transformation_hash)
);

CREATE INDEX idx_transformations_image_id ON transformations(image_id);
CREATE INDEX idx_transformations_status   ON transformations(status);
