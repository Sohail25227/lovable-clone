# AI App Builder (Lovable Clone)

Natural language prompt → React app generate → live preview.

A from-scratch implementation of an AI-powered app builder, built as a learning
and portfolio project. Backend-first, with a deliberate path from modular
monolith to microservices.

**Live:** [lovable-clone-indol.vercel.app](https://lovable-clone-indol.vercel.app) ·
API at [lovable-clone-api.onrender.com](https://lovable-clone-api.onrender.com/api/health)

Both run on free plans, so the first request after a quiet spell wakes the backend
and takes about a minute. Everything after that is normal speed.

---

## Status

| Day | Scope | State |
|-----|-------|-------|
| 1 | Spring Boot 4 skeleton, health endpoint | Done |
| 2 | PostgreSQL via Docker Compose, JPA wiring | Done |
| 3 | JWT auth (signup / login / me), global error handling | Done |
| 4 | Project CRUD with ownership enforcement | Done |
| 5 | Spring AI + Groq, prompt → runnable app via structured output | Done |
| 6 | Persist generated files, status transitions, optimistic locking | Done |
| 6b | Validate generated output, retry once on violations | Done |
| 6c | Live preview: generated apps run in the browser | Done |
| 6d | Prompt history, follow-up prompts refining an existing app | Done |
| 6e | Generation interlock, bounded retry budget | Done |
| 7 | React frontend: auth, projects, builder with live preview | Done |
| 7b | Flyway owns the schema, Hibernate only validates it | Done |
| 7c | Generation claims expire, so a crash cannot brick a project | Done |
| 8 | Backend containerised, production profile, image verified end to end | Done |
| 8b | Deployment config: Render blueprint, Vercel SPA rewrite | Done |
| 8c | Hosted: Neon Postgres, backend on Render, frontend on Vercel | Done |
| 8d | JVM tuned to fit 512 MB after a production OOM; startup 3x faster | Done |
| 9+ | File storage (MinIO), RAG (Qdrant), Kubernetes | Planned |

---

## Stack

**Current:** Java 17 · Spring Boot 4.0.0 · Spring Security · Spring Data JPA ·
PostgreSQL 16 · Flyway 11 · JJWT · Spring AI 2.0 · Groq (Llama 3.3 70B) ·
Docker Compose · React 19 · Vite 8 · Tailwind 4 · React Router 7

**Planned:** Redis · MinIO · Qdrant · Kubernetes (k3s)

Groq is reached through the OpenAI-compatible starter, so the provider is a
`base-url` change rather than a code change.

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

### 2. Set the secrets

Neither is committed, and neither has a default — the app fails to start
rather than run with a placeholder.

```bash
export JWT_SECRET_KEY="any-long-random-string-at-least-32-chars-long"
export GROQ_API_KEY="gsk_..."   # free key from https://console.groq.com
```

> On a network that intercepts TLS (corporate proxies such as Zscaler), calls
> to Groq fail with `PKIX path building failed` while `curl` to the same URL
> succeeds. `curl` trusts the OS keychain; the JVM trusts its own `cacerts`.
> Import the proxy's root CA into `$JAVA_HOME/lib/security/cacerts`.

### 3. Run the backend

```bash
# Only needed if your default `java` is not 17+
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

cd backend
mvn -s .mvn/settings.xml spring-boot:run
```

App starts on **http://localhost:8081**.

Flyway builds the schema on first boot, so an empty database is enough to start.

> When behaviour doesn't match the source, run `mvn clean spring-boot:run`.
> The IDE's compiler and Maven both write to `target/classes`, so a save
> caught mid-write can leave a truncated `.class` file. Maven then skips it,
> because its incremental check compares timestamps rather than content, and
> the build stays green. `javap -p target/classes/...` shows the truth.

### 4. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

UI on **http://localhost:5173**. No dev proxy: the browser talks to the backend
cross-origin, so CORS is exercised in development rather than discovered in
production. The port is pinned with `strictPort`, because the backend allows exactly
one origin and a silent fallback to `5174` would surface as a confusing CORS failure.

Both sides read the same origin from one place. Change it and change both:

```bash
export FRONTEND_ORIGIN="http://localhost:5173"   # backend: CORS + preview frame-ancestors
# frontend/.env → VITE_API_BASE_URL=http://localhost:8081
```

### 5. Optional: run the backend as a container

This is what a host runs, so it is worth exercising before deploying. Tests run inside
the build, so the image only exists if the suite passes.

```bash
cd backend
docker build -t lovable-backend:test .

docker run --rm \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e PORT=9000 \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://host.docker.internal:5432/aibuilder" \
  -e SPRING_DATASOURCE_USERNAME=aibuilder \
  -e SPRING_DATASOURCE_PASSWORD=aibuilder123 \
  -e JWT_SECRET_KEY="$JWT_SECRET_KEY" \
  -e GROQ_API_KEY="$GROQ_API_KEY" \
  -p 8082:9000 \
  lovable-backend:test
```

`host.docker.internal` is how the container reaches Postgres on the host; `localhost`
inside a container is the container. The `PORT` and published-port mismatch above is
deliberate — it proves the app follows `PORT` rather than the hardcoded `8081` a host
would kill it for.

Behind a TLS-intercepting proxy, generation fails inside the container with a PKIX error
even though it works outside: the host JDK has had the proxy's CA imported and the
container's has not. Everything else works, and generation is not worth breaking the
image over — see the design note below.

---

## Deploying

Three pieces, three hosts, all on free plans: Postgres on **Neon**, the backend container
on **Render**, the built frontend on **Vercel**.

The database is deliberately not on Render. A free Render Postgres expires 30 days after
it is created and is deleted 14 days later, which is the wrong shape for something meant
to stay up. Neon's free tier sleeps when idle instead of expiring, which is why
`application-prod.yml` keeps `connection-timeout` at 30 seconds and `minimum-idle` at 1.

Order matters, because two of the three need a URL the others have not produced yet:
database, then backend, then frontend, then one last edit to the backend.

### 1. Database (Neon)

Create a project in the **Singapore** region, matching Render below. Neon hands back a
connection string; Spring needs it split into three, and one part of it changed:

```
postgresql://alice:secret@ep-cool-name.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
           └─user┘ └pass┘ └──────────────── host ────────────────────┘ └db┘

SPRING_DATASOURCE_URL       jdbc:postgresql://ep-cool-name.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
SPRING_DATASOURCE_USERNAME  alice
SPRING_DATASOURCE_PASSWORD  secret
```

Two things to get right in that URL:

- **Take the direct connection string, not the pooled one.** Neon's pooled endpoint (the
  host with `-pooler` in it) is PgBouncer in transaction mode, where a session is not
  guaranteed to be the same connection twice. Flyway takes a session-level advisory lock
  around migrations, so it can hang or fail there. The app brings its own pool anyway.
- **Keep `sslmode=require`, drop anything else Neon appends.** `channel_binding` is a
  libpq parameter that the JDBC driver does not understand.

Nothing needs to be created inside the database. Flyway builds the schema on first boot.

### 2. Backend (Render)

The repo has a `render.yaml`, so this is a **Blueprint**, not a hand-made service: New →
Blueprint → pick the repo. It reads the file and asks only for the values marked
`sync: false`.

| Variable | Value |
|----------|-------|
| `SPRING_DATASOURCE_URL` / `USERNAME` / `PASSWORD` | from Neon, above |
| `GROQ_API_KEY` | the same key used locally |
| `FRONTEND_ORIGIN` | not known yet — put `http://localhost:5173` and fix it in step 4 |

`JWT_SECRET_KEY` is not asked for: Render generates a 256-bit value and keeps it, which
is what `generateValue: true` in the blueprint means. `SPRING_PROFILES_ACTIVE=prod` and
`PORT` are handled without asking.

The first build takes several minutes, because it compiles and runs the test suite inside
the image. Watch the logs for Flyway applying the migrations to the empty Neon database —
that is the moment the schema is created. Then:

```bash
curl https://<your-service>.onrender.com/api/health
```

### 3. Frontend (Vercel)

Import the repo, set the root directory to `frontend`, and add one environment variable:

```
VITE_API_BASE_URL = https://<your-service>.onrender.com
```

Vite inlines that at build time, not at runtime, so changing it later means redeploying
rather than restarting.

`frontend/vercel.json` rewrites every path to `index.html`. Without it, `/projects/5`
loads fine when React Router navigates to it and 404s when the page is refreshed, because
no such file exists — the routing lives in the bundle, and the bundle has to be served
first.

### 4. Point the backend at the frontend

Set `FRONTEND_ORIGIN` on Render to the Vercel URL, with no trailing slash, and let it
redeploy. This one value becomes both the allowed CORS origin and the preview's
`frame-ancestors`, so until it is right, every API call fails CORS and the preview iframe
stays blank.

### What to expect on the free tier

The backend sleeps after 15 minutes without traffic, and the next request spends about a
minute waking it. Neon's compute sleeps too, and wakes in seconds. Neither is worth paying
to avoid, but it is worth opening the link once before showing it to anyone.

Groq's free tier is the limit that actually bites: 100,000 tokens a day for
`llama-3.3-70b-versatile`, against roughly 10,000 per generation, which is about **ten
builds a day**. Past that the API answers `429` with the wait until the budget refills —
`Limit 100000, Used 95017, Requested 10003. Please try again in 1h12m17s` — and the app
surfaces it as a `429` carrying that wait, leaving the project's existing files and status
untouched. Worth knowing before a demo, because the quota is per day, not per session.

### What the first deploy actually showed

The build took four minutes and the app came up on the first attempt, which was mostly
luck of the preparation rather than luck: the two things that would have broken it had
already been fixed while containerising.

Three lines in the startup log are worth reading, because each one is a decision paying
off:

- `Tomcat started on port 10000`. Render picks the port and passes it in `PORT`. Had the
  config kept its hardcoded `8081`, Render would have scanned, found nothing listening,
  and killed the service — the `No open ports detected, continuing to scan...` line is
  that scan, ending the moment Tomcat bound.
- `All configured schemas are empty; baseline operation skipped`, then `Migrating schema
  "public" to version "1 - initial schema"`. Because the database was untouched, Flyway
  ran V1 rather than baselining over it. This is the concrete reason to refuse anything
  that pre-creates tables: with a non-empty schema and no history table,
  `baseline-on-migrate` would have marked it as already at V1, skipped it, and left V2
  altering a table that was never created.
- `Started LovableCloneApplication in 139.6 seconds`, against 5.9 locally. That is 0.1
  CPU, and it is the honest cost of the free tier. A second deploy logged `Schema
  "public" is up to date. No migration necessary.`, which is the migrations proving they
  are idempotent.

One thing this network could not verify at any point: the corporate proxy re-signs TLS
and drops Postgres connections, so neither Groq nor Neon could be reached from the laptop,
in a container or out of it. Both worked on the first try from Render, which does not sit
behind it. Local verification stopped at "the container boots and serves HTTP"; the rest
was verified in the environment that actually has to run it.

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
| `POST` | `/api/projects/{id}/generate` | Prompt → generated app (`200`) |
| `GET` | `/api/projects/{id}/files` | Stored files for a project |
| `GET` | `/api/projects/{id}/messages` | Prompt history, oldest first |
| `POST` | `/api/projects/{id}/preview-token` | Mint a 30-minute preview URL |

### Preview

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/preview/{token}/` | The generated `index.html` |
| `GET` | `/api/preview/{token}/{file}` | Any stored file for that project |

No `Authorization` header — the signed token in the path is the credential. Paste
the `previewUrl` from `preview-token` into a browser and the app runs.

`generate` takes `{"prompt": "..."}` and returns an app that runs in a browser
with no build step:

```json
{
  "appName": "Todo List",
  "summary": "Add, complete, filter and delete tasks.",
  "files": [
    { "path": "index.html", "content": "..." },
    { "path": "app.jsx",    "content": "..." },
    { "path": "styles.css", "content": "..." }
  ]
}
```

Expect 3–20 seconds per call, and roughly double that when the first reply fails
validation and is retried. Files are stored against the project, so `GET /files`
returns them afterwards, and regenerating replaces the previous set. The
project's status moves `DRAFT → GENERATING → READY`, or to `FAILED` if the model
call throws or its output is unusable after the retry — that case answers **502**
with the violations listed.

**The same endpoint refines.** Call `generate` again on a project that already has
files and the prompt is treated as a change to the running app rather than a fresh
build: the current files and the recent prompt history go in with it. "Make the
accent colour purple instead of blue" on a todo app kept add, complete, delete and
`localStorage` intact, left `index.html` byte-identical, and altered 1.5% of
`app.jsx`. Every prompt and reply summary is appended to `GET /messages`.

A second `generate` on a project that is already generating returns **409** in
tens of milliseconds, without calling the model. A write that races another write
returns **409** too (see optimistic locking below). When the provider's rate limit is
hit — routine on a free tier — the answer is **429** carrying the wait taken from the
provider's `retry-after`, not a `500`.

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

PROJECT_ID=$(curl -s -X POST localhost:8081/api/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"My first app","description":"A todo list"}' \
  | sed 's/.*"id":\([0-9]*\).*/\1/')

curl -s -X POST "localhost:8081/api/projects/$PROJECT_ID/generate" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"A todo app with add, complete, filter and delete."}'

# Refine it — same endpoint, and the existing app goes in as context
curl -s -X POST "localhost:8081/api/projects/$PROJECT_ID/generate" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"Make the accent colour purple instead of blue."}'

# Then open the app in a browser
curl -s -X POST "localhost:8081/api/projects/$PROJECT_ID/preview-token" \
  -H "Authorization: Bearer $TOKEN"
```

---

## Project structure

```
Lovable_Clone/
├── backend/
│   ├── Dockerfile        ← two stages; only the JRE stage ships
│   └── src/main/
│       ├── java/com/aibuilder/lovableclone/
│       │   ├── account/      ← users, auth
│       │   ├── workspace/    ← projects
│       │   ├── generation/   ← LLM calls, output validation, generated files, chat history
│       │   └── common/       ← security, config, exceptions
│       └── resources/db/migration/  ← Flyway migrations, applied in version order
├── docker-compose.yml    ← PostgreSQL, for local development
├── render.yaml           ← the hosted backend, as config rather than dashboard clicks
├── frontend/
│   ├── vercel.json       ← serve index.html for every path, so refresh does not 404
│   └── src/
│       ├── api/          ← fetch wrapper, bearer token, error mapping
│       ├── auth/         ← session context
│       ├── pages/        ← login, projects, builder
│       └── components/   ← shared UI
└── doc/                  ← third-party course reference, not part of the build
```

Each feature package owns its own `controller` / `service` / `repository` /
`entity` / `dto` layers.

Changing an entity means writing the matching `V2__...sql`, `V3__...sql` and so on
in the same commit. Nothing generates them, and `ddl-auto: validate` fails the boot
if one is missing — which is the point.

Run the tests with `mvn -s .mvn/settings.xml test` from `backend/`. They need
neither Postgres nor a Groq key.

---

## Design decisions

**Modular monolith, not microservices (yet).** Packages are split along the
seams a future service split would follow (`account`, `workspace`,
`generation`). Cross-module
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

**Flyway owns the schema; Hibernate only validates it.** Days 1–6 ran on
`ddl-auto: update`, which is convenient and wrong for anything deployed: it
infers changes from a diff, never drops or narrows anything, and does it all
silently. It also emitted SQL Postgres rejected outright — adding `version` to
the populated `projects` table produced `not null` with no default, and the
column had to be added by hand. `db/migration/V1__initial_schema.sql` is the
current schema as one baseline; every change after it is a new numbered file.
`ddl-auto` is now `validate`, so a drifted entity fails at boot instead of
quietly reshaping the database.

Existing databases are covered by `baseline-on-migrate`, which records them at
V1 rather than refusing to touch a schema Flyway didn't create. On an empty
database that flag does nothing and V1 simply runs — which is what a fresh
deploy does, and what was tested. Applied migrations are effectively immutable:
Flyway checksums them, and appending a single comment to V1 is enough to stop
the next boot.

> **Boot 4 gotcha.** `flyway-core` on its own does nothing here. Boot 4 split
> autoconfiguration into per-technology modules, and `spring-boot-autoconfigure`
> no longer contains a single Flyway class — the app boots clean, runs no
> migrations, and creates no history table. `spring-boot-starter-flyway` is what
> brings the autoconfiguration. Postgres support is a second dependency
> (`flyway-database-postgresql`), separate since Flyway 10.

**Concurrent writes fail loudly, they don't overwrite.** `ProjectEntity` carries a
`@Version` field, so Hibernate appends `and version = ?` to every update and a
stale write matches zero rows. The obvious alternative, `@DynamicUpdate`, only
narrows the window — it writes fewer columns but still lets two writers touching
the same column clobber each other, and it costs the prepared-statement cache,
since each dirty-column combination compiles to a different SQL string.
Versioning closes the window instead of shrinking it. The resulting
`OptimisticLockingFailureException` maps to **409**, not 500: the caller did
nothing wrong and can retry. Spring's exception is the one caught, not JPA's, so
no persistence type leaks into the web layer.

Verified by holding the row in an uncommitted `psql` transaction and firing a
generate: the request returned 409, the other writer's change survived, and the
model was never called — the conflict surfaces on the first status update, ahead
of the expensive work. Worth knowing what this does *not* cover: a writer that
bypasses Hibernate and leaves `version` untouched defeats the check entirely, and
the full-column update then writes stale values back over it. Optimistic locking
is a protocol, and every writer has to be in it.

**Errors are centralised.** A single `@RestControllerAdvice` maps domain
exceptions to status codes and returns a uniform `ApiErrorDto`. Unexpected
exceptions are logged with a stack trace but respond with a generic message,
so internals never leak to clients.

**Optimistic locking makes writes safe; it is not an interlock.** Two `generate`
calls on one project each ran their status update in a separate short transaction,
so both wrote `GENERATING` legitimately, both called the model, and the free tier
paid twice. Versioning cannot help here, because neither write is stale — they are
sequential, just pointless. The fix is to make claiming the project a decision the
database takes:

```sql
update projects set status = 'GENERATING', updated_at = ?, version = version + 1
 where id = ? and owner_id = ? and status <> 'GENERATING'
```

The affected row count is the answer: `1` means this caller won and may spend a
model call, `0` means it did not. Two concurrent `generate` calls now come back as
one `200` and one `409` after 46 ms — the loser is turned away before the expensive
work, not after. Zero rows is ambiguous between "already generating" and "not
yours", so a follow-up read separates them and a stranger still gets **404**, never
a 409 that would confirm the project exists.

Two details a bulk update gets wrong by default, both silent. `@PreUpdate` does not
fire, so `updatedAt` is set in the statement. And Hibernate does not touch `version`,
which is exactly the hole documented above — a writer outside the protocol lets a
stale entity elsewhere still look fresh, so the increment is written by hand.

**A claim without an expiry is a way to lose a project permanently.** The first
version of the interlock only asked whether the project was already `GENERATING`,
which assumes the holder eventually finishes. Kill the process mid-generation and it
never does: the row stays `GENERATING`, every later `generate` answers 409, and there
is no API that can undo it. Deploying makes this likely rather than theoretical,
since free hosts idle out and restart containers, and a generation holds a request
thread for up to a few minutes.

A claim now carries `generation_started_at` (V2), and one older than ten minutes can
be taken over. The lease has to exceed the longest *legitimate* generation — two
validation attempts at up to three model calls each — because stealing a claim from a
worker that is merely slow means two model calls racing to write the same project's
files. Ten minutes clears a worst case of about four and a half.

A null timestamp counts as stale, which is what heals the rows that were already
stuck when the column was added: no repair script, and no status the application
cannot reach. Verified on all three: a claim 30 seconds old still answers 409, one 11
minutes old is taken over, and a stuck row with no timestamp generated successfully
on the next attempt.

**Ownership and interlock in one statement, not two.** The owner check used to be a
separate `getProjectById` before the status update. Folding `owner_id` into the
claim's `where` clause removes a query and the window between checking and acting.
Verified: another user's `generate` on someone's project answers 404 and records no
prompt, because the claim fails before anything is written.

**Retry is configuration on the wrong prefix, and it fails without a symptom.** This
one took two wrong answers before the evidence settled it, and both wrong answers
looked right at the time.

The README first claimed Spring AI retried `429`s out of the box. It then claimed the
opposite: that `spring-ai-retry` was an optional dependency missing from the
classpath, so nothing was retried, and that adding it plus
`spring.ai.retry.on-http-codes: [429]` fixed it. That reasoning about
`SpringAiRetryAutoConfiguration` is accurate in isolation, and the wired
`RetryTemplate` was real. It simply has nothing to do with this application.

A live `429` proved it. The provider's daily token limit ran out mid-testing, and the
call failed in **0.6 s** with `com.openai.errors.RateLimitException` — no retry, no
backoff, and no Spring frame anywhere in the stack. Spring AI 2.0's OpenAI path goes
through the official `openai-java` SDK over OkHttp, not Spring's `RestClient`:

```
javac -p OpenAiChatAutoConfiguration.class
  → openAiChatModel(OpenAiCommonProperties, OpenAiChatProperties, ToolCallingManager, …)
```

No `RetryTemplate` parameter, no `ResponseErrorHandler`. `spring.ai.retry.*` cannot
reach this model, so that whole block was configuration that read as load-bearing and
did nothing. It was removed along with the dependency. The knob that exists is the
SDK's, and it was already retrying with sane defaults — 3 attempts, 60 s timeout:

```yaml
spring.ai.openai:
  max-retries: 2
  timeout: 45s
```

Both are tightened because `generate` is synchronous and the validation retry sits on
top: the default worst case is about 3 minutes of a held request thread, and this
caps a single validation attempt near 90 s. `RetryConfigTest` pins these against the
real `application.yml` — flip the expected value and it fails, which is the only proof
that the yml is being read rather than a default.

The lesson generalises past retry: a property that binds is not a property that acts.
Both wrong answers came from reading configuration and framework source; only calling
the provider and watching the clock distinguished them.

**A `429` is not a `500`.** Left alone, the SDK's `RateLimitException` reached the
catch-all and became "Something went wrong", which is a lie — the request was fine and
would succeed later. It is translated at the boundary of the generation service into a
`ModelRateLimitedException` and answered as **429**, so the SDK type stays out of the
web layer, the same way the JPA exception does. The wait comes from the `retry-after`
header rather than the provider's prose, since that message is the only reliable place
the delay appears, and it carries the organisation id, which is logged and not
returned. The UI then shows something a user can act on: *"The AI provider's rate limit
was reached. Try again in about 31 minutes."*

**History is append-only, and separate from the code.** `ChatMessageEntity` has no
`updatedAt` and its `createdAt` is `updatable = false` — history that can be edited
is not history, so a correction is a new message rather than a rewrite of an old
one. Ordering is by `id`, not `createdAt`: a prompt and its reply can land in the
same millisecond, and then timestamp ordering is undefined.

Only the reply's one-line summary is stored, never the code — the files are already
persisted, and duplicating them into the transcript would mean the history alone
could exhaust the context window it exists to feed.

**A follow-up needs the current code and the past intent, for different reasons.**
The files answer "what is there now": without them "make the header blue" rewrites
the whole app from the prompt and silently drops every earlier feature. The
transcript answers "what was asked for": intent like "keep it minimal" leaves no
trace in the code that a later turn could read back. Context is capped at the last
six messages, since older turns cost tokens without adding constraints.

The user prompt is recorded *before* the model call, so a failed attempt still shows
what was asked. Context is read before that, so the new prompt cannot appear in its
own history.

**The generated output format is dictated by the preview mechanism.** Previews
run in an `iframe`, which rules out a build step, which rules out `import`
statements and `package.json`. So the model is constrained to CDN-loaded React
with in-browser Babel. The downstream constraint decides the upstream contract.

**The preview token lives in the path, not the query string.** A browser
navigating to a URL cannot attach an `Authorization` header, so the preview needs
a credential the URL itself carries. A query parameter looks like the obvious
choice and quietly fails: `index.html` loads, but the `src="app.jsx"` inside it
resolves the path and **drops the query**, so the sub-resource arrives
unauthenticated. As a path prefix, relative resolution carries the token along for
free.

**Both token types check their own scope.** A preview token's subject is a
project id; an access token's subject is a user id. Without a `scope` claim
checked in both directions, a preview token — which is visible in URLs, history
and logs — passed as `Authorization: Bearer` would have its project id read back
as a user id. The confusion is silent and grants the wrong thing, so each parser
refuses the other's tokens. The token also carries `ownerId`, which lets preview
reads go through the same ownership-checked query as `GET /files` instead of
needing an unguarded one.

**Malformed tokens are 401, not 500.** JJWT throws for a bad signature, bad
encoding and expiry alike, and none of those were mapped, so a typo'd token
reached the catch-all and answered `500`. Parsing is now wrapped once, in the one
place that parses.

**Previews are served with a CSP, and that is a mitigation rather than a fix.**
Model-written HTML and JavaScript run from our own origin, which is stored XSS by
construction. Scripts are restricted to the two CDNs the contract requires, but
`unsafe-eval` cannot be dropped, because in-browser Babel is what compiles the
JSX. The real fix is a separate origin per project; that is why hosted builders
serve previews from their own domain.

**Generated code is validated, because prompt rules are requests and not
guarantees.** Tightening the prompt fixed five defects on Day 5 and new ones
appeared anyway — one reply spelled the global `ReactDom`, which is `undefined`
and takes the page down on load. So the rules that decide whether the app runs
at all moved into `GeneratedAppValidator`: the three files exist, Tailwind is a
script rather than a stylesheet link, Babel and both React UMD builds are
present, there is a `#root` to mount into, `ReactDOM.createRoot` is used and the
removed `ReactDOM.render` is not, and nothing needs a build step. A rejected
reply is sent back with its own violations listed and gets **one** retry; if that
also fails the request is a **502** and the project is `FAILED`. Invalid code is
never stored, so `READY` cannot lie.

Only breakage is validated, never taste. "Generous whitespace" cannot be checked
by string matching, and failing on it would burn a retry and a slice of the free
tier for nothing, so aesthetics stay in the prompt where a miss is survivable.

That line held under pressure when every generated file came back with zero line
breaks — `app.jsx` was a single 1846-character line. It is trivially detectable and
it was still the wrong thing to validate: the app ran perfectly, and rejecting it
would have spent a retry on formatting. One prompt rule asking for real line breaks
and two-space indentation took it to 25 newlines. The test for whether a rule
belongs in the validator is not "can I check it" but "does the app break".

The validator is the one piece here with unit tests, and deliberately so: it is a
pure function, so its reject paths can be proven without spending a model call —
which an end-to-end test can never do reliably, since a good reply never
exercises them.

**The sandbox has no network, and the model has to be told.** A generated weather
app fetched `api.openweathermap.org` with `appid=YOUR_API_KEY`, wrote no `.catch`,
and rendered `Temperature: °C` forever. Every layer failed quietly: the key was a
placeholder, `connect-src 'self'` blocked the request before it left the browser,
and the rejected promise went nowhere. Nothing crashed, so nothing was reported.

The gap was in the contract, not the reply. The prompt described what to build and
never described where it runs, so the model assumed the usual: a page with internet
access and secrets. It has neither. The prompt now says so and asks for sample data
when a request implies live data, and the validator rejects `fetch`, `XMLHttpRequest`,
`axios` and placeholder keys — because a prompt rule is a request, and this failure
is invisible rather than loud. A sample-data version of the same app still passes;
both cases are unit tested, so the rule cannot quietly start rejecting every app.

**The badge and the preview must not contradict each other.** A red `FAILED` sat
above a weather app that was visibly running. Both were telling the truth —
`FAILED` means the last attempt failed, and the previous files survive a failure —
but read together they say the app is broken when it is not. In the builder, where
the preview is on screen, `FAILED` with files present now reads `LAST ATTEMPT
FAILED` in amber; with no files it stays red, because then it is simply true.

The same screen was also stale: the badge is fetched once on mount, so it kept
saying `FAILED` after the status had already changed. The builder now refetches the
project on focus. Only the project — refreshing files would mint a new preview token
and remount the iframe, throwing away the generated app's own state on every tab
switch.

**A rate limit is not a failed generation.** The catch-all marked the project
`FAILED` on any exception, so hitting the free tier's daily quota badged a project
whose files were intact and whose preview still worked. The generation never
started. `ModelRateLimitedException` now releases the claim instead: back to `READY`
if files exist, `DRAFT` if not, inferred from the files rather than remembered,
since that is what the status describes.

**Response validity is enforced by the provider, not requested in the prompt.**
Asking the model to escape a code payload into JSON strings failed
intermittently — one unescaped quote inside JSX ends the string and the parse
dies. Groq's JSON mode constrains decoding, so malformed output becomes
structurally impossible instead of merely discouraged. `maxTokens` is capped
alongside it, because a truncated reply is also invalid JSON.

**The prompt shows APIs rather than naming versions.** "React 18" produced
`ReactDOM.render` (removed in 18) and Tailwind 2, because version labels are
resolved from training data. Spelling out the exact `createRoot` call and
pinning the CDN URL fixed five observed defects in one pass. Requirements are
also phrased so they can be checked — "render a message when the list is
empty" survives, "handle the empty state" gets ignored.

**Prompt-shaped contracts live in records.** `@JsonPropertyDescription` on
record components feeds the JSON schema that Spring AI sends to the model, so
the model reads the same contract the parser enforces. Renaming a field changes
the prompt.

**No transaction spans the model call.** A generation takes seconds; wrapping it
in `@Transactional` would hold a pooled connection for that entire round trip
and exhaust the pool under modest concurrency. Ownership is checked in a short
transaction, the model is called outside one.

**Modules call each other's services, never each other's repositories.**
`generation` reaches `workspace` through `ProjectService`. A service call can
become an HTTP call during a service split; a repository call cannot.

**The frontend is a separate origin on purpose, and that closes a security gap.**
A Vite dev proxy would have been less work and would have served the preview from
`localhost:5173`, the same origin as the UI. Generated code would then have shared an
origin with the session token. Talking to the backend cross-origin instead puts every
generated app on the backend's origin, where the browser's same-origin policy — not our
CSP — stops it from reaching the app's DOM or `localStorage`. The gap that remains is
narrower: projects still share an origin with each other, so a per-project preview
origin is still the real fix.

It also means CORS is exercised from the first request rather than discovered at
deploy time. One property, `app.frontend-origin`, feeds both the allowed origin and the
preview's `frame-ancestors`, because they are the same fact and drift if written twice.
The origin is named rather than `*`, and `allowCredentials` is off: the token travels in
an `Authorization` header, so permitting cookies would only open a CSRF path that
stateless JWT does not otherwise have.

**Spring Security blocks its own preview by default.** `X-Frame-Options: DENY` is on
every response, and `DENY` refuses framing even same-origin, so the iframe rendered
nothing while the network tab showed `200`s. The header cannot be varied per path within
one filter chain, so preview gets its own chain matched on `/api/preview/**` with frame
options disabled, and the API keeps `DENY`. `frame-ancestors` in the preview CSP replaces
it, which is strictly better: it can name the frontend origin, while `X-Frame-Options`
only understands "nobody" and "same origin".

The iframe is additionally sandboxed to `allow-scripts allow-same-origin allow-forms`.
`allow-same-origin` looks wrong at a glance and is required: without it the frame gets an
opaque origin, `localStorage` throws, and every generated app dies in its `useState`
initialiser — the contract tells the model to persist there. It grants nothing against
this app, which is a different origin either way, and what the sandbox does buy is that
generated code cannot navigate the top-level tab.

**The token is in `localStorage`, knowingly.** An httpOnly cookie resists XSS better,
but needs credentialed CORS and CSRF handling, which the stateless design deliberately
avoids. The trade is acceptable here mainly because of the origin split above: the
untrusted code this product exists to run cannot read that key.

**The UI shows the server's message rather than its own.** Error copy is not mapped by
status code, because the useful part is what the backend already said — the rate-limit
wait, or which contract rules the model broke. A generic "Something went wrong" per
status would discard exactly the information the user needs.

**On a phone the two panes take turns.** The builder is a 384px sidebar beside a preview,
which is a desktop shape: a phone is about 390px wide, so the sidebar took the screen and
the preview was squeezed to nothing. Sending a prompt appeared to do nothing, because the
app it produced was in a pane with no width. Below the `md` breakpoint they are now one at
a time, switched by a chat/app toggle, and generating switches to the app — otherwise the
result still lands somewhere the user is not looking. Shrinking both to fit was never the
alternative; at that width, half of each is neither.

**The image is built in two stages, and only the second one ships.** Building needs a JDK
and Maven; running needs a JRE. Keeping one stage would leave Maven, the source and a
few hundred megabytes of `~/.m2` sitting in production. Dependencies live in a BuildKit
cache mount rather than an image layer, so they survive between builds without being
shipped — a rebuild after the last fix took 13 seconds against 150 for the first.

An earlier version cached dependencies with `dependency:go-offline` in its own layer.
That resolves every plugin and profile, which is far more than `package` needs, and
`package` then downloads its own set anyway. Tests run inside the build, so no image
exists unless the suite is green; they need no secrets, which is what makes that
possible.

Two things only a real run could catch, both found that way:

- `eclipse-temurin:17-jre-alpine` is published for amd64 only, and this is an Apple
  Silicon machine. `docker manifest inspect` says the tag exists — the failure is `no
  match for platform in manifest`, which arrives from the build, not from the registry.
  The multi-arch `17-jre` tag is Debian, so the non-root user is created with `useradd`
  rather than BusyBox's `adduser -S`
- `spring.datasource.hikari.*` binds straight onto `HikariDataSource`, whose setters take
  plain milliseconds. Boot's duration strings work almost everywhere else in the config,
  so `connection-timeout: 30s` looks correct and stops the app from starting

The container runs as `app`, not root, and reads `PORT` because hosts assign it and kill
whatever does not listen there. `ENTRYPOINT` is `sh -c` with `exec`, so `JAVA_OPTS`
expands *and* the JVM becomes PID 1 — without `exec` the shell keeps PID 1, `SIGTERM`
never reaches Spring, and shutdown is a kill instead of a graceful stop.

Verified against the local database with `SPRING_PROFILES_ACTIVE=prod` and `PORT=9000`:
Flyway validated three migrations and reported the schema current, startup took 5.9
seconds, `whoami` answered `app`, and the API answered `200` on health and `401` on a
bad password. Generation is the one thing that cannot be checked this way here — a
corporate proxy re-signs TLS on this network, and the container's truststore has no
reason to carry that CA. Adding it to the image would leak an internal detail into a
public repository and would be wrong in production regardless, so generation is verified
after deployment, where no such proxy exists.

### Fitting a JVM into 512 MB

`MaxRAMPercentage=75` was the first guess, reasoning only that the default quarter of
512 MB is a 128 MB heap and Boot with JPA, Flyway and Spring AI does not start in that.
It is the wrong number, and a deploy said so: `==> Out of memory (used over 512Mi)`,
then a port scan that timed out on a port Tomcat had already bound, then a shutdown.

The mistake is treating the container limit as a heap budget. Docker can reproduce
Render exactly — `--memory=512m --memory-swap=512m --cpus=0.5` — and inside that
container the JVM reports `MaxHeapSize = 402653184`: 384 MB of heap alone, before
metaspace (no default ceiling at all), the code cache, and a 2 MB stack for every one of
Tomcat's up-to-200 threads. Idle RSS was already 345 MB with a heap that was mostly
empty. There was never room for the heap to reach its own ceiling.

Five changes, each measured in that container rather than reasoned about:

| | before | after |
| --- | --- | --- |
| Startup, 0.5 CPU | 46 s | 14 s |
| Startup, 0.25 CPU | 163 s | 67 s |
| Idle | 345 MB | 256 MB |
| After 300 authenticated requests | ~400 MB | 315 MB |
| Heap ceiling | 384 MB | 256 MB |

- `MaxRAMPercentage=50` puts the heap ceiling at 256 MB and leaves the other 256 MB for
  everything the heap limit does not cover
- `MaxMetaspaceSize=128m` gives class metadata a ceiling it does not have by default.
  Unbounded, growth there kills the container silently; bounded, it is a legible
  `OutOfMemoryError: Metaspace`
- `Xss512k` takes thread stacks from 2 MB, multiplied by however many threads exist.
  `server.tomcat.threads.max: 20` is the other half of that: 200 threads is meaningless
  behind 5 database connections and 0.1 CPU. A 50-way burst now settles at 22 HTTP
  threads instead of spawning 50
- `TieredStopAtLevel=1` was the surprise. Keeping the JIT at C1 cut startup by 2.4x on a
  CPU-starved box, because C2 compilation competes for the one thing that is scarce, and
  took 45 MB with it. The cost is lower steady-state throughput on hot code, which this
  app does not have — every request's time is spent waiting on Groq
- `spring.main.lazy-initialization` in the prod profile, worth another 39 MB and 2
  seconds. The obvious objection is that lazy initialisation only defers the cost, so the
  saving should evaporate under load — it does not here: 294 MB → 256 MB idle, and
  357 MB → 315 MB after the same 300 requests. Enough of this context is never touched by
  a running request to be worth not building

`ExitOnOutOfMemoryError` is there so a heap exhaustion kills the process and the platform
restarts it. Without it the JVM survives in permanent GC, and a service that is up but
answering nothing is harder to notice than one that is down.

Lazy initialisation is the one of these with a real downside, which is why it is scoped
to the prod profile: a bean that fails to build now fails on the first request rather
than at startup, and a deploy that passes its health check and then breaks is worse than
one that never goes live. The two paths where that would hurt most were checked rather
than assumed. Flyway and the `DataSource` still run during startup, so a bad migration or
an unreachable database stops the deploy. And starting without `JWT_SECRET_KEY` still
exits `1` with `Could not resolve placeholder`, because `JwtAuthFilter` is a servlet
filter and Tomcat cannot start without it — the fail-fast promise in `application.yml`
survives. Development stays eager, where a mistake should surface while it is being
written.

Verified after the change: no OOM across 300 requests, `OOMKilled=false`, no errors in
the log, and 197 MB of headroom where the original had the heap alone able to walk past
the limit.

---

## Known gaps

- `GeneratedAppValidator` and the model retry configuration are the only tested
  things; no controller, service or repository has a test, and the frontend has none
  at all
- Every preview shares one origin, so generated apps share `localStorage`. A
  regenerated counter opened at 1 instead of 0, having inherited the previous
  app's saved state. Per-project origins are the fix, and they are also what
  would contain the XSS noted above
- A preview token cannot be revoked before it expires, and anyone holding the URL
  can open it. Thirty minutes is the only bound
- Using a preview token on the API answers `403` where `401` would be right. The
  filter clears the context and Spring Security then reports it as anonymous
  access denied rather than failed authentication
- Validation covers what breaks the app, not whether it does what was asked. A
  request for "increment and reset" came back with increment and *decrement*, and
  no string match catches that
- A claim expires after ten minutes, so a crash costs the user that long before the
  project can be generated again. A worker that renewed its lease while alive, or a
  job queue that noticed the worker die, would make the wait proportional to the
  actual failure rather than to the worst legitimate generation
- The assistant's stored message is the app's summary, so it records what the app
  *is* rather than what the turn *changed*. A functional change shows up (adding a
  filter widened the summary) but a cosmetic one does not — after "make it purple"
  the summary was word-for-word what it had been. A per-turn note of what changed
  would be the more useful thing to keep
- Deleting a project leaves its `generated_files` and `chat_messages` rows behind.
  There are no foreign keys, by design, so nothing cascades. `workspace` should not
  import `generation` to clean up either — the fix is an event `generation` listens
  for, which is also what a service split would need
- Follow-ups are only as reliable as the model's willingness to preserve what it was
  not asked to change. One measured turn kept 98.5% of `app.jsx` and left
  `index.html` byte-identical, but nothing enforces it: the validator checks that
  the app runs, not that last turn's features survived
- Generation is synchronous, so a request thread is held for the whole model call and
  the UI can only sit on a spinner. Retry is bounded now, but the real fix is a job
  queue with the client polling — which is also what would let `GENERATING` mean
  anything useful in the interface
- The builder refetches the project on focus, so the badge corrects itself, but files
  and preview still only load on mount. A project regenerated in another tab shows its
  new status next to its old code until the page is reloaded
- `generate` is not idempotent — a retried HTTP request bills a fresh generation.
  An idempotency key would fix it
- Generated apps cannot reach the network at all, so "show me live prices" becomes a
  sample-data app. Opening `connect-src` to public keyless APIs would make those
  requests real, but it also lets model-written code talk to the internet from the
  user's browser — an allow-list of hosts is the shape of the answer, and it is not
  built
- 512 MB leaves about 197 MB of headroom once the JVM has settled, and nothing watches
  it. There are no memory metrics and no alert; the way the last limit was found was a
  platform notice after the fact. Actuator with a metrics endpoint is the small version
  of the fix
- `findByOwnerId` and `findByProjectIdOrderByIdAsc` return unbounded lists, so a user with
  thousands of projects, or a project with a long history, loads all of it into memory as
  entities, then DTOs, then JSON — three copies in one request. The model's context window
  is already capped with `Limit`; the read paths are not. Pagination is the fix, and it is
  the reason memory can still grow with data even after the tuning above
- Migrations are only ever applied forward. There is no `undo` (that is Flyway's paid
  tier), so a bad migration is fixed by writing the next one
- No refresh tokens or token revocation, so an expired token means a silent bounce to
  the login screen mid-session
- No rate limiting on auth endpoints
- The image is ~250 MB compressed, most of it the Debian JRE and a 150 MB fat jar.
  `jlink` with only the needed modules, or a layered jar so dependencies and application
  classes cache separately, would cut both. Neither matters until deploys are frequent
  enough for the pull to be felt
- `VITE_API_BASE_URL` is inlined into the bundle at build time, so pointing the frontend
  at a different backend is a rebuild rather than a restart. Reading it from a small
  runtime config the page fetches first would decouple them, at the cost of one request
  before anything renders
