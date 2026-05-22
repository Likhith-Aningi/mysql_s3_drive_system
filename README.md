# Drive System

A personal cloud file storage application. Files are uploaded directly from the browser to S3 using presigned URLs — they never pass through the backend server. CloudFront delivers files for fast, permanent sharing links.

---

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 4.0 · Java 21 · Spring Security 7 · JWT |
| Database | MySQL 8 (production) · H2 in-memory (tests) |
| Storage | AWS S3 (direct browser upload via presigned URLs) |
| CDN | AWS CloudFront (permanent share links) |
| Frontend | React 19 · Vite 6 · Tailwind CSS 4 · Axios |

---

## Architecture

### Upload flow

```
Browser  →  POST /api/files/upload/initiate  →  Backend
                                                  ├── saves FileMetadata (status: PENDING)
                                                  └── returns presigned PUT URL + fileMetadataId

Browser  →  PUT <presigned S3 URL>  →  S3 directly (never touches backend)

Browser  →  POST /api/files/{id}/confirm  →  Backend
                                              ├── HeadObject check (verifies file landed in S3)
                                              └── marks FileMetadata COMPLETED
```

Files with `PENDING` status older than 20 minutes are purged automatically by a background scheduler (`FileCleanupService`), along with their S3 objects. This handles network drops, browser closes mid-upload, and presigned URL expiry.

### Download / share

- **Download**: uses `cloudfrontUrl` directly from the listing response (no extra API call). Falls back to a 1-hour presigned S3 GET for files without a CloudFront URL.
- **Share → expiring**: 7-day presigned S3 URL via `GET /api/files/{id}/share`
- **Share → permanent**: CloudFront URL served directly from the cached file object (no API call)

---

## Project structure

```
mysql_s3_drive_system/
├── backend/          Spring Boot application
│   └── src/
│       ├── main/java/com/drive/
│       │   ├── config/          SecurityConfig, S3Config, SwaggerConfig
│       │   ├── controller/      AuthController, FileController, GlobalExceptionHandler
│       │   ├── dto/             Request/response DTOs
│       │   ├── entity/          User, FileMetadata, UploadStatus
│       │   ├── repository/      UserRepository, FileRepository
│       │   ├── security/        JwtUtil, JwtAuthFilter, UserDetailsServiceImpl
│       │   └── service/         AuthService, FileService, S3Service, FileCleanupService
│       └── test/
│           ├── java/com/drive/
│           │   ├── controller/  AuthControllerTest, FileControllerTest
│           │   └── service/     FileServiceTest, FileCleanupServiceTest
│           └── resources/
│               ├── application.yml   H2 config for tests
│               └── data.sql          seed users + files
└── frontend/         React application
    └── src/
        ├── components/
        │   ├── auth/            Login, Register
        │   ├── dashboard/       Dashboard, FileCard, UploadModal, ShareModal, PreviewModal
        │   └── shared/          Navbar, ProtectedRoute
        ├── context/             AuthContext, ThemeContext
        ├── hooks/               useFiles
        └── services/            api.js (Axios + JWT interceptor), fileService.js
```

---

## Local development

### Prerequisites

- Java 21+
- Maven (or use the included `mvnw.cmd`)
- Node.js 20+ and npm 10+
- MySQL 8 running locally
- AWS account with an S3 bucket and (optionally) a CloudFront distribution

### 1. Configure backend secrets

Create `backend/src/main/resources/application-local.yml` (this file is gitignored):

```yaml
cloudfront:
  url: "https://<distribution>.cloudfront.net"   # leave empty string if not using CloudFront

spring:
  datasource:
    password: <mysql-password>

jwt:
  secret: <64-char-hex-string>

aws:
  s3:
    bucket: <your-bucket-name>
  credentials:
    access-key: <aws-access-key-id>      # omit both to use DefaultCredentialsProvider (IAM / ~/.aws)
    secret-key: <aws-secret-access-key>
```

