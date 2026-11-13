# Project Policies

This is a project to learn Spring Boot and Java. The user comes from C# dotnet. 

## Educational Approach

- Treat coding tasks as opportunities to teach, not just deliver changes. Briefly
  explain the key decision and why it fits this project; keep routine or obvious
  details concise.
- When a Java or Spring concept differs meaningfully from C#/.NET, make the
  connection explicit and explain the practical difference. Do not force a
  comparison when it adds no clarity.
- For unfamiliar concepts, use a small example from the code being worked on,
  then point out the relevant tradeoff or common pitfall.
- During debugging, explain the evidence behind the diagnosis and how the check
  distinguishes between likely causes.
- Keep teaching proportional to the task. Make the change and verify it; do not
  turn routine work into a lecture or delay progress with background material.

## Layout

- Target desktop browsers. Mobile-specific layouts, responsive adaptations, and
  mobile viewport tests are not required unless explicitly requested.
- Do not remove existing responsive styles merely to enforce this policy.

## Optional And Data Types

- Do not introduce single-component records solely to wrap a value or its absence.
  Use the underlying type for required values and Optional<T> for optional results.
- Prefer Optional.ofNullable, map, flatMap, filter, orElseGet, and orElseThrow for
  optional values. Do not return null from an Optional-returning method, use
  unchecked get(), or routinely unwrap with orElse(null).
- Use Optional for local extraction and return values where absence is meaningful,
  not as an entity field, request parameter, or replacement for a collection.
- For one-field JSON responses, preserve the external object/field contract and
  extract the field with Optional; reject missing/null required fields explicitly.
- Multi-component records remain appropriate for actual composite data. Do not
  replace them with Optional, which expresses presence/absence, not aggregation.

## Java Structure

- Use `StringBuilder` with chained `append` calls to assemble long strings,
  such as AI prompts, instead of long `+` concatenation chains. Preserve the
  exact text and whitespace, and call `toString()` when passing the result.
- Keep Java package names aligned with source directories and give each top-level
  type its own file.
- Keep JPA entities, domain rules, and application-facing interfaces/ports in
  `domain` (group specialized contracts under packages such as `domain.ai`);
  use `application` for transactional workflows and use cases.
- Keep MVC controllers grouped by feature under `web/controller`; put request
  DTOs and cross-cutting web error handling in `web/dto` and `web/error`.
- Keep provider clients and storage adapters in `infrastructure`, implementing
  their domain interfaces; keep framework configuration in `config`.
- In `infrastructure.persistence`, name each Spring Data interface after its
  domain entity as `<Entity>Repository` and declare custom queries there with
  `@Query`. Name its JPA-facing adapter `<Entity>JPA`. Spring Data generates the
  repository implementation; the adapter delegates to it rather than
  implementing `JpaRepository` itself.
- Name a JPA adapter for a cross-entity domain port after that port's subject
  with the `JPA` suffix, for example `WorkflowJPA`.
- Do not move business rules into controllers or add unrelated endpoints to a
  generic controller just to share a file.

## Real Implementations

- Implement real application behavior going forward; do not add new demo modes.
- As each functionality receives a real implementation, remove its demo path,
  demo configuration switches, simulated results, and obsolete demo UI in the
  same change. Do not retain a parallel demo implementation for that functionality.
- Never fall back to simulated output when configuration, credentials, or a real
  provider fails. Report the failure explicitly; do not present it as success.
- If a functionality is not implemented yet, show an unavailable state rather
  than fabricated output. Remove existing demo behavior as that area is migrated.
- Mocks, stubs, and fixtures are allowed in tests only, not as selectable runtime
  providers or substitutes for real end-to-end verification.

## Verification

- Run focused tests for changed behavior. Stop any process using the packaged JAR
  before rebuilding that JAR on Windows.
- On Windows, run Maven commands from the repository root with `mvnw.cmd`; do not
  assume `mvn` is installed or available on `PATH`.