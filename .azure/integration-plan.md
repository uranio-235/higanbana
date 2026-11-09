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
  boundaries validated in `Workflow`.
- Novel characters use a bidirectional OneToMany/ManyToOne foreign-key relation.
  The studio stores selected sheet snapshots and passes them to text/image prompts.
- Image boundary: `ImageProvider.java`; replace `DemoImageProvider` only after a
  real provider is selected/verified. NVIDIA text never enables real images.
- Database: persistent SQLite (`./higanbana.db`), Liquibase migrations and Hibernate
  schema validation. H2 is test-only via the test profile; no seed data.
- Changelog: `src/main/resources/db/changelog/db.changelog-master.xml` includes
  `changes/001-initial-schema.xml`. Do not modify applied changesets.
- Storage: approved files only under `app.manga-dir` (default `./data/manga`). Pending
  bytes in SQLite; rejected bytes removed. Image associations survive restart.
  Back up SQLite and the image directory together.
- SQLite uses foreign keys, WAL, busy timeout 10 s and a one-connection JDBC pool.
  No row-level FOR UPDATE support; pool serialization is local-instance only.
- Services: Essential = local MVC, JPA/SQLite, local approved-image storage;
  Enhancement = optional NVIDIA text. No external service needed for demo.
- NVIDIA config: properties `app.nvidia.api-key`, `app.text.model`, `app.nvidia.base-url` (base excludes
  `/v1`, default `https://integrate.api.nvidia.com`). Spring AI appends
  `/v1/chat/completions`. Mock HTTP contract tested; actual model availability and
  live inference NOT tested. Do not label mock output as live provider output.

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
| POST | `/novels/{n}/chunks/propose` | `limit`, optional `revision` |
| POST | `/novels/{n}/chunks/confirm` | Confirm saved segments idempotently |
| GET | `/novels/{n}/panels?chunkId={c}` | Panel negotiation for a chunk |
| POST | `/novels/{n}/chunks/{c}/panels/propose` | `count`, optional `revision` |
| POST | `/novels/{n}/chunks/{c}/panels/save` | Repeated `descriptions` fields |
| POST | `/novels/{n}/chunks/{c}/panels/confirm` | Confirm saved panel plan |
| GET | `/novels/{n}/studio` | Select 1/2 panels, edit drafts |
| POST | `/novels/{n}/studio` | Repeated `panelIds`, optional `characterIds` and `direction` |
| POST | `/novels/{n}/studio/{d}/save` | `prompt` |
| POST | `/novels/{n}/studio/{d}/generate` | Candidate from saved prompt |
| GET | `/novels/{n}/generation` | Pending/approved/rejected candidates |
| POST | `/novels/{n}/candidates/{c}/approve` | Idempotent approval, file write |
| POST | `/novels/{n}/candidates/{c}/reject` | Reject pending candidate |
| GET | `/novels/{n}/candidates/{c}/image` | Pending/approved image; rejected fails |
| GET | `/novels/{n}/reader` | Approved entries in narrative order |
| GET | `/novels/{n}/entries/{e}/image` | Approved local image/download |
| GET | `/demo.svg` | Watermarked original demo illustration |
| GET | `/health` | Health probe |

## Verification

- Tests: 36 passing (2 lossless chunking, 9 service workflow, 6 MockMvc/H2 HTTP,
  19 demo/NVIDIA contract). Complete seven-screen HTTP flow, invalid UTF-8, CSRF,
  ownership, 1/2-panel selection, state guards, narrative ordering, snapshots,
  idempotent approval, and approved-only disk storage covered.
- H2 tests execute Liquibase and validate the schema; repeated migration execution
  leaves one changeset. H2 is absent from the packaged JAR.
- Real SQLite startup/schema validation and persistence of a novel/character across
  restart passed. JDBC binary roundtrip (300 KB) and foreign-key rejection passed;
  the JDBC fixture was rolled back. No live AI provider was used for these checks.
- Character FK persistence, orphan removal, input validation, cross-novel guards,
  escaped HTML, context limits, and immutable draft/candidate snapshots covered.
- Desktop browser character create/edit and two-panel plus character selection
  passed. The candidate contains the selected visual sheet and images render.
- Official Maven distribution SHA-512 verified; wrapper pins SHA-256.
- Versions: Boot 3.5.7; Spring AI 1.1.0; Maven 3.9.11; wrapper plugin 3.3.4;
  SQLite JDBC 3.49.1.0; Liquibase 4.31.1; H2 2.3.232 (tests only);
  Hibernate/community dialects 6.6.33.Final; HTMX 2.0.4; Lucide 0.468.0.
- Java certificate issue on this Windows machine: use
  `MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE`
  if necessary. TLS validation was not disabled.
- NVIDIA chunk negotiation covers every bounded source window with validated unit
  endpoints; it never rewrites source text or sends the whole novel at once.
- Browser demo workflow passed: zero reader images before approval, one afterwards.
- Desktop and 390 px mobile screenshots reviewed. All six mobile views checked
  without page-width overflow or broken images. Real NVIDIA inference unverified.
- Final verified JAR running at http://127.0.0.1:8091 for user review.
- README updated; the previously removed Italian translation was not recreated.
  No commits or deployments.

## Remaining Manual Review

Configure and test actual NVIDIA inference only with privately supplied credentials.
Select a real image provider before replacing the explicit demo adapter. Stop a
running JAR before rebuilding on Windows to avoid locked/replaced archive errors.
Do not provision cloud resources. New schema changes require versioned Liquibase
changesets and SQLite compatibility checks; H2 remains the automated test database.