# Higanbana Local Integration

- Scope: local-only Spring MVC/Thymeleaf monolith. No Azure resources, deployment,
  external database, or seed data. No separate frontend process.
- Approved scope source: `.azure/project-plan.md` and autonomous implementation request.
- Backend/frontend folder: repository root (`Q:\source\higanbana`).
- Java: JDK 21; verified java/javac 21.0.13.
- Build and tests: `.\mvnw.cmd clean verify`.
- Run: `.\mvnw.cmd spring-boot:run` or `java -jar target/higanbana-0.1.0.jar`.
- Bind: `127.0.0.1:8080`; set `server.port` for another local port.
- Health: `GET /health` -> `ok`.
- Frontend: server-rendered `src/main/resources/templates/workspace.html` (seven
  views), `problem.html`, static CSS/JS and locally vendored HTMX/Lucide.
- No frontend build/dev command beyond the Maven/Spring commands above.
- Mock/live switch: property `app.text-mode=demo|nvidia`; `TextAssistant.java` owns text
  selection. No mock client files/datasets to delete and no TypeScript/shared npm
  package. Java workflow types: individual files in `local.higanbana.domain`;
  boundaries validated in `application/Workflow`.
- Java layout: `domain` contains entities and lossless chunk splitting;
  `application/Workflow` owns transactional orchestration; `infrastructure/ai`
  and `infrastructure/image` contain provider integrations; `web/controller`
  separates workspace, characters, chunks, panels, studio, generation, and image
  routes; `web/dto` and `web/error` hold MVC contracts/handling; `config` contains
  cross-cutting web configuration. Public route paths are unchanged.
- Novel characters use a bidirectional OneToMany/ManyToOne foreign-key relation.
  The studio stores selected sheet snapshots and passes them to text/image prompts.
- Images are generated outside the application. Studio copies the editable prompt
  together with the selected character snapshots and accepts a user-uploaded PNG
  or JPEG (up to 20 MB, 40 megapixels) as a candidate. Images are decoded and
  re-encoded; candidate provenance identifies them as user uploads. Historical
  demo candidates retain their original provenance. NVIDIA generates panel text,
  not images.
- Panel prompts request concrete visual details from the source along with
  monochrome manga linework and screentones. New Studio drafts include the full
  original chunk as reference, the chosen panel prompts in narrative order and
  manga composition rules. Any number of the chunk's existing panels can be
  selected; panel planning currently allows up to 12 per chunk. Existing saved
  drafts are not rewritten.
- Studio draft panel IDs are stored in `studio_draft_panels`; older drafts fall
  back to their first/second panel columns. Approval checks every associated
  panel, including for previously approved images.
- Deleting a Studio draft preserves each related candidate's prompt and its
  complete panel associations in `candidate_panels`; pending and approved
  images remain available and their review rules remain unchanged.
- Studio final prompts are generated with NVIDIA using the selected panel
  prompts, source chunk and character snapshots. Each new draft stores its exact
  response cache key. Deletion evicts that response in the same transaction;
  creating the same draft afterwards requests a fresh response. Older drafts
  were assembled locally and have no Studio response cache key to evict.
- Database: persistent DuckDB (`./higanbana.duckdb`), DuckDB JDBC 2.0.0.1 and
  `db/duckdb/schema.sql`; Hibernate uses PostgreSQL-compatible SQL with automatic
  DDL disabled. H2/Liquibase remain test-only via the test profile.
- Confirmed chunk plans use the child table `chunk_plan_confirmation` rather than
  updating the referenced plan row; startup migrates existing `confirmed=true`
  values into this table to accommodate DuckDB FK update restrictions.
- Panel plan confirmation uses `panel_plan_confirmation` for the same DuckDB FK
  limitation. Startup backfills legacy confirmations and their provenance. Editing
  prompts touches collection rows only; regenerating an unconfirmed proposal
  replaces its parent, while regenerating a confirmed legacy demo plan updates
  panel prompts and confirmation provenance without changing panel IDs or saved
  studio/candidate snapshots.
- AI response cache uses `ai_response_cache` in DuckDB, keyed by SHA-256 of the
  operation/provider/model/prompt version/generation settings and exact final
  request. Only validated successful output is stored; default TTL is 30 days.
  Prompt/source content is not duplicated into the cache table.
- Storage: approved files only under `app.manga-dir` (default `./data/manga`). Pending
  bytes in DuckDB; rejected bytes removed. Image associations survive restart.
  Back up DuckDB and the image directory together.
- DuckDB uses an embedded one-connection JDBC pool. Explicit row locks are omitted;
  this remains a single-instance experiment.
- Services: Essential = local MVC, JPA/DuckDB, local approved-image storage;
  Enhancement = optional NVIDIA text. No external service needed for demo.
- NVIDIA config: properties `app.nvidia.api-key`, `app.text.model`, `app.nvidia.base-url` (base excludes
  `/v1`, default `https://integrate.api.nvidia.com`). Spring AI appends
  `/v1/chat/completions`. Mock HTTP contract tests cover failures and caching;
  live model availability can change. Do not label mock output as live provider output.
- The configured default text model is `nvidia/nemotron-3.5-lightning-30b-a3b`.
  Its NVIDIA request explicitly sets `reasoning_effort=none` to preserve the
  output token budget for structured responses; other models are not altered.
  Each uncached Studio generation uses a new sampling seed; cache hits reuse the
  prior response until its draft is deleted. A different seed does not guarantee
  that a model will produce different text.

