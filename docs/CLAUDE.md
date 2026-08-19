# LeaseGuard — Claude Development Instructions

## Mission

LeaseGuard is a take-home project for a Staff Software Engineer position at Newmark, built in
collaboration with Claude as an AI development partner (AI use is explicitly permitted for this
assignment). The engineering bar is deliberately high: a small, polished, production-shaped, and
fully explainable solution, where every design decision is intentional and defensible in a
technical walkthrough — not code generated for its own sake.

## Selected problem

Commercial real estate asset and leasing managers manage many properties and leases. Lease expirations, renewal-notice deadlines, rental exposure, ownership, and follow-up activity are often distributed across spreadsheets and systems. Teams therefore struggle to determine which lease requires action first.

LeaseGuard is an internal portfolio dashboard that imports synthetic CSV data, calculates an explainable risk score from critical dates and rental exposure, prioritizes high-risk leases, and tracks follow-up actions.

## Target users

- Primary: CRE Asset Manager or Leasing Manager
- Secondary: Property Manager or Portfolio Manager
- Core job to be done: “Show me which leases create the greatest portfolio risk, why they are risky, and what requires action first.”

## Required technical direction

- Java 21
- A current stable Spring Boot version compatible with Java 21
- Maven
- Spring MVC and Thymeleaf
- Bootstrap and Chart.js
- Spring Data JPA
- PostgreSQL
- Flyway
- Bean Validation
- Spring Boot Actuator
- OpenAPI/Swagger for REST endpoints where useful
- JUnit 5, Mockito, Spring Boot integration tests, and Testcontainers
- Docker Compose
- Modular monolith with one deployable application

Verify major dependency versions and compatibility against official documentation before selecting them.

## Simplicity constraints

- Build one Spring Boot application and one PostgreSQL database.
- Do not add a separate JavaScript build unless user explicitly approves it.
- Do not add Redis, a message broker, object storage, pgAdmin, service discovery, or cloud infrastructure to the MVP.
- Prefer Spring Boot conventions and direct, readable code over custom frameworks.
- Create abstractions only when they protect a real domain boundary, enable meaningful testing, or remove demonstrated duplication.
- Do not use event sourcing, CQRS, hexagonal-architecture ceremony, or a generic rules engine for this assignment.
- A reviewer must be able to trace CSV import → persistence → risk calculation → dashboard without navigating unnecessary indirection.

## Required repository deliverables

Create and maintain these implementation files in addition to application source:

- `pom.xml`
- `Dockerfile`
- `compose.yaml`
- `.dockerignore`
- `.gitignore`
- `.env.example` containing non-secret local defaults only
- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml` if a separate local profile adds clear value
- `src/main/resources/db/migration/V1__create_core_schema.sql` and later versioned Flyway migrations as needed
- Thymeleaf templates and static assets
- Unit and integration tests
- `README.md`
- Required documents listed later in this file

Do not generate Kubernetes manifests, Terraform, CI/CD pipelines, or cloud deployment files unless explicitly requested.

## Docker and local runtime requirements

`compose.yaml` must define only the services needed to run the MVP:

1. `postgres`
   - Use an official pinned PostgreSQL image version rather than `latest`.
   - Create the application database and user through environment variables.
   - Store database files in a named Docker volume so ordinary container restarts preserve data.
   - Include a `pg_isready` health check.
   - Expose the database port only for local development and document it.
2. `app`
   - Build from the repository `Dockerfile` using a multi-stage build.
   - Run as a non-root user where practical.
   - Wait for the PostgreSQL health check before startup.
   - Read connection settings from environment variables with safe local defaults.
   - Expose the application port and include an application health check if it remains simple.

The following reviewer flows must be supported and documented:

```bash
# Complete containerized application
docker compose up --build

# Stop containers while preserving database data
docker compose down

# Intentionally remove containers and the local database volume
docker compose down -v

