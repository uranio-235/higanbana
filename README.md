# Higanbana

Local light-novel-to-manga workbench. Seven Spanish-language screens: novels,
characters, chunk negotiation, panel negotiation, studio, candidate review, and reader.
Starts in **explicit demo mode**, with no credentials, cloud resources, or deployment.
No data is seeded at startup.

## Run on Windows

Requires a Java 21 JDK (`java -version` and `javac -version`). Maven installation,
Node.js, Docker, and Azure are not needed. First build needs internet access.

```powershell
Set-Location Q:\source\higanbana
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

Open http://127.0.0.1:8080. Stop with Ctrl+C. If the port is occupied, set
`server.port=8081` in `src/main/resources/application.properties` before starting.

Alternatively, after building: `java -jar target/higanbana-0.1.0.jar`.
On Unix use `sh ./mvnw clean verify` and `sh ./mvnw spring-boot:run`.
The server binds to **127.0.0.1**; do not expose this unauthenticated local tool.

### Windows Certificate Trust

On this machine Java's default trust store could not validate Maven Central.
Using the Windows root store resolved it without disabling TLS verification:

```powershell
$env:MAVEN_OPTS='-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'
.\mvnw.cmd clean verify
```

Only use this on Windows when needed. If a standalone JAR also needs that trust
store, pass those same `-D` options to `java` before `-jar`. Do not bypass TLS.
If the wrapper's initial download itself fails, configure your trusted network
certificate/proxy for PowerShell first.

## Demo Workflow

1. **Novelas**: title plus pasted text OR a `.txt` UTF-8 upload (2 MB maximum;
   source limited to 1,000,000 UTF-16 code units). Invalid UTF-8 is rejected.
2. **Chunks**: propose with a 200-8,000 character limit; revise the size and notes;
   inspect the complete segments; explicitly confirm. Concatenation reproduces
   the original exactly, including whitespace, CRLF, and Unicode. Boundaries
   prefer nearby line breaks and never split a surrogate pair.
3. **Paneles**: choose a confirmed chunk; propose 1-12 panels; revise the direction
   or edit each description (3,000 characters maximum). Save edits before
   explicitly confirming the saved proposal.
4. **Estudio**: choose exactly one or two distinct confirmed panels from the same
  novel. They are sorted in narrative order. Select the characters in the scene,
  create a description, edit it, and save it (6,000 characters maximum).
  Multiple drafts are allowed.
5. **Generacion**: generate from the **saved** description. Candidates snapshot
   that description; later edits do not change existing candidates. Approve or
   reject explicitly. Approval is idempotent. Approving a candidate that covers
   every panel of an existing approved image replaces it: the old reader entry is
   removed, its candidate becomes rejected and its file is deleted after commit.
   A candidate that would leave some of those panels without an image is refused.
   Approved images cannot be rejected.
6. **Lector**: only approved images appear, in narrative order, with download links
   and an on-demand PDF download (one approved image per page). Multi-panel
   compositions stay intact; the exporter does not guess panel crop boundaries.

**Personajes** is available in the novel sidebar independently of these six stages.
Each novel owns its character sheets through JPA `OneToMany` / `ManyToOne` with a
non-null foreign key. Confirm the narrative chunks first, then select and examine them.
Each action sends the complete selected chunk and existing character sheets in one
NVIDIA request. It creates new sheets and enriches known characters with newly discovered
stable traits in complete visual prompts (2,000 characters maximum). Prompts describe
reusable identity, not poses, framing, settings, lighting or temporary states such as
wet hair. Previous stable traits and user revisions are supplied as context to preserve
them. New traits must be explicitly stated for that character, not inferred from
setting, names or stereotypes. Unknown attributes are omitted rather than filled in.
Clothing worn in a scene (for example a festival yukata) is not habitual clothing
unless the text explicitly establishes that. These are model instructions, not a
deterministic factuality check. Existing sheets are not automatically regenerated;
scene-specific details in older prompts are removed when that character is
examined or revised again. Unmentioned sheets are unchanged. Progress is saved;
names are matched case-insensitively,
although aliases may still require manual cleanup. The prompt is read-only: provide
instructions to NVIDIA to revise it in a separate request. Provider errors leave the
chunk pending and existing characters unchanged. Character management
is handled by `CharacterManager`, independently of the numbered workflow. Deleting a sheet never deletes
existing drafts or candidates.

Both panel-prompt generation and final studio-prompt generation expose the
read-only `lookup_characters` tool. The initial request includes the novel's
character names, not all descriptions. The model can request all relevant sheets
in one batch; only sheets from that novel are available. The backend supplies
their stored descriptions without another AI extraction. Unknown names, invalid
arguments, multiple tool calls or a second tool round fail explicitly. A generation
uses at most two text-model requests before retries: one to request the sheets and
one to produce the final JSON. With no lookup it uses one; cache hits use none.
No request pacing is implemented here.

The model is instructed to incorporate known identity traits without inventing
unknown attributes; the scene remains the source for temporary clothing, actions,
expressions and setting. These instructions are not a deterministic guarantee of
model adherence. The configured provider/model must support OpenAI-compatible
tool calling; unsupported requests are reported, not replaced with simulated output.
Cache keys include the complete available sheets, so updating or deleting a sheet
invalidates the corresponding generation. Cached tool results retain the consulted
sheet snapshots.

Studio character checkboxes remain optional explicit references, not a prerequisite
for automatic lookup. Explicitly selected and automatically consulted sheets are
snapshotted into each studio draft and appended when copying its complete image
prompt. Editing a prompt cannot silently remove these references; later sheet
changes do not alter prepared drafts or uploaded images. Create a new draft to use
updated sheets. Used sheets are capped at 3,000 combined characters. Panel source
context is capped at 8,000 characters, studio references at 30,000, and generated
studio prompts at 10,000 including mandatory drawing rules (up to 41,000 with source
references). Oversized inputs are rejected, not silently truncated.

Confirmed chunk/panel plans are immutable. Replanning is refused rather than
deleting downstream work. To explore another confirmed segmentation, import a
new novel. Draft proposals remain editable until confirmation.

All text and image results are labelled with their origin. Demo text is a local
deterministic draft, **not an AI response**. The image is a real, rendered,
original SVG illustration, prominently watermarked DEMO; it is a generic local
illustration with minor prompt-dependent variation, **not a semantic image model**.

## Models And Local Configuration

Application settings are literal values in `src/main/resources/application.properties`;
no environment variables are required. The model settings are independent:

```properties
app.text-mode=demo
app.text.model=meta/llama-3.1-8b-instruct
app.image.model=
app.nvidia.base-url=https://integrate.api.nvidia.com
app.ai.cache.ttl=30d
```

`app.text-mode` accepts only `demo` or `nvidia`, not `text`. The text model is used
only in NVIDIA mode. The image model is intentionally blank and reserved for a
future image adapter: setting it does not activate real image generation.

The optional `application-local.properties` in the project working directory is
imported automatically and overrides public settings. It is ignored by Git and
is outside the packaged resources. Keep credentials there, not in source files:

```properties
app.text-mode=nvidia
app.nvidia.api-key=your-new-token
```

Replace the placeholder privately. An existing local key is preserved during
configuration; rotate any key previously saved in source or shared.
Do not commit the local file. Run Maven or the JAR from the project directory so
the relative import resolves, and restart after changing configuration. Application
data persists in DuckDB. Integration tests override credentials with a fake test key
and force demo text mode; NVIDIA HTTP contract tests use a mock server.

Spring AI uses the OpenAI-compatible chat-completions contract:
`POST https://integrate.api.nvidia.com/v1/chat/completions`.

