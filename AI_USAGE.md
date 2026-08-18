# AI Usage Disclosure

This project was built with Claude (Anthropic) as an active development collaborator, per the
explicit permission in `CLAUDE.md`. This document is an honest account of how it was used.

## What AI did

- **Planning:** Read `CLAUDE.md`, `docs/product-brief.md`, `docs/architecture.md`, and
  `docs/data-dictionary.md`, then proposed the MVP scope, screens, domain model, schema, package
  structure, CSV import design, risk-scoring rules, and implementation phases. This plan was
  presented to Nitesh and approved before any application code was written.
- **Implementation:** Wrote the Spring Boot application code (domain entities, services,
  controllers, Thymeleaf templates), the Flyway migration, Docker/Compose configuration, and the
  full test suite (unit, Testcontainers integration, MockMvc web tests) directly.
- **Dependency verification:** Checked current stable versions of Spring Boot, Flyway,
  Testcontainers, springdoc-openapi-adjacent libraries, and PostgreSQL against Maven Central's
  authoritative metadata and official documentation rather than relying on memorized version
  numbers, since a plausible-sounding but wrong version is a real risk with an LLM's training
  cutoff. One initial web-search result claiming a newer Spring Boot major version existed was
  explicitly distrusted and cross-checked against Maven Central before a final choice was made.
- **Debugging real environment issues:** Diagnosed and fixed a genuine schema/JPA-mapping bug
  (`CHAR(2)` vs. Hibernate's default `varchar` mapping for the `state` column) caught by actually
  running `docker compose up --build`, not just by reading code. Diagnosed and fixed a Testcontainers/
  Docker API version negotiation failure against a newer Docker Desktop release, and a Surefire
  test-discovery gap (`*IT.java` classes are excluded by Surefire's default include pattern, which
  is normally the Failsafe plugin's job) that was silently skipping every integration test under
  a plain `./mvnw test` until caught by checking actual Surefire output rather than assuming
  green meant everything ran.
- **Manual end-to-end verification:** Before writing automated tests, manually exercised the
  running containerized application via `curl` - import, reimport idempotency, invalid-file
  rejection, optimistic-lock conflict behavior, dashboard KPI accuracy - and cross-checked the
  dashboard's numbers against an independent Python script computing the same aggregates directly
  from the CSV, rather than trusting the UI's own math.
- **Simplification review (requested by Nitesh after the initial build):** Reviewed the codebase
  for unnecessary complexity, pushed back on a suggestion to replace CSV with a generic "text
  file" (CSV already is a text format; the complexity is in validation, not the file type - see
  `docs/assumptions-and-tradeoffs.md`), and instead found and applied three real simplifications:
  removed a server-side upload cache in favor of a stateless echo-the-content-back design,
  merged two near-duplicate error templates into one, and deleted two enum values
  (`NOTE_ADDED`, `FAILED`) that were declared but never actually produced anywhere in the
  application (confirmed by grepping, not assumed). While re-verifying the app live against
  Docker after these changes, found and fixed an unrelated pre-existing bug: unmapped routes were
  being caught by the generic exception handler and rendered as a 500 instead of a proper 404.

## What was verified, not assumed

- Every dependency version cited in this repository's `pom.xml` was checked against Maven
  Central's `latestVersion` metadata at the time of writing, not recalled from training data.
- `./mvnw test` was actually run to completion (75/75 passing) before being documented as a
  working command - see the README's verified commands section for the exact output summary.
- `docker compose up --build`, `docker compose down`, and `docker compose down -v` were each
  actually run against this repository, not just written and assumed correct.
- The dashboard's KPI numbers (total rent, expiring-lease counts, overdue notices, unassigned
  leases) were independently recomputed from the raw CSV and compared against the running
  application's output.

## What a human should still double-check

- The specific numeric risk-scoring thresholds (high-rent dollar amount, tenant-concentration
  percentage) are configurable defaults chosen to produce a reasonable-looking distribution
  across the synthetic demo data; they are not derived from real CRE industry benchmarks and
  should be validated against real portfolio data before any production use.
- The property/tenant conflict-handling policy (property conflicts block the batch, tenant name
  conflicts warn) is a judgment call documented in `docs/assumptions-and-tradeoffs.md` - reasonable,
  but worth confirming against actual business requirements.
- No security review beyond basic input validation and parameterized queries (via JPA) was
  performed; this is an internal demo application per its explicitly stated non-goals, not a
  production system.
