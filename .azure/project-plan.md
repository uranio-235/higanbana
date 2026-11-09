# Higanbana Project Plan

## Scope

Build a local Java Spring Boot monolith for adapting light novels into manga.
The user requested boilerplate and authorized autonomous implementation when
unavailable for further questions. No Azure resources or deployment are required.

## Technology

- Use an installed supported Java LTS and compatible stable Spring Boot/Spring AI releases.
- Maven Wrapper, Spring MVC, Thymeleaf, HTMX, Validation, Spring Data JPA, H2.
- H2 is in-memory: all relational data is lost when the process stops.
- Store accepted image files on disk; their database associations are ephemeral.
- Spring AI for bounded text requests to a configurable NVIDIA-compatible endpoint.
- Explicit deterministic demo mode without credentials; never label demo output as AI output.
- Image provider is not decided: provide an adapter boundary and explicit demo assets.
- No vector database, RAG, external queue, authentication service, or cloud provisioning.

## Screens And Workflow

1. Novels: enter a title and paste or upload UTF-8 text.
2. Chunks: presegment the full source locally without truncation; propose, revise,
   and confirm chunks. Persist every confirmed chunk in source order.
3. Panels: choose a chunk, request or revise a bounded proposal of N panels,
   and confirm the resulting ordered panel records.
4. Studio: select exactly one or two confirmed panels; draft, edit, and save
   the description used for image generation. Preserve panel order and ownership.
5. Generation: generate an image candidate from the saved prompt, then explicitly
   accept or reject it. Only accepted candidates become ordered manga entries.
6. Reader: display accepted entries for a novel in narrative order.

## Safety And State

- Validate all inputs and ownership at the service boundary.
- Enforce one/two-panel selection, same novel, valid state transitions, and
  idempotent approval; rejected/unreviewed images never appear in the reader.
- Confirmation is explicit; do not silently overwrite downstream work on replanning.
- Bound AI context and output size. Never send an entire large novel in one request.
- Keep API keys in environment variables; show useful errors without secrets.
- Bind the development server to localhost; this is not a production security setup.

## Verification

- Verify dependency compatibility and compile with Maven Wrapper.
- Run focused tests for lossless chunking, panel limits/ownership, and approval flow.
- Run a complete demo HTTP workflow across all six screens with no credentials.
- Check desktop/mobile rendering if browser tooling is available.
- Start the application on a free local port and report its URL.
- Provide synchronized English and Italian READMEs with run/configuration instructions.