Non-secret defaults (DB URL `localhost:3306/drive_db`, region `ap-south-1`, CORS origins, port 8080) are in the committed `application.yml`.

### 2. S3 bucket CORS

Add this CORS rule to your S3 bucket so the browser can PUT directly:

```json
[
  {
    "AllowedHeaders": ["*"],
    "AllowedMethods": ["PUT"],
    "AllowedOrigins": ["http://localhost:5000"],
    "ExposeHeaders": []
  }
]
```

### 3. Start the backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

### 4. Start the frontend

```bash
cd frontend
npm install
npm run dev      # http://localhost:5000  (proxies /api → localhost:8080)
```

---

## API reference

### Auth — `/api/auth` (no token required)

| Method | Path | Body | Response |
|--------|------|------|----------|
| POST | `/register` | `{ name, email, password }` | `{ token, email, name }` 201 |
| POST | `/login` | `{ email, password }` | `{ token, email, name }` 200 |

Validation: `name` 2–100 chars, `password` 8–100 chars, `email` must be valid format.

### Files — `/api/files` (Bearer token required)

| Method | Path | Query / Body | Response |
|--------|------|------|----------|
| POST | `/upload/initiate` | `{ fileName, contentType, fileSize }` | `{ uploadUrl, s3Key, fileMetadataId }` |
| POST | `/{id}/confirm` | — | 200 |
| GET | `/` | — | `FileMetadataDto[]` (COMPLETED only, newest first) |
| GET | `/{id}/download` | — | `{ url }` |
| GET | `/{id}/share` | `?linkType=expiring\|permanent` | `{ url, linkType }` |
| DELETE | `/{id}` | — | 204 |

All file endpoints are owner-scoped — users can only access their own files.

`FileMetadataDto` fields: `id`, `originalName`, `contentType`, `fileSize`, `uploadedAt`, `cloudfrontUrl`.

### Error responses

All errors use [RFC 9457 Problem Detail](https://www.rfc-editor.org/rfc/rfc9457) format. The `detail` field contains the human-readable message.

| Exception | HTTP status |
|---|---|
| Validation failure | 400 |
| `IllegalStateException` | 400 |
| `BadCredentialsException` | 401 |
| `AccessDeniedException` | 403 |
| `NoSuchElementException` | 404 |
| `IllegalArgumentException` | 409 |

---

## Running tests

```powershell
cd backend

# All tests (36 total)
.\mvnw.cmd test

# Single class
.\mvnw.cmd test -Dtest=FileServiceTest

# Single method
.\mvnw.cmd test -Dtest=FileServiceTest#deleteFile_success
```

Tests use H2 in-memory — no MySQL or AWS credentials needed. `S3Client` and `S3Presigner` are mocked in the context load test.

---

## Data model

```
users
  id           BIGINT PK
  email        VARCHAR(100) UNIQUE
  name         VARCHAR(100)
  password     VARCHAR (BCrypt hash)
  created_at   DATETIME

file_metadata
  id             BIGINT PK
  original_name  VARCHAR
  s3_key         VARCHAR UNIQUE   (users/{userId}/{uuid}/{fileName})
  content_type   VARCHAR
  file_size      BIGINT
  cloudfront_url VARCHAR (nullable)
  upload_status  VARCHAR(20)      PENDING | COMPLETED
  owner_id       BIGINT FK → users.id
  uploaded_at    DATETIME
```

---

## Features

- JWT authentication (register / login)
- Direct-to-S3 browser upload with 15-minute presigned PUT URLs
- Upload confirmation via HeadObject check — no ghost files
- Automatic cleanup of stale PENDING uploads (every 5 min, purges rows > 20 min old)
- CloudFront permanent share links + 7-day expiring S3 share links
- File listing, download, and delete
- Dark / light theme toggle with localStorage persistence
- Fully responsive UI