# Run tests
./mvnw test
```

Also support a developer flow that starts only PostgreSQL with Docker and runs Spring Boot from the IDE or Maven, if doing so requires little additional configuration.

Do not require PostgreSQL, Maven, or Java to be installed on the reviewer's machine for the fully containerized flow; Docker must be sufficient. The repository may include Maven Wrapper so the non-containerized developer flow does not require a global Maven installation.

Never place real credentials in Git. `.env.example` must contain clearly marked local demo values only. Document how production secrets would be supplied without implementing a secret-management platform.

## Database schema lifecycle

- Flyway is the only owner of production-shaped schema creation and evolution.
- On application startup, Flyway must create missing tables by applying versioned migrations exactly once per database.
- Flyway's schema-history table records applied versions. Restarting the application must not recreate or drop tables.
- Set Hibernate schema behavior to `validate`; do not use `ddl-auto=create`, `create-drop`, or `update`.
- Do not combine Flyway with `schema.sql` or ORM-generated schema creation.
- Every schema change after V1 must be a new immutable migration such as `V2__add_lease_version.sql`. Do not edit an already-applied migration merely to change an existing database.
- Use primary keys, foreign keys, unique constraints, check constraints, indexes, appropriate precision/scale for money, and optimistic-lock version columns where justified.
- Use snake_case names consistently in SQL and document any important database constraints.

Expected core tables:

- `properties`
- `tenants`
- `leases`
- `lease_actions`
- `import_batches`
- `import_errors` only if persisted errors materially improve the demo; otherwise keep preview errors transient and explain the choice
- `flyway_schema_history` managed automatically by Flyway

Do not drop or truncate business tables during normal application startup.

## Demo-data lifecycle

- The database must start with schema only; do not silently reload the CSV on every application startup.
- The normal demo flow is an explicit import from the UI using `data/demo/lease_portfolio.csv`.
- Provide a clearly labeled “Load bundled demo data” action only if it reuses the same validation and import service as a file upload. Do not create a separate hidden seeding implementation.
- Import must be idempotent by stable external IDs. Reimporting the same valid dataset must not create duplicates.
- Before committing an import, show a preview containing new, changed, unchanged, warning, and error counts where feasible.
- Use one all-or-nothing transaction for the MVP import. If any error exists, persist no property, tenant, or lease rows from that batch.
- The invalid sample CSV must demonstrate errors without changing business data.
- Preserve imported data across `docker compose down` and application restarts through the named volume.
- `docker compose down -v` is the documented explicit reset mechanism. A development-only reset action may be added only if it is clearly guarded, confirmed by the user, and does not complicate the project.
- Do not run `data.sql` automatically on every startup.
- Demonstrate and test these cases: first import, exact reimport, changed existing lease, duplicate ID within one file, malformed row, conflicting property data, and full rollback after validation failure.

## MVP capabilities

1. Preview and import the provided CSV file.
2. Perform row-level validation and display actionable error messages.
3. Persist normalized Property, Tenant, Lease, and LeaseAction records.
4. Display a portfolio dashboard with:
   - total annual base rent;
   - leases expiring within 90, 180, and 365 days;
   - annual rent at risk;
   - overdue renewal-notice dates;
   - high-risk leases; and
   - unassigned leases.
5. Calculate an explainable lease risk score with contributing reasons.
6. Filter by property, city, risk level, expiration window, assigned manager, and workflow status.
7. Display lease details.
8. Assign a manager and update a lease action/status.
9. Show an audit-friendly action history.
10. Provide a repeatable synthetic demo-data import/reset flow.

## Explicit non-goals

- PDF or OCR lease extraction
- LLM- or ML-based risk scoring
- Actual email or SMS delivery
- Authentication, authorization, or enterprise SSO implementation
- External CRE or market-data APIs
- Real Newmark or client data
- Microservices, Kafka, Kubernetes, or Elasticsearch
- Full lease accounting, billing, or CAM reconciliation
- Production multi-tenancy

Do not implement these non-goals without approval.

## Risk-scoring baseline

The score must be deterministic and explainable. Inject `Clock` so tests remain deterministic. Make the default demo as-of date `2026-08-17` configurable and display it in the UI. A production profile may use the system date.

Baseline rules:

- Lease already expired and unresolved: +50
- 0–90 days to expiration: +40
- 91–180 days to expiration: +25
- 181–365 days to expiration: +10
- Renewal-notice deadline passed and lease unresolved: +35
- Annual base rent at or above the configurable high-rent threshold: +15
- Tenant contributes at least a configurable percentage of the property's rent: +10
- No manager assigned: +10
- Status is `RENEWAL_IN_PROGRESS`: -20
- Status is `RENEWED`: final risk score 0
- Status is `TENANT_EXITING`: keep the financial exposure visible; do not treat it as resolved unless an explicit business rule says so

Risk levels:

- HIGH: 70+
- MEDIUM: 40–69
- LOW: 1–39
- NONE: 0

Return human-readable reason codes with every non-zero result. Keep rules in one cohesive domain service. Do not create a premature generic rules-engine framework. Thresholds may be configuration-driven.

## CSV strategy

- Primary file: `data/demo/lease_portfolio.csv`
- Invalid examples: `data/demo/lease_portfolio_invalid.csv`
- The CSV is intentionally denormalized as an interchange format. Normalize it into the relational domain model during import.
- `lease_external_id` is the idempotency/business key. Reimporting the same file must not create duplicate leases.
- `property_external_id` and `tenant_external_id` are stable business references.
- Validate the complete file and show a preview/error report before persistence. Use an all-or-nothing transaction for the MVP to avoid a partial and misleading portfolio state.
- Discuss staged/chunked import as future work for large files; do not implement it in the MVP.
- Missing optional values are allowed. Missing required values produce row errors.
- Use `BigDecimal` for money, `LocalDate` for business dates, and `Instant` for audit timestamps.

## Architecture boundaries

Organize packages by technical layer:

```text
com.leaseguard
  controller    - Spring MVC @Controller classes (one per screen/feature)
  service       - application services and business logic (including CSV parsing internals)
  repository    - Spring Data JPA repositories and query-building helpers
  model         - JPA @Entity classes and their persisted-column enums
  dto           - view models, filter/criteria objects, and computed (non-persisted) value types
  exception     - custom exceptions and the global @ControllerAdvice handler
  config        - @Configuration classes, @ConfigurationProperties, and servlet filters
