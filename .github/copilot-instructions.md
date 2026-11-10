# Project Policies

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

- Keep Java package names aligned with source directories and give each top-level
  type its own file.
- Keep JPA entities, domain rules, and application-facing interfaces/ports in
  `domain` (group specialized contracts under packages such as `domain.ai`);
  use `application` for transactional workflows and use cases.
- Keep MVC controllers grouped by feature under `web/controller`; put request
  DTOs and cross-cutting web error handling in `web/dto` and `web/error`.
- Keep provider clients and storage adapters in `infrastructure`, implementing
  their domain interfaces; keep framework configuration in `config`.
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