Keep the base URL **without `/v1`**; Spring AI appends `/v1/chat/completions`.
Use a model currently enabled for your NVIDIA account. The sample model's live
availability and credentials have **not** been tested. The transport contract,
Authorization header, output cap, and error behavior are tested with mock HTTP,
not a live NVIDIA inference call. This is not a provider availability guarantee.
Missing credentials or text model in NVIDIA mode fail startup. Errors never silently fall back
to demo or display provider response bodies/API keys.

Each text request is bounded to 8,000 context characters plus 1,500 revision
characters, with `max_tokens=1800`, one attempt, a configurable `app.nvidia.connect-timeout`
(default 10 s) and `app.nvidia.read-timeout` (default 180 s). In NVIDIA mode the entire novel is processed in local windows of
at most 6,000 characters (or the chosen smaller chunk limit). Within each window,
the model groups numbered source units; validated end indices select exact source
substrings without rewriting or dropping text. Windows are processed sequentially,
so large novels can take multiple requests; the whole novel is never sent at once.
Panel planning sees one chunk; studio sees selected panel descriptions.
Output structures/counts are validated before persistence. Source
text is untrusted narrative content, not system instructions. This is not a
guarantee against model prompt injection; inspect proposals before confirming.

### Manga prompts and lettering

`TextAssistant` owns all prompt instructions, context formatting, references, and
final image-prompt assembly. `Workflow` validates selections and passes structured
data using `ChunkRequest`, `PanelPromptRequest`, and `StudioPromptRequest` records,
created with their Immutables-generated builders. It persists the returned prompt
unchanged; character snapshots are also formatted by the assistant.

