# AI Usage Disclosure

This project was built with Claude (Anthropic) as an active development collaborator, per the
explicit permission in `CLAUDE.md`. This document is an honest account of how it was used.

## What AI did

- **Planning:** Read `CLAUDE.md`, `product-brief.md`, `architecture.md`, and
  `data-dictionary.md`, then proposed the MVP scope, screens, domain model, schema, package
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
  file" (CSV already is a text format; the complexity is in validation, not the file type), and
  instead found and applied three real simplifications:
  removed a server-side upload cache in favor of a stateless echo-the-content-back design,
  merged two near-duplicate error templates into one, and deleted two enum values
  (`NOTE_ADDED`, `FAILED`) that were declared but never actually produced anywhere in the
  application (confirmed by grepping, not assumed). While re-verifying the app live against
  Docker after these changes, found and fixed an unrelated pre-existing bug: unmapped routes were
  being caught by the generic exception handler and rendered as a 500 instead of a proper 404.
- **Code-review-driven bug fixes (a second AI reviewer pass, applied by Claude after discussion
  with Nitesh):** Four issues were raised against the running codebase, each investigated against
  the actual code before being accepted or explained away, then fixed with a regression test:
  1. A true simultaneous double-submit on lease assignment/status-change (as opposed to the
     already-handled stale-page case) surfaced Hibernate's own `ObjectOptimisticLockingFailureException`
     instead of the application's `LeaseVersionConflictException`, which meant it reached the
     generic 500 handler instead of the friendly conflict message. Fixed by catching both
     exception types with the same user-facing outcome.
  2. Reimporting a lease with a changed `property_external_id`/`tenant_external_id` (but otherwise
     identical fields) was silently classified as "Unchanged" and never reassigned the lease's
     property/tenant association - confirmed by tracing `ImportService.upsertLease()`, which never
     passed the resolved `Property`/`Tenant` into the update path. Fixed in `Lease.differsFrom()`/
     `applyImportedValues()` and covered with new Testcontainers tests that reimport a lease with
     only its property (then only its tenant) changed and assert the association actually moved.
  3. The lease-list "expiring within N days" filter (`LeaseSpecifications`) only checked
     `endDate <= cutoff`, with no lower bound, so already-expired leases were included alongside
     genuinely upcoming ones - inconsistent with `DashboardService`'s own definition of the same
     concept, which explicitly excludes them. Fixed by adding the missing `endDate >= asOf` bound
     and covered with a boundary test (expired, at-today, mid-window, at-cutoff, beyond-window).
  4. The import preview page re-submits the previewed CSV content as a plain form field at
     commit time (not multipart), which is governed by Tomcat's default 2MB form-post limit -
     distinct from the 5MB multipart upload limit configured for the preview step. A file between
     2-5MB would preview successfully but could fail to commit on size alone. Fixed by aligning
     `server.tomcat.max-http-form-post-size` with the existing 5MB multipart limit.

  All four fixes were verified with `./mvnw test` (81/81 passing, up from 75) and by rebuilding
  and restarting the actual containerized application (`docker compose up --build`), confirming
  the existing 30 demo leases survived the restart unchanged.

## What was verified, not assumed

- Every dependency version cited in this repository's `pom.xml` was checked against Maven
  Central's `latestVersion` metadata at the time of writing, not recalled from training data.
- `./mvnw test` was actually run to completion (81/81 passing) before being documented as a
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
  conflicts warn) is a deliberate judgment call, reasonable but worth confirming against actual
  business requirements.
- No security review beyond basic input validation and parameterized queries (via JPA) was
  performed; this is an internal demo application per its explicitly stated non-goals, not a
  production system.
