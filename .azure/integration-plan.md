# Higanbana Local Integration

- Scope: local-only Spring MVC/Thymeleaf monolith. No Azure resources, deployment,
  external database, or seed data. No separate frontend process.
- Approved scope source: `.azure/project-plan.md` and autonomous implementation request.
- Backend/frontend folder: repository root (`Q:\source\higanbana`).
- Java: JDK 21; verified java/javac 21.0.13.
- Build and tests: `.\mvnw.cmd clean verify`.
- Run: `.\mvnw.cmd spring-boot:run` or `java -jar target/higanbana-0.1.0.jar`.
- Bind: `127.0.0.1:8080`; override `PORT` for another local port.
- Health: `GET /health` -> `ok`.
- Frontend: server-rendered `src/main/resources/templates/workspace.html` (six
  views), `problem.html`, static CSS/JS and locally vendored HTMX/Lucide.
- No frontend build/dev command beyond the Maven/Spring commands above.
- Mock/live switch: `APP_TEXT_MODE=demo|nvidia`; `TextAssistant.java` owns text
  selection. No mock client files/datasets to delete and no TypeScript/shared npm
  package. Java workflow types: `Domain.java`; boundaries validated in `Workflow`.
- Image boundary: `ImageProvider.java`; replace `DemoImageProvider` only after a
  real provider is selected/verified. NVIDIA text never enables real images.
- Database: in-memory H2 (`jdbc:h2:mem:higanbana`), JPA create-drop. NO seed data.
  No PostgreSQL/SQL service, migration tool/directory, or connection env needed.
- Storage: approved files only under `MANGA_DIR` (default `./data/manga`). Pending
  bytes in H2; rejected bytes removed. H2 associations are lost at shutdown even
  though approved files survive. No recovery/index reconstruction implemented.
- Services: Essential = local MVC, JPA/H2, local approved-image storage;
  Enhancement = optional NVIDIA text. No external service needed for demo.
- NVIDIA config: `NVIDIA_API_KEY`, `NVIDIA_MODEL`, `NVIDIA_BASE_URL` (base excludes
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
| GET | `/novels/{n}/chunks` | Chunk proposal/review |
| POST | `/novels/{n}/chunks/propose` | `limit`, optional `revision` |
| POST | `/novels/{n}/chunks/confirm` | Confirm saved segments idempotently |
| GET | `/novels/{n}/panels?chunkId={c}` | Panel negotiation for a chunk |
| POST | `/novels/{n}/chunks/{c}/panels/propose` | `count`, optional `revision` |
| POST | `/novels/{n}/chunks/{c}/panels/save` | Repeated `descriptions` fields |
| POST | `/novels/{n}/chunks/{c}/panels/confirm` | Confirm saved panel plan |
| GET | `/novels/{n}/studio` | Select 1/2 panels, edit drafts |
| POST | `/novels/{n}/studio` | Repeated `panelIds`, optional `direction` |
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

- Tests: 16 passing (2 lossless chunking, 5 service workflow, 3 MockMvc/H2 HTTP,
  6 demo/NVIDIA contract). Complete six-screen HTTP flow, invalid UTF-8, CSRF,
  ownership, 1/2-panel selection, state guards, narrative ordering, snapshots,
  idempotent approval, and approved-only disk storage covered.
- Official Maven distribution SHA-512 verified; wrapper pins SHA-256.
- Versions: Boot 3.5.7; Spring AI 1.1.0; Maven 3.9.11; wrapper plugin 3.3.4;
  H2 2.3.232; Hibernate 6.6.33.Final; HTMX 2.0.4; Lucide 0.468.0.
- Java certificate issue on this Windows machine: use
  `MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE`
  if necessary. TLS validation was not disabled.
- NVIDIA chunk negotiation covers every bounded source window with validated unit
  endpoints; it never rewrites source text or sends the whole novel at once.
- Browser demo workflow passed: zero reader images before approval, one afterwards.
- Desktop and 390 px mobile screenshots reviewed. All six mobile views checked
  without page-width overflow or broken images. Real NVIDIA inference unverified.
- Final verified JAR running at http://127.0.0.1:8091 for user review.
- English/Italian README instructions synchronized. No commits or deployments.

## Remaining Manual Review

Configure and test actual NVIDIA inference only with privately supplied credentials.
Select a real image provider before replacing the explicit demo adapter. Stop a
running JAR before rebuilding on Windows to avoid locked/replaced archive errors.
Do not provision cloud resources or create migrations for ephemeral H2.