Generated panel and studio prompts start with application-owned manga drawing and
lettering instructions, even if the text model returns only scene descriptions.
They require monochrome ink and halftones, readable black text in white speech
bubbles with tails toward the speaker, and separate tailless narrator captions.
The text assistant is asked to quote each literal line with its speaker and panel,
or mark a silent panel explicitly. Original-scene references are context, not text
to print wholesale. The instructions count toward the existing 1,500-character
panel and 10,000-character generated studio prompt limits; oversized responses are
rejected rather than truncated.

Existing saved prompts and images are not rewritten. Regenerate panel prompts and
create a new studio draft to apply the updated instructions. User edits remain
authoritative. Images are generated externally and uploaded for review; prompt
contract tests do not guarantee that an external model renders lettering correctly.

Successful NVIDIA text responses are cached in DuckDB for 30 days by default.
`app.ai.cache.ttl` configures the retention period. The SHA-256 key covers the
operation, provider endpoint, model, prompt version, generation parameters, and
the complete final request text, so changed context or instructions trigger a new
inference. Identical concurrent requests in this process share one in-flight call.
Only responses that pass operation-specific validation are stored; provider errors
and malformed output are never cached. The original prompt/source is not copied to
the cache table, only its hash, metadata, and validated response. Cached responses
are still model output: review them before confirming chunks or panels. To force a
fresh response after a prompt implementation change, increment `PROMPT_VERSION` in
`TextAssistant`; existing entries expire naturally. Expired rows are ignored and
cleaned on subsequent cache writes.

**Images always remain DEMO**, even when NVIDIA text is enabled. No real image
API is invented or called. Implement `ImageProvider` and replace the demo bean
once a provider contract is selected and verified. Only trusted, sanitized image
output may be returned by a future adapter, especially if using SVG.

To return to demo, set `app.text-mode=demo` in the local file and restart.

## Storage And Safety

- The application uses **DuckDB**, stored in `./higanbana.duckdb`. Novels,
  characters, plans, panels, drafts, candidates, and reader associations survive
  restart. DuckDB database/WAL files are ignored by Git.
- DuckDB's schema is initialized from `src/main/resources/db/duckdb/schema.sql`.
  Hibernate uses PostgreSQL-compatible SQL with automatic DDL disabled; a shared
  sequence generates IDs. H2 and Liquibase remain test-only.
- Chunk plans store their confirmation as a separate child row. This avoids
  updating a DuckDB parent row while `chunk_plan_segments` references it; startup
  copies confirmation state from existing plans into the new marker table.
- Pending candidate bytes live in DuckDB; rejection removes those bytes.
- Only approval writes a UUID-named file to `./data/manga`. Configure the path with
  `app.manga-dir` in properties. Normal transaction rollback cleans up a newly written file.
- Approved files and their DuckDB associations both survive restart. Back up the
  database and `data/manga` together. Stop the app cleanly before copying DuckDB
  and its WAL. A process crash can still leave an orphan image file.
- DuckDB is embedded and the one-connection JDBC pool is for this local experiment;
  explicit row locks are omitted.
- AI response cache metadata and validated responses are stored in DuckDB;
  prompts and source text are not. Cache retention defaults to 30 days.
- Forms use session CSRF tokens, strict same-site cookies, escaped Thymeleaf text,
  a same-origin CSP, and local browser assets. There is no production auth setup.
- HTML forms use HTMX navigation; importing a file uses standard multipart POST.
  Core workflows also work without JavaScript. No CDN is contacted at runtime.

## DuckDB Schema

The active DuckDB schema is `src/main/resources/db/duckdb/schema.sql`, applied
idempotently on startup. Add experimental schema changes there. H2 tests continue
to run the test Liquibase changelog independently. The AI response cache table is
created in both schemas. DuckDB vector search/embeddings
are not wired into the application yet.

