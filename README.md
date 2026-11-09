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
   reject explicitly. Approval is idempotent. An already covered panel cannot be
   approved again through another candidate. Approved images cannot be rejected.
6. **Lector**: only approved images appear, in narrative order, with download links.

**Personajes** is available in the novel sidebar independently of these six stages.
Each novel owns its character sheets through JPA `OneToMany` / `ManyToOne` with a
non-null foreign key. Create, edit, or delete a name (120 characters maximum) and
visual description (2,000 characters maximum). Deleting a sheet never deletes
existing drafts or candidates.

Selected character sheets are snapshotted into each studio draft, supplied to the
text assistant, and appended to the saved prompt sent to the image provider.
Editing a prompt cannot silently remove these references; later sheet changes
do not alter prepared drafts or generated images. Create a new draft to use an
updated sheet. Selection is optional for scenery-only images. Selected sheets
are capped at 3,000 combined characters, text context at 8,000 and the final image
prompt at 10,000; oversized selections are rejected, not silently truncated.

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
configuration migration; rotate any key previously saved in source or shared.
Do not commit the local file. Run Maven or the JAR from the project directory so
the relative import resolves, and restart after changing configuration. Application
data persists in SQLite. Integration tests override credentials with a fake test key
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
characters, with `max_tokens=1800`, one attempt, 10 s connection timeout and 60 s
read timeout. In NVIDIA mode the entire novel is processed in local windows of
at most 6,000 characters (or the chosen smaller chunk limit). Within each window,
the model groups numbered source units; validated end indices select exact source
substrings without rewriting or dropping text. Windows are processed sequentially,
so large novels can take multiple requests; the whole novel is never sent at once.
Panel planning sees one chunk; studio sees selected panel descriptions.
Output structures/counts are validated before persistence. Source
text is untrusted narrative content, not system instructions. This is not a
guarantee against model prompt injection; inspect proposals before confirming.

**Images always remain DEMO**, even when NVIDIA text is enabled. No real image
API is invented or called. Implement `ImageProvider` and replace the demo bean
once a provider contract is selected and verified. Only trusted, sanitized image
output may be returned by a future adapter, especially if using SVG.

To return to demo, set `app.text-mode=demo` in the local file and restart.

## Storage And Safety

- The application uses **SQLite**, stored in `./higanbana.db` relative to the
  working directory. Novels, characters, plans, panels, drafts, candidates, and
  reader associations survive restart. Database/WAL files are ignored by Git.
- Liquibase creates and upgrades the schema at startup. Hibernate uses
  `ddl-auto=validate`: it neither creates nor drops application tables.
- H2 is **test-only**, in memory, selected by `@ActiveProfiles("test")`.
  The tests run the same Liquibase changelog and Hibernate schema validation.
- Pending candidate bytes live in SQLite; rejection removes those bytes.
- Only approval writes a UUID-named file to `./data/manga`. Configure the path with
  `app.manga-dir` in properties. Normal transaction rollback cleans up a newly written file.
- Approved files and their SQLite associations both survive restart. Back up the
  database and `data/manga` together. Stop the application cleanly before copying
  files, or use SQLite's backup API; do not copy just the main file of a live WAL
  database. A process crash can still leave an orphan image file.
- SQLite enables foreign keys, WAL journaling, and a 10-second busy timeout.
  A one-connection JDBC pool serializes this local instance's transactions;
  SQLite does not provide row-level `FOR UPDATE` locks. This is not a multi-user
  authorization or multi-instance coordination setup.
- Forms use session CSRF tokens, strict same-site cookies, escaped Thymeleaf text,
  a same-origin CSP, and local browser assets. There is no production auth setup.
- HTML forms use HTMX navigation; importing a file uses standard multipart POST.
  Core workflows also work without JavaScript. No CDN is contacted at runtime.

## Database Migrations

The entry point is `src/main/resources/db/changelog/db.changelog-master.xml`.
It includes `changes/001-initial-schema.xml`, which creates the application tables,
ordered collections, relationship foreign keys, and unique indexes. Liquibase
tracks applied changes in `DATABASECHANGELOG` and does not repeat them on restart.

Add new versioned changelog files and include them from the master. Do not edit
applied changesets, clear checksums, or enable Hibernate schema updates to bypass
a migration failure. SQLite has limited `ALTER TABLE` support: plan later changes
accordingly. Test migrations on H2 and verify SQLite compatibility before release.
The old in-memory H2 sessions are not imported into SQLite.

## Verified Build

Pinned, resolvable Maven Central releases:

| Component | Version |
| --- | --- |
| Java | 21 (verified runtime/JDK 21.0.13) |
| Spring Boot | 3.5.7 |
| Spring AI | 1.1.0 (documented Boot 3.4/3.5 compatibility) |
| Maven / Wrapper plugin | 3.9.11 / 3.3.4, official only-script wrapper |
| SQLite JDBC / SQLite engine | 3.49.1.0 / 3.49.1 |
| Liquibase | 4.31.1, Boot-managed |
| Hibernate / community dialects | 6.6.33.Final, Boot-managed |
| H2 (tests only) | 2.3.232, Boot-managed |
| HTMX / Lucide | 2.0.4 / 0.468.0, vendored with licenses |

The POM includes MVC, Thymeleaf, Validation, Data JPA, SQLite, Liquibase, the Spring AI OpenAI
module (no credential-requiring AI autoconfiguration), and Boot test support.
H2 has test scope and is absent from the packaged application.

`./mvnw.cmd clean verify` runs lossless segmentation tests, service state/selection
tests, a complete seven-screen MockMvc/H2 workflow, character persistence and
orphan removal, prompt snapshots, multipart/CSRF/ownership checks, Liquibase
idempotency and H2 test isolation,
and mock NVIDIA HTTP contract tests. See
`target/surefire-reports` for results. The complete demo workflow was also checked
in a browser: zero reader images before approval and one afterwards. Desktop and
390 px mobile screenshots were reviewed; all six mobile views had no page-width
overflow or broken images. The new characters screen and two-panel/character
selection were additionally verified on desktop. Stop a running JAR before
rebuilding it on Windows.
SQLite was separately verified with real startup, schema validation, novel/character
creation and persistence across restart, foreign-key enforcement, a rolled-back
300 KB binary roundtrip, and an unchanged migration count on subsequent starts.
These are database checks, not a claim of live NVIDIA/image inference verification.

## Code Map

- `src/main/java/local/higanbana/domain`: one JPA entity per file, plus `Row` and
  `ReviewState`. `Novel` owns `NovelCharacter` through a bidirectional relationship.
- `Workflow.java`: transactional behavior, state checks, local approval storage.
- `Chunking.java`: lossless local segmentation.
- `TextAssistant.java`: explicit demo or configurable NVIDIA text via Spring AI.
- `ImageProvider.java` / `DemoImageProvider.java`: replaceable image boundary.
- `WebController.java` / `WebConfiguration.java`: MVC, UTF-8, CSRF, headers, timeouts.
- `src/main/resources/templates`: seven workspace views and actionable error screen.
- `src/main/resources/static`: responsive CSS, progressive JS, local libraries.
- `src/main/resources/db/changelog`: versioned Liquibase schema migrations.
- `src/test/resources/application-test.properties`: H2-only test configuration.
- `.azure/integration-plan.md`: route inventory and remaining verification notes.

This boilerplate is local-only and needs human review before any production use.