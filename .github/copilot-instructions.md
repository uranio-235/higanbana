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

## Verification

- Run focused tests for changed behavior. Stop any process using the packaged JAR
  before rebuilding that JAR on Windows.