## Verified Build

Pinned, resolvable Maven Central releases:

| Component | Version |
| --- | --- |
| Java | 21 (verified runtime/JDK 21.0.13) |
| Spring Boot | 3.5.7 |
| Spring AI | 1.1.0 (documented Boot 3.4/3.5 compatibility) |
| Maven / Wrapper plugin | 3.9.11 / 3.3.4, official only-script wrapper |
| DuckDB JDBC / engine | 2.0.0.1 / 2.0.0.1 |
| PDFBox / Batik | 3.0.8 / 1.20 |
| Hibernate | 6.6.33.Final, Boot-managed |
| Liquibase / H2 (tests only) | 4.31.1 / 2.3.232, Boot-managed |
| HTMX / Lucide | 2.0.4 / 0.468.0, vendored with licenses |

The POM includes MVC, Thymeleaf, Validation, Data JPA, DuckDB JDBC, PDFBox/Batik,
Liquibase/H2 for tests, Spring AI OpenAI, and Boot test support.

`./mvnw.cmd clean verify` runs lossless segmentation tests, service state/selection
tests, a complete seven-screen MockMvc/H2 workflow, DuckDB JPA startup and sequence
tests, character persistence and orphan removal, prompt snapshots, multipart/CSRF/
ownership, and H2 test isolation, mock NVIDIA HTTP contract tests, DuckDB/H2 cache
persistence, expiry, cache hits, request-key separation, invalid-response handling,
and confirmation of chunks against DuckDB. The verified suite passes 58 tests; see
`target/surefire-reports` for results. The complete demo workflow was also checked
in a browser: zero reader images before approval and one afterwards. Desktop and
390 px mobile screenshots were reviewed; all six mobile views had no page-width
overflow or broken images. The new characters screen and two-panel/character
selection were additionally verified on desktop. Stop a running JAR before
rebuilding it on Windows.
The real app was started against DuckDB and loaded the four stored novels, including
the 35,000-character source.
These are database checks, not a claim of live NVIDIA/image inference verification.

## Code Map

- `src/main/java/local/higanbana/domain`: one JPA entity per file, plus `Chunking`, `Row` and
  `ReviewState`. `Novel` owns `NovelCharacter`; chunk confirmation is an immutable
  child marker to avoid DuckDB's parent-update/foreign-key constraint.
- `src/main/java/local/higanbana/application/Workflow.java`: transactional
  application workflow and ownership/state checks; approved images go through
  the `ApprovedImageStorage` port and its filesystem adapter.
- `src/main/resources/db/duckdb/schema.sql`: active DuckDB tables and indexes.
- `src/main/java/local/higanbana/infrastructure/ai`: NVIDIA client and persistent
  validated-response cache; `JdbcAiResponseCache` is backed by DuckDB JDBC.
- `src/main/java/local/higanbana/infrastructure/ai/TextAssistant.java`: NVIDIA
  text provider and bounded narrative segmentation.
- `src/main/java/local/higanbana/infrastructure/image`: image-provider contract
  and current demo adapter.
- `src/main/java/local/higanbana/application/MangaPdfExportService.java` and
  `src/main/java/local/higanbana/infrastructure/pdf`: on-demand PDF export using
  PDFBox; approved SVG panels are rasterized with Batik.
- `src/main/java/local/higanbana/domain/export/MangaPdfGenerator.java`: the PDF
  generation port used by the PDFBox adapter.
- `src/main/java/local/higanbana/web/controller`: workspace, character, chunk,
  panel, studio, generation, and image controllers; route URLs remain unchanged.
- `src/main/java/local/higanbana/web/dto` and `web/error`: form DTOs and global
  MVC error handling. `src/main/java/local/higanbana/config`: web security headers,
  CSRF, and provider timeouts.
- `src/main/resources/templates`: seven workspace views and actionable error screen.
- `src/main/resources/static`: responsive CSS, progressive JS, local libraries.
- `src/main/resources/db/duckdb/schema.sql`: active DuckDB schema initialization.
- `src/main/resources/db/changelog`: legacy schema used by H2 tests only.
- `src/test/resources/application-test.properties`: H2-only test configuration.
- `.azure/integration-plan.md`: package map, route inventory, and verification notes.

This is an experimental learning project; DuckDB VSS/embedding search is a later step.