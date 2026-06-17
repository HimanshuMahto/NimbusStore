# NimbusStore

A backend-only image processing platform inspired by Cloudinary, built with Java Spring Boot.

Authenticated users upload images, manage metadata, request transformations (resize and more), and retrieve original or transformed bytes via REST APIs. Transformations are deduplicated via a hash of the config so identical requests are served from disk instead of recomputed.

The project is intentionally designed to demonstrate backend engineering concepts — clean architecture, ownership-based authorization, metadata-driven file management, storage abstraction, and an extensible transformation system — rather than CRUD.

---

## Tech stack

**Backend**
- Java 17, Spring Boot (Web MVC, Data JPA, Security, Validation)
- Spring Security + JWT (`io.jsonwebtoken:jjwt`)
- Hibernate via Spring Data JPA

**Image processing**
- [Thumbnailator](https://github.com/coobird/thumbnailator) for resize / scale operations

**Database**
- PostgreSQL (prod) / H2 (in-memory, dev) — swap via `spring.datasource.*`
- Flyway migrations under `src/main/resources/db/migration`

**Storage**
- Local filesystem for the current version
- Designed to support AWS S3 / Cloudflare R2 / MinIO later via `storageKey` indirection — no schema changes required for migration

**Build**
- Maven (wrapper: `./mvnw`)

---

## Project layout

```
src/main/java/cloudinary/project/
├── ProjectApplication.java
├── controller/        # Auth + Image controllers
├── service/           # AuthService, ImageService, TransformationService, JwtService, ...
├── security/          # SecurityConfig + JwtFilter
├── repository/        # JPA repositories
├── entity/            # JPA entities
├── dto/               # Request/response DTOs
└── error/             # GlobalExceptionHandler + ApiError
src/main/resources/
├── application.properties
└── db/migration/V1__init.sql
```

---

## Architecture

The application is currently a modular monolith with three primary modules:

### Authentication module
User registration and login, JWT generation and validation, endpoint protection.

### Image module
Image upload, metadata storage, retrieval, paginated listing, download/view. The original `ImageEntity` is **immutable after upload** — transformations never mutate it.

### Transformation module
Image transformations, transformation metadata, derived-artifact storage, hash-based caching. Each transformation produces a new file at its own `outputStorageKey` and a corresponding `TransformationEntity` row.

---

## Data model

### User
Represents an authenticated platform user.

| Field | Notes |
| --- | --- |
| `id` | PK |
| `username`, `email`, `passwordHash` | credentials |
| `createdAt`, `updatedAt` | timestamps |

A user can own many images.

### Image
Represents an original uploaded image. Stores metadata only; bytes live on disk.

| Field | Notes |
| --- | --- |
| `id` | PK |
| `user` | FK → users |
| `fileName`, `contentType`, `fileSize` | original file metadata |
| `storageKey` | path-segment under `app.storage.local.root` |
| `width`, `height` | from `ImageIO` |
| `checksum` | MD5 of bytes |
| `isPublic` | viewability flag |
| `createdAt`, `updatedAt` | timestamps |

An image can have many transformations.

### Transformation
Represents a derived artifact generated from an original image.

| Field | Notes |
| --- | --- |
| `id` | PK |
| `image` | FK → images |
| `transformationHash` | MD5 of canonicalized config (unique with `image_id`) |
| `transformationConfig` | JSON map of params (e.g. `{width,height}`) |
| `outputStorageKey` | path-segment for the derived file |
| `outputContentType`, `outputFileSize` | derived-artifact metadata |
| `status` | enum: `PENDING` / `PROCESSING` / `COMPLETED` / `FAILED` |
| `errorMessage` | populated on `FAILED` (currently unused — sync path) |
| `createdAt`, `updatedAt` | timestamps |

Unique constraint on `(image_id, transformation_hash)` — the same config against the same source can only exist once.

---

## Authentication

Implemented with stateless JWT. CSRF disabled in `SecurityConfig` (stateless JWT API — intentional).

| Method | Path | Body | Returns |
| --- | --- | --- | --- |
| `POST` | `/auth/signup` | `RegisterUserRequestDto` | `201` + `RegisterUserResponseDto` |
| `POST` | `/auth/login` | `LoginRequestDto` | `200` + `LoginUserDto` (carries JWT) |

Protected endpoints expect `Authorization: Bearer <token>`. Authorization is ownership-based: users can access their own images and transformations; image downloads also respect the `isPublic` flag.

---

## Features implemented

### User management
- Registration, login, JWT-protected endpoints

### Image management
- `POST /api/user/uploadImage` — multipart upload, MIME allowlist (jpeg/png/gif/webp), MD5 checksum, `ImageIO` magic-byte verification, owner-scoped persistence
- `GET /api/user/images` — paginated listing (`?page=&size=&sort=`) defaulting to 20 per page, sorted by `createdAt desc`
- `GET /api/user/image/{imageId}/content` — streamed binary download (`Content-Disposition: attachment`), owner-or-public authz

### Transformation (in progress)
- `POST /api/user/image/{id}/transform` — resize via Thumbnailator. Hashes config, looks up existing transformation, serves cached result if found; otherwise runs the resize, writes the new file, and persists the metadata.

---

## Storage strategy

Originals and transformed artifacts both live on disk under `app.storage.local.root`. The application persists only a `storageKey` (path-segment) — never a full URL or absolute path.

```
~/.imagetransformer/uploads/abc/def/542986b0-a662-4aa4-...     # original
~/.imagetransformer/uploads/xyz/uvw/<another-uuid>             # transformed
```

This indirection lets a future migration to S3/R2/MinIO ship without touching the schema — only the storage adapter changes.

---

## Transformation caching

Before any transformation runs, the service computes a hash of the request config:

```
transformationHash = MD5(canonicalized(config))
```

Then queries `transformations` for an existing row with the same `(image_id, transformation_hash)`:

1. **Cache hit** → return the existing metadata. No work done.
2. **Cache miss** → run the transformation, write the output file, save a new row.

This prevents duplicate processing and storage when the same transformation is requested repeatedly.

---

## Planned API

```
# Auth
POST /auth/register
POST /auth/login

# Images
POST   /images
GET    /images
GET    /images/{id}
GET    /images/{id}/content
DELETE /images/{id}

# Transformations
POST /images/{id}/transform
GET  /transformations/{id}
GET  /transformations/{id}/content
```

---

## Planned transformation types

**Phase 1** — resize (done), rotate, format conversion
**Phase 2** — crop, grayscale, flip, mirror, compression
**Phase 3** — watermark, advanced filters

---

## Prerequisites

- JDK 17+
- (Optional) PostgreSQL 14+ if running against Postgres instead of H2

## Running locally

Default profile uses in-memory H2 — no external services needed.

```bash
./mvnw spring-boot:run
```

App boots on `http://localhost:8080`. H2 console at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:imagetransformer`).

### Switching to PostgreSQL

Edit `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/nimbusstore
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.driver-class-name=org.postgresql.Driver
spring.flyway.enabled=true
spring.jpa.hibernate.ddl-auto=validate
```

Flyway applies `V1__init.sql` on startup.

## Configuration

| Property | Default | Description |
| --- | --- | --- |
| `server.port` | `8080` | HTTP port (override via `SERVER_PORT`) |
| `app.storage.local.root` | `~/.imagetransformer/uploads` | Where image bytes live on disk |
| `app.upload.max-bytes` | `10485760` (10 MB) | Per-file upload cap |
| `jwt.secretKey` | _(hardcoded in properties)_ | **Replace before deploying.** Move to env var. |

## Build a runnable jar

```bash
./mvnw clean package
java -jar target/project-0.0.1-SNAPSHOT.jar
```

## Tests

```bash
./mvnw test
```

---

## Design principles

- **Clean layering** — controllers handle HTTP, services hold business logic, repositories own persistence. Services return plain DTOs; controllers wrap them in `ResponseEntity`.
- **Ownership-based authorization** — applied at the service layer (e.g. download requires owner OR public).
- **Immutable originals** — uploaded image rows are never mutated. Transformations are separate artifacts.
- **DTO-based API contracts** — entities never leave the service layer.
- **Storage abstraction via storageKey** — DB stores path-segments, not URLs. Backend swap is local.
- **Hash-keyed transformation cache** — duplicate work is detected before it runs.

---

## Planned future enhancements

**Infrastructure** — Docker Compose, AWS S3, Redis, RabbitMQ
**Scalability** — async transformation workers, job queues, retry mechanisms
**Security** — signed URLs, rate limiting
**Observability** — structured logging, metrics, Prometheus + Grafana
**Developer experience** — OpenAPI / Swagger, Flyway-only schema management, integration tests

---

## Project goal

Build a production-style image processing backend that demonstrates practical backend engineering skills: authentication, file management, image processing, persistence, optimization, and scalable system design.
