# AI App Builder (Lovable Clone)

Natural language prompt → React app generate → live preview.

A from-scratch implementation of an AI-powered app builder, built as a learning
and portfolio project. Backend-first, with a deliberate path from modular
monolith to microservices.

---

## Status

| Day | Scope | State |
|-----|-------|-------|
| 1 | Spring Boot 4 skeleton, health endpoint | Done |
| 2 | PostgreSQL via Docker Compose, JPA wiring | Done |
| 3 | JWT auth (signup / login / me), global error handling | Done |
| 4 | Project CRUD with ownership enforcement | Done |
| 5 | Spring AI + Groq LLM, code generation | Next |
| 6+ | File storage (MinIO), RAG (Qdrant), live preview (K8s) | Planned |

---

## Stack

**Current:** Java 17 · Spring Boot 4.0.0 · Spring Security · Spring Data JPA ·
PostgreSQL 16 · JJWT · Docker Compose

**Planned:** Spring AI 2.0 · Groq (Llama 3.3) · Redis · MinIO · Qdrant ·
React + Vite · Kubernetes (k3s)

---

## Running locally

### Prerequisites

- Java 17+ (Boot 4 minimum; project default JDK may be older — see below)
- Docker Desktop running
- Maven 3.9+

### 1. Start PostgreSQL

```bash
docker compose up -d
```

Postgres listens on `localhost:5432` (db/user `aibuilder`). Data persists in a
named volume across restarts.

### 2. Set the JWT secret

The signing key is **not** committed. Export it before starting the app:

```bash
export JWT_SECRET_KEY="any-long-random-string-at-least-32-chars-long"
```

### 3. Run the backend

```bash
# Only needed if your default `java` is not 17+
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

cd backend
mvn -s .mvn/settings.xml spring-boot:run
```

App starts on **http://localhost:8081**.

Hibernate runs with `ddl-auto: update`, so tables are created on first boot.

> When behaviour doesn't match the source, run `mvn clean spring-boot:run`.
> Maven's incremental compile can silently reuse stale `.class` files.

---

## API

Base URL: `http://localhost:8081`

### Public

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/health` | Liveness check |
| `POST` | `/api/auth/signup` | Register, returns JWT (`201`) |
| `POST` | `/api/auth/login` | Authenticate, returns JWT (`200`) |

### Authenticated

Send `Authorization: Bearer <token>`.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/auth/me` | Current user id |
| `POST` | `/api/projects` | Create project (`201`) |
| `GET` | `/api/projects` | List caller's projects |
| `GET` | `/api/projects/{id}` | Get one project |
| `DELETE` | `/api/projects/{id}` | Delete project (`204`) |

Errors return a consistent shape:

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Username already exists: alice",
  "timestamp": "2026-08-13T06:04:00Z"
}
```

### Quick smoke test

```bash
TOKEN=$(curl -s -X POST localhost:8081/api/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"secret123","name":"Alice"}' \
  | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')

curl -s -X POST localhost:8081/api/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"My first app","description":"A todo list"}'
```

---

## Project structure

```
Lovable_Clone/
├── backend/
│   └── src/main/java/com/aibuilder/lovableclone/
│       ├── account/      ← users, auth
│       ├── workspace/    ← projects, files
│       └── common/       ← security, config, exceptions
├── docker-compose.yml    ← PostgreSQL
├── frontend/             ← React (not started)
└── doc/                  ← third-party course reference, not part of the build
```

Each feature package owns its own `controller` / `service` / `repository` /
`entity` / `dto` layers.

---

## Design decisions

**Modular monolith, not microservices (yet).** Packages are split along the
seams a future service split would follow (`account`, `workspace`). Cross-module
references are by **id only** — `ProjectEntity` holds a `Long ownerId` rather
than a JPA relation to `UserEntity`. A foreign key would not survive the split
into separate databases; an id does.

**Ownership lives in the query, not in an `if`.** Repositories expose
`findByIdAndOwnerId(...)` instead of `findById(...)` followed by a comparison.
This makes IDOR impossible to forget rather than merely discouraged.
Requests for another user's project return **404, not 403** — a 403 would
confirm the resource exists.

**Stateless JWT.** No server-side sessions, so instances scale horizontally
without sticky routing or a shared session store.

**Services take `userId` as a parameter.** Nothing in the service layer reads
`SecurityContextHolder`; only controllers do, via `AuthUtil`. Services stay
callable from schedulers and message consumers, and unit tests need no
security mocking.

**Read-only transactions by default.** `ProjectService` is annotated
`@Transactional(readOnly = true)` at class level, with write methods overriding
it. The safe case is the default.

**Enums persist as strings.** `@Enumerated(EnumType.STRING)` — the default
`ORDINAL` corrupts existing rows the moment an enum constant is reordered.

**Errors are centralised.** A single `@RestControllerAdvice` maps domain
exceptions to status codes and returns a uniform `ApiErrorDto`. Unexpected
exceptions are logged with a stack trace but respond with a generic message,
so internals never leak to clients.

---

## Known gaps

- No automated tests yet
- `ddl-auto: update` instead of a migration tool (Flyway/Liquibase)
- No refresh tokens or token revocation
- No rate limiting on auth endpoints
- CORS not configured (needed once the frontend exists)
