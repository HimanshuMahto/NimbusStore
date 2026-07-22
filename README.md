# NimbusStore

A backend image processing service inspired by Cloudinary — upload images, apply composable transformations (resize, crop, rotate, filters, watermark, format conversion, compression), and download the results. Built with Java 17 + Spring Boot.

Authenticated users own their images. Transformations are hash-deduplicated so identical requests are served from disk instead of recomputed. Each transformation produces a new artifact — original files are immutable.

Built as a portfolio implementation of the [roadmap.sh Image Processing Service](https://roadmap.sh/projects/image-processing-service) project.

---

## Tech stack

**Backend**
- Java 17, Spring Boot (Web MVC, Data JPA, Security, Validation)
- Spring Security + stateless JWT (`io.jsonwebtoken:jjwt`)
- Hibernate via Spring Data JPA

**Image processing**
- [Thumbnailator](https://github.com/coobird/thumbnailator) — pipeline for all 9 transformation operations
- Custom `BufferedImageOp` filters for grayscale and sepia

**Persistence**
- PostgreSQL 16
- Jackson JSON (`jsonb`) for transformation configs

**API documentation**
- OpenAPI 3 + Swagger UI via `springdoc-openapi`

**Storage**
- Local filesystem with `storageKey` indirection (S3/R2 migration is a single-adapter swap)

**Build**
- Maven (wrapper: `./mvnw`)

---

## Quick start

**Prerequisites**: JDK 17+, Docker (for Postgres).

```bash
# 1. Start Postgres
docker run --name image-pg \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=nimbusstore \
  -p 5432:5432 \
  -d postgres:16

# 2. Run the app
./mvnw spring-boot:run
```

Open **http://localhost:8080/swagger-ui/index.html** for the interactive API browser.

---

## Features

### Auth
- Signup / login with bcrypt-hashed passwords
- Stateless JWT (`Authorization: Bearer <token>`)
- Client-side logout endpoint (server-side token invalidation deferred)
- `/me` — get/update your profile

### Image management
- Multipart upload with MIME allowlist (`image/{jpeg, png, webp, gif}`)
- Magic-byte validation via `ImageIO` (rejects mislabeled files)
- MD5 checksum stored per image
- Paginated listing (`?page=&size=&sort=`)
- Streamed binary download (`Content-Disposition: attachment`)
- Metadata fetch, delete-with-cascade (deletes all transformations + files)
- Owner-or-public visibility model

### Transformations — all 9 operations, composable
| Operation | Parameters |
|---|---|
| **Resize** | `width`, `height` |
| **Crop** | `x`, `y`, `width`, `height` |
| **Rotate** | `rotate` (degrees, ±360) |
| **Compress** | `quality` (0–100) |
| **Format** | `JPEG` / `PNG` / `WEBP` / `GIF` |
| **Flip** | `flipVertical` |
| **Mirror** | `mirror` (horizontal) |
| **Grayscale** | `grayscale` |
| **Sepia** | `sepia` |
| **Watermark** | `imageId`, `position` (9 anchors), `opacity` |

Multiple operations can be requested in one call and are applied in a canonical pipeline order:

```
crop → resize → rotate → filters → flip/mirror → watermark → format → compress
```

### Transformation caching
Every request's config is MD5-hashed. Before running any work, the service looks up `(image_id, transformation_hash)`. Cache hit → return the existing artifact. Cache miss → run the transformation and persist.

This deduplicates both **compute** and **storage** — identical transformations of the same source are stored once.

### Rate limiting
`POST /api/images/{id}/transformations` is rate-limited to **60 requests / minute / user** via a PostgreSQL fixed-window counter. Excess requests receive `429 Too Many Requests` with a `Retry-After: 60` header.

Enforced by a Spring `HandlerInterceptor` scoped to the transformation endpoint; other endpoints are unaffected.

### API documentation
Interactive Swagger UI at `/swagger-ui/index.html`. OpenAPI 3 spec at `/v3/api-docs`. Bearer-auth is wired in the UI — click **Authorize**, paste a JWT, and every "Try it out" call carries the token.

---

## API reference

### Auth (public)
| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/signup` | Register a new user |
| `POST` | `/auth/login` | Exchange credentials for a JWT |
| `POST` | `/auth/logout` | No-op stub; client discards the token |

### User (authenticated)
| Method | Path | Description |
|---|---|---|
| `GET` | `/api/me` | Current user's profile |
| `PATCH` | `/api/me` | Update profile |

### Images (authenticated)
| Method | Path | Description |
|---|---|---|
| `POST` | `/api/images` | Multipart upload (`file`, optional `metadata`) |
| `GET` | `/api/images` | Paginated list (owner-scoped) |
| `GET` | `/api/images/{imageId}` | Single image metadata |
| `GET` | `/api/images/{imageId}/content` | Download original bytes |
| `DELETE` | `/api/images/{imageId}` | Delete image + cascade transformations |

### Transformations (authenticated)
| Method | Path | Description |
|---|---|---|
| `POST` | `/api/images/{imageId}/transformations` | Create a transformation (**rate-limited**) |
| `GET` | `/api/transformations` | Paginated list of all my transformations |
| `GET` | `/api/images/{imageId}/transformations` | Paginated list for one image |
| `GET` | `/api/transformations/{transformationId}` | Single transformation metadata |
| `GET` | `/api/transformations/{transformationId}/content` | Download transformed bytes |
| `DELETE` | `/api/transformations/{transformationId}` | Delete transformation + file |

Authorization is ownership-based. Downloads of transformations also honor the source image's `isPublic` flag.

---

## Project layout

```
src/main/java/cloudinary/project/
├── ProjectApplication.java
├── controller/          # Auth, User, Image, Transformation controllers
├── service/             # AuthService, ImageService, TransformationService, JwtService
├── security/            # SecurityConfig, JwtFilter, RateLimitInterceptor
├── config/              # OpenApiConfig, WebConfig, custom filter classes
├── repository/          # JPA repositories (User, Image, Transformation, RateLimiter)
├── entity/              # JPA entities
├── dto/                 # Request/response DTOs (typed, validated)
└── error/               # Global exception handler + error envelope
src/main/resources/
└── application.properties
```

---

## Architecture

Modular monolith with four modules:

- **Authentication** — signup/login, JWT issue and validate, filter-based enforcement.
- **User** — profile management (`/me` endpoints).
- **Image** — upload, storage, retrieval, listing, delete-with-cascade. Original images are **immutable after upload**.
- **Transformation** — Thumbnailator-backed operation pipeline, hash-keyed cache, derived-artifact storage, listing, retrieval, delete.

Each layer respects clean separation:
- Controllers know HTTP — they extract params, call services, wrap results in `ResponseEntity`.
- Services own business logic — validation, authz, transformation, persistence orchestration. Return plain DTOs.
- Repositories own SQL — Spring Data queries + one native query for the rate-limit upsert.

---

## Data model

### User
Represents an authenticated platform user. One user owns many images.

| Field | Notes |
|---|---|
| `id` | PK |
| `username`, `email` | unique |
| `passwordHash` | bcrypt |
| `createdAt`, `updatedAt` | timestamps |

### Image
An original uploaded image. Metadata only — bytes live on disk under `storageKey`. Immutable after upload.

| Field | Notes |
|---|---|
| `id` | PK |
| `user` | FK → users |
| `fileName`, `contentType`, `fileSize` | original upload metadata |
| `storageKey` | path segment (e.g. `abc/def/uuid-1`) — never an absolute path |
| `width`, `height` | probed via `ImageIO` at upload |
| `checksum` | MD5 of the uploaded bytes |
| `isPublic` | visibility flag |
| `createdAt`, `updatedAt` | timestamps |

### Transformation
A derived artifact produced from an image + config. Belongs to one image.

| Field | Notes |
|---|---|
| `id` | PK |
| `image` | FK → images |
| `transformationHash` | MD5 of the canonicalized config |
| `transformationConfig` | `jsonb` — the full request as structured data |
| `outputStorageKey` | path segment for the derived file |
| `outputContentType`, `outputFileSize` | derived-artifact metadata |
| `status` | `PENDING` / `PROCESSING` / `COMPLETED` / `FAILED` (currently sync — only `COMPLETED` used) |
| `errorMessage` | populated on `FAILED` (unused today) |
| `createdAt`, `updatedAt` | timestamps |

Unique constraint on `(image_id, transformation_hash)` enforces cache semantics at the DB level.

### RateLimit
Tracks per-user request counters in fixed-minute windows.

| Field | Notes |
|---|---|
| `userId`, `windowStart` | composite PK |
| `requestCount` | int |

Rows accumulate one per (user, minute); a daily cleanup job (planned) will evict rows older than the retention window.

---

## Storage strategy

Bytes live on disk under `${app.storage.local.root}` (default: `~/.imagetransformer/uploads`). The DB persists only a **storage key** — a path segment like `abc/def/542986b0-a662-4aa4-...` — never an absolute path or URL.

```
~/.imagetransformer/uploads/abc/def/uuid-original      # ImageEntity.storageKey
~/.imagetransformer/uploads/xyz/uvw/uuid-transformed   # TransformationEntity.outputStorageKey
```

**Why this shape**: migrating to object storage (S3, R2, MinIO) becomes a code-only change. The DB schema stays; only the file-read/write adapter is replaced.

---

## Transformation pipeline

Every request flows through:

1. **Fetch source image** (`orElseThrow` → 404) + authz (owner only for create).
2. **Compute hash** — MD5 of the request DTO.
3. **Cache lookup** — `SELECT` by `(image_id, hash)`. Present → return; done.
4. **Build Thumbnailator chain** — conditional operations in canonical order.
5. **Execute** via `.toOutputStream(...)` to the exact `outputStorageKey` (avoids Thumbnailator's extension-appending quirk).
6. **Persist** `TransformationEntity` with hash, config map, output metadata, status `COMPLETED`.
7. **Return** the DTO.

The pipeline always specifies both a sizing operation (defaulting to `scale(1.0)` if no resize) and an explicit output format (defaulting to the source's), guaranteeing Thumbnailator has everything it needs.

---

## Rate limiting

**Algorithm**: fixed-window counter, atomic in one SQL statement:

```sql
INSERT INTO rate_limits (user_id, window_start, request_count)
VALUES (:userId, date_trunc('minute', now()), 1)
ON CONFLICT (user_id, window_start)
DO UPDATE SET request_count = rate_limits.request_count + 1
RETURNING request_count;
```

**Chosen over Redis/token-bucket** because:
- No new infrastructure required — PG is already in the stack.
- `INSERT ... ON CONFLICT ... RETURNING` is atomic in a single round-trip.
- The boundary-burst trade-off of fixed-window is negligible at 60 req/min/user.
- The `HandlerInterceptor` abstraction leaves the door open to swap in Redis (`Bucket4j`) if scale demands it.

Enforced via a Spring `HandlerInterceptor` registered for `/api/images/*/transformations` only. Other endpoints don't touch the rate-limit table.

---

## Configuration

All settings are overridable via environment variables. Defaults are safe for local dev:

| Env var | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `STORAGE_ROOT` | `~/.imagetransformer/uploads` | On-disk file root |
| `UPLOAD_MAX_BYTES` | `10485760` (10 MB) | Per-file upload cap |
| `JWT_SECRET_KEY` | fallback (dev-only) | **Set before deploying anywhere non-local** |

Database credentials are currently hardcoded in `application.properties`; move to env vars in the same style before deploying.

---

## Running locally

**Postgres** (via Docker):
```bash
docker run --name image-pg \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=nimbusstore \
  -p 5432:5432 \
  -d postgres:16
```

Container data lives inside the container. For durable data across `docker rm`, mount a volume:
```bash
docker run ... -v image-pg-data:/var/lib/postgresql/data ...
```

**Spring app**:
```bash
./mvnw spring-boot:run
```

Boots on `http://localhost:8080`. Hibernate creates tables on first startup (`ddl-auto=update`).

**Verify data**:
```bash
docker exec -it image-pg psql -U postgres -d nimbusstore -c "\dt"
```

---

## Build a runnable jar

```bash
./mvnw clean package
java -jar target/project-0.0.1-SNAPSHOT.jar
```

---

## Design principles

- **Clean layering** — HTTP concerns stay in controllers, business logic in services, SQL in repositories. Services return plain DTOs; controllers wrap into `ResponseEntity`.
- **Ownership-based authz** — applied at the service layer. Reads honor `isPublic`; writes are owner-only.
- **Originals are immutable** — every transformation produces a new artifact; the source is never overwritten.
- **Typed, validated DTOs** — request bodies are strongly typed with nested DTOs (`ResizeDto`, `CropDto`, `WatermarkDto`, etc.) and Bean Validation constraints on every field.
- **Storage indirection** — DB stores path segments, not URLs. Backend swap is a code-only change.
- **Hash-keyed cache** — expensive transformations are computed once per unique config.
- **Fail-fast validation** — MIME allowlist, magic-byte check, size limits enforced at upload boundary.
- **Composable transformations** — one request can chain multiple operations; canonical order guarantees deterministic output.

---

## Spec coverage — roadmap.sh Image Processing Service

| Requirement | Status |
|---|---|
| User signup / login with JWT | ✅ |
| Upload image (multipart) | ✅ |
| Transform image (composable) | ✅ |
| Get image by ID | ✅ |
| List images paginated | ✅ |
| Resize | ✅ |
| Crop | ✅ |
| Rotate | ✅ |
| Watermark | ✅ |
| Flip / Mirror | ✅ |
| Compress | ✅ |
| Format conversion | ✅ |
| Grayscale / Sepia filters | ✅ |
| Rate limiting on transformations | ✅ |
| Caching of transformed images | ✅ (hash-keyed) |
| Input validation + error handling | ✅ |
| Cloud storage integration | 🟡 (design-ready via `storageKey`; local-only impl today) |
| Async processing (optional) | 🟡 (status enum ready; sync execution today) |

---

## Roadmap

Next up, in priority order:

- **`StorageService` interface** — extract behind `LocalStorageService` implementation; open the door to `S3StorageService` without touching services or controllers.
- **Global error envelope polish** — populate `statusCode` from the exception type consistently.
- **Repository + controller tests** — `@DataJpaTest`, `@WebMvcTest`, MockMvc for happy paths and authz.
- **GitHub Actions CI** — `./mvnw verify` on every PR.
- **Docker Compose for the app** — one-command clone-and-run.
- **Refresh tokens** — currently client-side logout with 1-hour access tokens. Refresh flow is a future upgrade.
- **Async transformation pipeline** — Spring `@Async` or a message queue; the `TransformationStatus` enum's other values become load-bearing.
- **Signed download URLs** — share-a-link without a Bearer header.
- **Observability** — structured logging, Prometheus metrics, Grafana dashboards.

---

## Project goal

Demonstrate practical backend engineering: clean architecture, ownership-based authorization, composable domain operations, hash-keyed idempotency, real database interactions, and a thoughtful set of abstractions that support scaling from a single-instance dev environment to production infrastructure — without rewrites.
