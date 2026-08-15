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
| 6d | Prompt history, follow-up prompts refining an existing app | Done |
| 6e | Generation interlock, bounded retry budget | Done |
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
returns **409** too (see optimistic locking below).

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
│   └── src/main/java/com/aibuilder/lovableclone/
│       ├── account/      ← users, auth
│       ├── workspace/    ← projects
│       ├── generation/   ← LLM calls, output validation, generated files, chat history
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

**Ownership and interlock in one statement, not two.** The owner check used to be a
separate `getProjectById` before the status update. Folding `owner_id` into the
claim's `where` clause removes a query and the window between checking and acting.
Verified: another user's `generate` on someone's project answers 404 and records no
prompt, because the claim fails before anything is written.

**Retry is configuration, and its two failure modes are both silent.** The README
used to claim Spring AI retried `429`s out of the box. Both halves were wrong, and
reading the jars is what settled it. `SpringAiRetryAutoConfiguration` is
`@ConditionalOnClass(RetryUtils)`, and `RetryUtils` ships in `spring-ai-retry`,
which Spring AI 2.0 makes an *optional* dependency of the starter. It was not on the
classpath, the condition failed quietly, the app booted fine, and nothing was ever
retried — not even a `502` from the provider.

Adding the dependency is not enough either. The property-aware error handler checks
`on-http-codes` first, then treats any remaining `4xx` as non-transient, and
`on-client-errors` defaults to `false`. `429` is a `4xx`, so a rate limit failed
without a single retry unless it is opted in explicitly:

```yaml
spring.ai.retry.on-http-codes: [429]
```

The defaults that do apply are sized for a batch job: 10 attempts, 2 s initial
backoff, multiplier 5, capped at 3 minutes — worst case roughly 19 minutes with a
request thread held the whole time. `generate` is synchronous, so the budget is cut
to 3 attempts and a 4 s cap: at most 3 s of added sleep. Combined with the one
validation retry that is 6 model calls worst case, which is the ceiling worth
having until generation moves to a background job.

`RetryConfigTest` pins all three facts against the real `application.yml`, because
each of them fails without a symptom: drop the jar and retry disappears, drop the
`429` line and rate limits stop being retried, widen the budget and a thread blocks
for a quarter of an hour.

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

- `GeneratedAppValidator` and the retry configuration are the only tested things;
  no controller, service or repository has a test
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
- The interlock has no lease, so a process killed mid-generation leaves the project
  stuck in `GENERATING` and every later `generate` answers 409 forever. A claim
  should carry a timestamp and expire, so a stale one can be taken over
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
- Generation is synchronous, so a request thread is held for the whole model call.
  Retry is bounded now, but the real fix is a job queue with the client polling
- `generate` is not idempotent — a retried HTTP request bills a fresh generation.
  An idempotency key would fix it
- Schema changes to populated tables have to be applied by hand. Adding the
  `version` column needed `alter table projects add column version bigint not
  null default 0` first, because `ddl-auto: update` emits it without a default
  and Postgres rejects that on a non-empty table. This is the concrete cost of
  the missing migration tool below
- `ddl-auto: update` instead of a migration tool (Flyway/Liquibase)
- No refresh tokens or token revocation
- No rate limiting on auth endpoints
- CORS not configured (needed once the frontend exists)
