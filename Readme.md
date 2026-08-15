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
| 5 | Spring AI + Groq, prompt → runnable app via structured output | Done |
| 6 | Persist generated files, status transitions, optimistic locking | Done |
| 6b | Validate generated output, retry once on violations | Done |
| 6c | Live preview: generated apps run in the browser | Done |
| 6d | Prompt history, follow-up prompts | Next |
| 7+ | File storage (MinIO), RAG (Qdrant), Kubernetes | Planned |

---

## Stack

**Current:** Java 17 · Spring Boot 4.0.0 · Spring Security · Spring Data JPA ·
PostgreSQL 16 · JJWT · Spring AI 2.0 · Groq (Llama 3.3 70B) · Docker Compose

**Planned:** Redis · MinIO · Qdrant · React + Vite · Kubernetes (k3s)

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

Hibernate runs with `ddl-auto: update`, so tables are created on first boot.

> When behaviour doesn't match the source, run `mvn clean spring-boot:run`.
> The IDE's compiler and Maven both write to `target/classes`, so a save
> caught mid-write can leave a truncated `.class` file. Maven then skips it,
> because its incremental check compares timestamps rather than content, and
> the build stays green. `javap -p target/classes/...` shows the truth.

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

A write that races another write on the same project returns **409** instead of
silently overwriting it (see optimistic locking below).

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

# Then open the app in a browser
curl -s -X POST "localhost:8081/api/projects/$PROJECT_ID/preview-token" \
  -H "Authorization: Bearer $TOKEN"
```

---

## Project structure

```
Lovable_Clone/
├── backend/
│   └── src/main/java/com/aibuilder/lovableclone/
│       ├── account/      ← users, auth
│       ├── workspace/    ← projects
│       ├── generation/   ← prompts, LLM calls, output validation, generated apps
│       └── common/       ← security, config, exceptions
├── docker-compose.yml    ← PostgreSQL
├── frontend/             ← React (not started)
└── doc/                  ← third-party course reference, not part of the build
```

Each feature package owns its own `controller` / `service` / `repository` /
`entity` / `dto` layers.

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

The validator is the one piece here with unit tests, and deliberately so: it is a
pure function, so its reject paths can be proven without spending a model call —
which an end-to-end test can never do reliably, since a good reply never
exercises them.

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

---

## Known gaps

- `GeneratedAppValidator` is the only tested class; nothing else has tests
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
- Prompts are not persisted, only the files they produced, so there is no history
  to build follow-up prompts ("make the header blue") from
- Nothing stops two generations running on the same project at once. Optimistic
  locking makes the *writes* safe but is not an interlock: each status update
  reloads the row in its own short transaction, so a double-clicked button still
  fires two model calls and burns quota twice. The fix is a compare-and-set —
  `set status = 'GENERATING' where id = ? and status <> 'GENERATING'`, then check
  the affected row count
- Schema changes to populated tables have to be applied by hand. Adding the
  `version` column needed `alter table projects add column version bigint not
  null default 0` first, because `ddl-auto: update` emits it without a default
  and Postgres rejects that on a non-empty table. This is the concrete cost of
  the missing migration tool below
- Spring AI retries `429`s out of the box, but its defaults are too generous for
  a synchronous endpoint: 10 attempts with backoff capped at 3 minutes can block
  a request thread for roughly 19 minutes. The free tier allows about seven
  generations per minute, so `429` is routine and this needs a bounded budget —
  and eventually an async job rather than a blocking call
- `POST /api/ai-test` is a temporary probe still sitting in `HealthController`
- `ddl-auto: update` instead of a migration tool (Flyway/Liquibase)
- No refresh tokens or token revocation
- No rate limiting on auth endpoints
- CORS not configured (needed once the frontend exists)