## Route Inventory

All mutation routes require a session CSRF `_csrf` form field. Open `/` first.
`{n}` is a novel ID; all nested ownership is verified at the service boundary.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/` | Novels/import screen |
| POST | `/novels` | Create from title plus source OR multipart UTF-8 TXT |
| GET | `/novels/{n}/characters` | Character sheets for this novel |
| POST | `/novels/{n}/characters` | Create with `name`, `description` |
| POST | `/novels/{n}/characters/{c}/save` | Edit name and visual description |
| POST | `/novels/{n}/characters/{c}/delete` | Delete sheet, preserve draft snapshots |
| GET | `/novels/{n}/chunks` | Chunk proposal/review |
| POST | `/novels/{n}/chunks/propose` | optional `revision`; analysis window is internal configuration |
| POST | `/novels/{n}/chunks/confirm` | Confirm saved segments idempotently |
| GET | `/novels/{n}/panels?chunkId={c}` | Panel negotiation for a chunk |
| POST | `/novels/{n}/chunks/{c}/panels/propose` | NVIDIA generates `count` independent image prompts from the chunk; optional `revision` |
| POST | `/novels/{n}/chunks/{c}/panels/save` | Repeated editable `imagePrompts` fields |
| POST | `/novels/{n}/chunks/{c}/panels/confirm` | Confirm and persist image prompts on panels |
| GET | `/novels/{n}/studio` | Select confirmed image prompts from one chunk, add cast/direction, edit final prompt |
| POST | `/novels/{n}/studio` | Repeated `panelIds`, optional `characterIds` and `direction` |
| POST | `/novels/{n}/studio/{d}/save` | `prompt` |
| POST | `/novels/{n}/studio/{d}/delete` | Delete the description while preserving associated candidates |
| POST | `/novels/{n}/studio/{d}/upload` | Multipart `prompt` and `image`; save prompt and create a review candidate atomically |
| GET | `/novels/{n}/generation` | Pending/approved/rejected candidates |
| POST | `/novels/{n}/candidates/{c}/approve` | Idempotent approval, file write |
| POST | `/novels/{n}/candidates/{c}/reject` | Reject pending candidate |
| GET | `/novels/{n}/candidates/{c}/image` | Pending/approved image; rejected fails |
| GET | `/novels/{n}/reader` | Approved entries in narrative order |
| GET | `/novels/{n}/entries/{e}/image` | Approved local image/download |
| GET | `/health` | Health probe |

## Verification

- Tests: 65 passing with DuckDB/H2. Complete
  seven-screen HTTP flow, invalid UTF-8, CSRF,
  ownership, 1/2-panel selection, state guards, narrative ordering, snapshots,
  idempotent approval, approved-only disk storage, DuckDB/H2 cache persistence,
  expiration, exact-request hits, key separation, malformed-response handling,
  DuckDB chunk confirmation/idempotence, and image-prompt panel-to-candidate
  propagation, DuckDB prompt save/regenerate/confirm, legacy demo recovery, and
  idempotent panel confirmation covered.
- H2 tests execute the test Liquibase changelog. A separate DuckDB test verifies
  JPA persistence and sequence generation.
- Character FK persistence, orphan removal, input validation, cross-novel guards,
  escaped HTML, context limits, and immutable draft/candidate snapshots covered.
- Desktop browser character create/edit and two-panel plus character selection
  passed previously. New Panel prompt rendering and legacy demo warning are covered
  in MockMvc; the shared debug app has not been restarted for the DuckDB fix and
  still shows legacy demo data. Live NVIDIA prompt generation remains unverified.
- Official Maven distribution SHA-512 verified; wrapper pins SHA-256.
- Versions: Boot 3.5.7; Spring AI 1.1.0; Maven 3.9.11; wrapper plugin 3.3.4;
  DuckDB JDBC 2.0.0.1; Liquibase 4.31.1 and H2
  2.3.232 (tests only); Hibernate 6.6.33.Final; HTMX 2.0.4; Lucide 0.468.0.
- Java certificate issue on this Windows machine: use
  `MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE`
  if necessary. TLS validation was not disabled.
- NVIDIA chunk negotiation covers every bounded source window with validated unit
  endpoints; it never rewrites source text or sends the whole novel at once.
- Cache stores successful operation-validated responses only; TTL is configurable
  with `app.ai.cache.ttl` (default 30 days). Exact repeated requests hit cache.
- Browser demo workflow passed: zero reader images before approval, one afterwards.
- Desktop and 390 px mobile screenshots reviewed. All six mobile views checked
  without page-width overflow or broken images. Real NVIDIA inference unverified.
- Final verified JAR running at http://127.0.0.1:8091 for user review.
- README updated; the previously removed Italian translation was not recreated.
  No commits or deployments.

## Remaining Manual Review

Configure and test actual NVIDIA inference only with privately supplied credentials.
Generate images externally and upload them through Studio. Stop a
running JAR before rebuilding on Windows to avoid locked/replaced archive errors.
Do not provision cloud resources. Update `db/duckdb/schema.sql` for DuckDB schema
experiments; the old Liquibase changelog is retained for H2 tests. DuckDB VSS/vector
search has not yet been wired into the application.