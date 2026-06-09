# NimbusStore

A Spring Boot image transformation service. Users sign up, upload images, and request transformations (resize, crop, format conversion, etc.) which are stored and cached by hash so identical requests are served from disk instead of recomputed.

## Tech stack

- **Java 17**, **Spring Boot 4.0.6** (Web MVC, Data JPA, Security, Validation)
- **PostgreSQL** (prod) / **H2** (in-memory, dev) — switched via `spring.datasource.*`
- **Flyway** for SQL migrations (`src/main/resources/db/migration`)
- **JWT** auth via `io.jsonwebtoken:jjwt:0.13.0`
- **Lombok** for boilerplate reduction
- **Maven** wrapper (`./mvnw`)

## Project layout

```
src/main/java/cloudinary/project/
├── ProjectApplication.java        # Spring Boot entrypoint
├── controller/AuthController.java # /auth/signup, /auth/login
├── service/                       # AuthService, JwtService, CustomUserDetailsService
├── security/                      # SecurityConfig + JwtFilter
├── repository/                    # JPA repositories (User, Image, Transformation)
├── entity/                        # JPA entities
├── dto/                           # Request/response DTOs
└── error/                         # GlobalExceptionHandler + ApiError
src/main/resources/
├── application.properties
└── db/migration/V1__init.sql      # users, images, transformations
```

## Prerequisites

- JDK 17+
- (Optional) PostgreSQL 14+ if you want to run against Postgres instead of H2

## Running locally

The default profile uses an in-memory H2 database — no external services needed.

```bash
./mvnw spring-boot:run
```

The app boots on `http://localhost:8080`. The H2 console is available at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:imagetransformer`).

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

Flyway will then apply `V1__init.sql` on startup.

## Configuration

| Property | Default | Description |
| --- | --- | --- |
| `server.port` | `8080` | HTTP port (override via `SERVER_PORT` env) |
| `app.storage.local.root` | `~/.imagetransformer/uploads` | Where uploaded images live on disk |
| `app.upload.max-bytes` | `10485760` (10 MB) | Per-file upload cap |
| `jwt.secretKey` | _(hardcoded in properties)_ | **Replace before deploying.** Move to env var. |

## API

### Auth

| Method | Path | Body | Returns |
| --- | --- | --- | --- |
| `POST` | `/auth/signup` | `RegisterUserRequestDto` (`username`, `email`, `password`) | `201` + `RegisterUserResponseDto` |
| `POST` | `/auth/login` | `LoginRequestDto` (`username`/`email`, `password`) | `200` + `LoginUserDto` with JWT |

Example:

```bash
curl -X POST http://localhost:8080/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com","password":"s3cret!"}'
```

```bash
curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"s3cret!"}'
```

Use the returned token on protected endpoints:

```
Authorization: Bearer <token>
```

## Data model

- **users** — credentials + profile
- **images** — uploaded originals (size, content-type, storage key, optional dimensions/checksum)
- **transformations** — derived outputs, keyed by `(image_id, transformation_hash)` so identical requests are deduplicated. Includes a `status` column for async processing.

See `src/main/resources/db/migration/V1__init.sql` for the canonical schema.

## Tests

```bash
./mvnw test
```

## Build a runnable jar

```bash
./mvnw clean package
java -jar target/project-0.0.1-SNAPSHOT.jar
```

## Notes / TODO

- `jwt.secretKey` is currently hardcoded — move to an env var (`JWT_SECRET_KEY`) before any non-local use.
- CSRF is disabled in `SecurityConfig` (stateless JWT API — intentional).
- Flyway is disabled in dev because H2 is used with `ddl-auto=update`; enable it when pointing at Postgres.
- Image upload / transformation endpoints are not yet wired into the controller layer — auth is the first slice.