```

Within each layer, keep responsibilities to that layer's concern - controllers stay thin and
delegate to services; services own business/orchestration logic; repositories only do data
access. A class's package tells you what kind of thing it is (controller, service, model, ...),
not which business feature it belongs to - use the class name and Javadoc for that.

## Implementation sequence and approval gates

Before coding:

1. Read this file, all files under `docs/`, and the CSV data dictionary.
2. Inspect the repository state.
3. Restate the MVP, assumptions, non-goals, and proposed package/domain design.
4. Present the proposed screens, routes, schema, and implementation plan.
5. **Stop and obtain approval.**

After approval, implement small vertical increments:

1. Project skeleton, Maven Wrapper, configuration, Dockerfile, Docker Compose, and Flyway baseline
2. Domain model and repositories
3. CSV validation, preview, and idempotent import
4. Risk calculation with exhaustive boundary tests
5. Dashboard and filtering
6. Lease detail and action tracking
7. Integration tests and operational concerns
8. README, screenshots/demo script

After every increment:

- Explain what changed and why.
- Run relevant compilation and tests.
- Report the actual commands and results.
- List assumptions and remaining risks.
- Recommend a Git checkpoint. Never push without permission.

## Quality rules

- Never claim a test passed unless it was run.
- Do not hide CSV validation errors in logs. Return a usable row-level report.
- Never log full imported rows or sensitive values.
- Use pagination for unbounded lease lists.
- Use database constraints for invariants that must survive concurrency.
- Keep transactions in application/service operations, not controllers.
- Handle malformed files, duplicate IDs, invalid dates, negative rent, impossible square footage, and end-before-start dates.
- Use Post/Redirect/Get for form submissions where appropriate.
- Provide consistent error pages/responses with correlation IDs where useful.
- Keep the UI accessible and easy to demonstrate. Avoid decorative complexity.
- Add health/readiness endpoints, structured-logging guidance, and a basic production configuration discussion.
- Keep code, code comments, documentation, commit messages, API messages, and UI text in professional English.

## Testing requirements

Use the smallest useful test pyramid. Do not chase line coverage by testing framework getters or trivial mappings.

### Unit tests

Write fast unit tests for:

- Every risk-scoring rule
- Exact boundaries at expired, 0, 90, 91, 180, 181, 365, and 366 days
- Combined rules, deductions, minimum score behavior, and `RENEWED` behavior
- Human-readable reason codes
- Portfolio rent-concentration calculation
- CSV row validation and normalization
- Date, money, enum, blank-field, and square-footage validation
- Idempotency decision logic

### Integration tests

Use Testcontainers PostgreSQL for tests that depend on database behavior. Verify:

- Flyway migrations apply successfully to an empty PostgreSQL database
- JPA mappings match the migrated schema
- Unique and foreign-key constraints
- Valid atomic import and exact reimport without duplicates
- Invalid-batch rollback
- Updating an existing lease according to the documented import policy
- Optimistic-lock conflict behavior
- Dashboard aggregate queries and filters
- Lease-action persistence and ordering

Do not replace PostgreSQL integration tests with H2 if PostgreSQL-specific behavior matters.

### Web tests

Use focused Spring MVC/MockMvc tests for:

- Dashboard rendering
- Filter parameters
- CSV upload validation responses
- Successful form submission and Post/Redirect/Get behavior
- Lease detail and action update
- 404, validation-error, and optimistic-lock-conflict handling

### Verification requirements

- `./mvnw test` must pass.
- If JaCoCo is added, use it as a visibility tool, not as a substitute for meaningful assertions. Do not add a brittle global threshold merely for appearance.
- Tests must be deterministic and independent. Never depend on execution order or the developer's existing database.
- Use test-data builders or fixtures when they improve readability; do not create an elaborate testing framework.
- Record the exact final verification commands and actual results in the handoff summary.

## Code comments and documentation standards

- Write self-explanatory names and small cohesive functions first; comments must explain **why**, constraints, formulas, or non-obvious decisions—not restate the code.
- Add concise Javadoc to public domain services, important application-service methods, risk policy/rules, import orchestration, and non-obvious public types.
- Document risk-score inputs, output, boundary semantics, and reason codes.
- Document transaction and idempotency behavior where it is not obvious from the method signature.
- Add short comments around deliberately complex SQL or framework workarounds.
- Do not add comments to getters, setters, constructors, obvious CRUD methods, or every line.
- Do not leave commented-out code, unexplained TODOs, AI conversation text, or generated boilerplate that is not used.
- Public-facing error text and Javadoc must be professional English.

## README requirements

Create a reviewer-focused `README.md` and keep it accurate throughout development. It must include:

1. Project title and a one-sentence value proposition
2. Problem, target user, and why it matters to commercial real estate
3. What was built, with MVP scope and explicit non-goals
4. Screenshots or a short GIF only if they can be produced cleanly
5. Architecture summary and a compact diagram
6. Technology choices and concise trade-offs
7. Repository structure
8. Prerequisites for both Docker-only and IDE/Maven workflows
9. Exact commands for:
   - cloning/opening the repository;
   - starting the complete application;
   - starting only PostgreSQL for IDE development;
   - running tests;
   - viewing logs;
   - stopping while preserving data; and
   - explicitly resetting the database volume
10. Application URL, Swagger URL if present, and Actuator health URL
11. Database creation and Flyway migration behavior
12. How to import the valid CSV and what results to expect
13. How to try the invalid CSV and verify rollback/error reporting
14. Demo as-of date and how to override it
15. Risk-scoring rules and why they are explainable
16. Assumptions, limitations, security boundary, and known gaps
17. Test strategy and verified test commands
18. Troubleshooting for occupied ports, unhealthy PostgreSQL, clean rebuild, and Testcontainers/Docker issues
19. Future roadmap ordered by business value
20. AI usage disclosure

Run every documented command in an equivalent clean environment before marking the README complete. Never document commands that were not verified.

## Required implementation review checklist

Before final handoff, confirm each item explicitly:

- [ ] `docker compose config` succeeds
- [ ] `docker compose up --build` starts PostgreSQL and the application
- [ ] PostgreSQL health check passes before the application starts
- [ ] Flyway creates the schema on the first startup
- [ ] Restarting does not drop tables or duplicate data
- [ ] `docker compose down` preserves data
- [ ] `docker compose down -v` resets the database intentionally
- [ ] Valid CSV imports successfully
- [ ] Reimport does not create duplicates
- [ ] Invalid CSV produces actionable errors and zero partial business rows
- [ ] Dashboard metrics match independently calculated expected values
- [ ] Risk reasons match scores
- [ ] Filters, pagination, details, assignment, status updates, and history work
- [ ] Unit, integration, and web tests pass
- [ ] No secrets, real customer data, or unnecessary services are present
- [ ] README commands are verified
- [ ] Code and documentation are in professional English

## Required documentation

Maintain:

- `README.md`
- `docs/product-brief.md`
- `docs/architecture.md`
- `docs/data-dictionary.md`
- `docs/demo-script.md`
- `docs/future-roadmap.md`

The README must include verified startup/test commands, the business story, architecture summary, sample-data workflow, assumptions, non-goals, and future work.
