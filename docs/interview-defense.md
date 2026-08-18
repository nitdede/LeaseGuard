# Interview Defense

First-person answers (30-60 seconds each) to likely questions about LeaseGuard's design. Written
to defend real decisions made while building this, including two bugs actually caught during
development (noted where relevant) - not a generic architecture essay.

## Top 20 likely questions

**1. Why a modular monolith instead of microservices?**
For a single-team, single-deployable internal tool with a few thousand leases, microservices add
network calls, deployment complexity, and distributed-transaction problems for zero benefit. One
deployable, one database, clear class-level boundaries (services own business logic, repositories
own data access, controllers stay thin) - if a real scaling or team-ownership reason showed up
later, splitting it apart is a refactor, not a rewrite. I didn't pre-build that indirection
speculatively.

**1a. Why package-by-layer (`controller`/`service`/`repository`/`model`/`dto`) instead of
package-by-feature?**
I actually built this the other way first - one package per business capability
(`lease`, `portfolio`, `importdata`, `action`, `dashboard`), each holding its own entities,
services, repositories, and controller together - and switched to package-by-layer partway
through at Nitesh's explicit request. Both are legitimate, widely-used conventions; the trade-off
is real, not cosmetic. Package-by-feature keeps everything about one capability in one place and
lets internal collaborators stay package-private (real encapsulation enforced by the compiler).
Package-by-layer makes it immediately obvious "what kind of thing is this class" from its
package, which is the more familiar convention for engineers coming from a traditional layered
background - at the cost of package-private encapsulation between layers: types like
`ImportIssue`'s factory methods had to become `public` once its callers (`service`) and the type
itself (`dto`) landed in different packages, since Java has no "friend package" concept. Neither
is objectively more correct for a codebase this size; I'd defend either, and the switch itself is
documented in `docs/assumptions-and-tradeoffs.md` rather than silently overwritten.

**2. Why deterministic rules instead of ML for risk scoring?**
There's no historical labeled outcome data (we don't know which leases "should" have been
prioritized in the past), and the business users making decisions need to trust and explain the
score to their own stakeholders. A rules engine with fixed point values and reason codes is fully
auditable - every score comes with a human-readable "why."

**3. Why not a generic rules-engine framework?**
Eight rules that don't change shape (only their thresholds do) don't justify a DSL, a rule-graph
executor, or plugin architecture. `RiskScoreService` is one cohesive, linearly-readable method;
thresholds are externalized to configuration where they're genuinely likely to change per
portfolio.

**4. Walk me through the CSV import transaction boundary.**
`ImportService.commit()` is a single `@Transactional` method. It re-validates the *actual file
content* (not client-supplied row/error counts) before touching the database, and if any row has
an error, nothing is written - no property, tenant, or lease row. That's enforced by Spring's
transaction rollback on exception, not by application-level cleanup logic.

**5. Why re-validate on commit instead of trusting the preview?**
The preview and the commit are two separate HTTP requests. Trusting client-supplied preview
*results* (e.g., a hidden field claiming "0 errors") would let a modified request bypass
validation. My first design held the uploaded bytes in a server-side in-memory cache keyed by an
opaque token, so commit could re-fetch and re-parse them. I simplified that: the preview page now
echoes the *exact file content itself* back to the browser in a hidden field, and commit
re-validates that content from scratch. Trust-wise it's identical - either way, commit parses the
real file text rather than believing a claimed outcome - but the second version needed no
server-side cache, no token-expiry handling, and no extra classes. For a file this size (the demo
CSVs are a few KB) that trade-off is a clear win; it would need revisiting only if uploads were
expected to be large enough that echoing the full content back in every page load became
wasteful.

**6. How is import idempotency implemented?**
`lease_external_id` is a unique, indexed business key. On import, I look it up; absent means
insert, present-and-identical means no-op, present-and-different means update. I compare
`BigDecimal` fields with `compareTo`, not `equals`, so `500000` and `500000.00` aren't treated as
a spurious change.

**7. What happens with a duplicate ID within one file?**
I actually found a real bug here during manual testing: my first implementation only flagged a
duplicate `lease_external_id` when *both* occurrences also passed their own field-level
validation, because I was grouping by the parsed row. A row with a duplicate ID that *also* had
an unrelated field error (e.g., a bad enum value) never got flagged as a duplicate - only the
"cleaner" copy did. I fixed it by grouping on the raw CSV value instead of the parsed result, and
added a test (`ImportServiceIT.duplicateLeaseExternalIdWithinFileBlocksTheWholeBatch`) asserting
both rows get the duplicate error, not just one.

**8. Why is property-conflict an error but tenant-name-conflict a warning?**
Property attributes (name, address, type, size) are structural - the dashboard groups and sums
by them. A mismatch usually means bad data. A tenant's display name changing is lower stakes (a
rebrand, a typo fix); blocking an entire 500-row import over one tenant's name would be overly
strict, so it's a warning and the newest name wins.

**9. Why is risk score computed on read instead of stored?**
At this data scale (tens of thousands of leases at most, per the product brief), computing score
for every lease on each dashboard/list request is fast and has zero staleness risk - the score is
always consistent with whatever the lease data currently says, with no cache-invalidation logic
needed. I documented the threshold for revisiting this (materialized view) in
`docs/architecture.md`'s scale-discussion section.

**10. Why is risk-level filtering done in memory instead of in SQL?**
Risk level is a *derived* value, not a stored column - there's no SQL predicate for it. I push
every filter that *can* be expressed in SQL (property, city, status, manager, expiration window)
down via a JPA `Specification`, fetch that filtered set, score it in Java, then filter/sort/
paginate the scored results. That's a legitimate, documented trade-off given the data scale, not
an oversight.

**11. How does optimistic locking actually work here?**
Two layers: the `leases.version` column with JPA `@Version` is the durable, database-enforced
guarantee. On top of that, `LeaseService` does an explicit comparison - the lease-detail form
carries a hidden `version` field, and before mutating I compare it against the currently
persisted version and throw a friendly `LeaseVersionConflictException` if they differ. That gives
a clear, testable, user-facing "someone else changed this" message instead of a raw
`ObjectOptimisticLockingFailureException` stack trace.

**12. Why not just rely on the raw JPA optimistic-lock exception?**
I could have, but it's harder to produce a good user-facing message from it reliably across
different call paths, and it only fires at flush/commit time, which is less predictable to test
and reason about than an explicit, immediate comparison. The `@Version` column is still there as
the real safety net underneath.

**13. Why Flyway with `ddl-auto=validate` instead of `update`?**
`update` is unpredictable in production - it infers schema changes from entity mappings and can
silently do the wrong thing (or fail loudly at the worst time). Flyway migrations are explicit,
reviewable, versioned SQL; `validate` means Hibernate only checks that the entity mappings agree
with what Flyway actually created, catching drift at startup instead of at runtime. I actually
hit this directly during development: I mapped `state` as a plain `String` (Hibernate defaults to
`varchar`), but my migration declared `CHAR(2)`. Startup failed immediately with a clear
schema-validation error instead of silently working until some edge-case query broke - I fixed it
by changing the migration to `VARCHAR(2)` before anything had shipped.

**14. Why Testcontainers instead of H2 for integration tests?**
H2 doesn't enforce PostgreSQL-specific behavior - check constraints, `numeric` precision, unique
constraint error types - accurately enough to trust for a schema this deliberately constrained.
Testcontainers runs the exact same `postgres:16.4-alpine` image as production, so a passing test
means the real database really does reject invalid dates, negative rent, and duplicate external
IDs.

**15. Why Thymeleaf instead of a React/Vue SPA?**
A separate frontend build, its own deployment pipeline, and a JSON API layer add real complexity
for zero benefit to this internal MVP's core outcome - a leasing manager needing an actionable
priority list, not a rich client-side app. Server-rendered pages with Bootstrap and a little
Chart.js cover every required screen.

**16. Why bundle Bootstrap/Chart.js via WebJars instead of a CDN?**
The Docker image is then fully self-contained - the app works even on a machine with no outbound
internet access, which matches "Docker must be sufficient" better than depending on a CDN being
reachable at demo time.

**17. Why no separate REST/JSON API despite CLAUDE.md mentioning OpenAPI?**
The instruction says to add OpenAPI "where useful." Since the entire application is
server-rendered, a parallel `/api/**` surface would exist purely to exercise the tooling, not
because anything consumes it - that's exactly the kind of unproportional complexity I was asked
to avoid. If a real API consumer showed up, I'd add a small read-only surface then.

**18. How do you handle money and dates correctly?**
`BigDecimal` for all rent figures (never `double`/`float`, which can't represent currency
exactly), `LocalDate` for business dates (lease start/end, notice, contact - no time-of-day
ambiguity), and `Instant` for audit timestamps (`created_at`, `occurred_at` - unambiguous UTC
instants). The database mirrors this: `numeric(14,2)` for rent, `date` for business dates,
`timestamptz` for audit fields.

**19. How is the "as of" date made deterministic?**
`Clock` is injected everywhere date logic matters, bound to a fixed instant built from the
configurable `leaseguard.demo.as-of-date` property (default `2026-08-17`). Tests construct their
own `Clock.fixed(...)` instances, so boundary tests (exactly 90 days, exactly 91 days, etc.) are
reproducible regardless of when they actually run. A production profile would swap in
`Clock.systemUTC()`.

**20. What's the actual security posture here?**
None, deliberately - this is a trusted local/internal demo per the explicit non-goals (no auth,
no RBAC, no tenant isolation). What *is* done: all queries go through JPA/parameterized SQL (no
string-concatenated SQL, so no injection surface), file upload size is capped, uploaded content
isn't logged, and error pages don't leak stack traces to the browser. `docs/architecture.md`
lists what a production version would need (OIDC/SSO, RBAC, encrypted transport, audit retention).

## Five difficult cross-questions

**Q: Your risk score can exceed 100 (e.g., 110). Isn't that a bug?**
No - it's intentional. The score isn't a percentage; it's a weighted sum of independent signals,
and the *level* (HIGH at 70+) is what actually drives behavior and sorting. Capping it at 100
would either clip legitimate signal (two HIGH leases both hitting 110 would look identical to one
at 75) or require an arbitrary renormalization that makes the individual reason weights harder to
reconcile with the total. I chose to keep the raw sum meaningful and only floor at 0.

**Q: Two managers submit conflicting updates within milliseconds of each other. Walk me through
exactly what happens at the database level, not just the app level.**
Both requests read the lease at, say, version 3. The first request's transaction commits an
UPDATE that changes the row and bumps `version` to 4. My application-level check in the second
request already compares its captured `expectedVersion=3` against what it re-reads via
`findById` at the start of its own transaction - if that second read happens *after* the first
commit, it'll see version 4, immediately mismatch, and reject before ever attempting a write. If
the timing were tighter still (both transactions read version 3 before either commits, which
requires read-committed isolation and near-simultaneous execution), the second transaction's
UPDATE would still be protected by the real `@Version` column: Hibernate includes `WHERE
version = 3` in its UPDATE, that row no longer matches after the first commit, zero rows are
affected, and Hibernate throws `ObjectOptimisticLockingFailureException`. Either way, no silent
lost update is possible - my current implementation's window is tiny but not provably zero purely
via application-level comparison; the `@Version` column is that last line of defense.

**Q: Why didn't you write an automated test that literally restarts the application to prove
Flyway doesn't recreate tables?**
I verified this manually - `docker compose up --build`, imported data, `docker compose down`
(preserves the volume), `docker compose up` again, confirmed the same 30 leases were still there
and Flyway logged "no migrations to apply" rather than recreating anything. I didn't automate it
because doing so in JUnit would mean spinning up a second, separate Spring context against the
same Testcontainers Postgres instance mid-test, which is more test-infrastructure complexity than
the property warrants here - the existing `FlywayMigrationIT` (fresh DB, one migration applies
cleanly) plus the JPA-mapping validation that every other integration test implicitly relies on
already cover the two ways this could actually break.

**Q: Your dashboard computes risk for every lease on every request. What actually breaks first as
the portfolio grows, and at what size?**
The lease list and dashboard both load the full filtered lease set into memory via one query
(with an `@EntityGraph` fetch for property/tenant to avoid N+1), then score and sort in Java.
That's O(n) per request with a small constant factor - fine into the tens of thousands of leases
range the product brief describes. What breaks first isn't correctness, it's request latency
creeping up as n grows, and eventually memory pressure from holding the whole scored list before
paginating. The fix, when profiling actually shows this mattering, is a materialized risk view
recomputed on a schedule or on data change, which `docs/architecture.md` calls out explicitly as
a documented-not-built future step.

**Q: You claim "no partial imports," but you upsert properties and tenants in a loop before the
whole batch finishes. What if the JVM crashes mid-loop?**
The whole loop runs inside `ImportService.commit()`'s single `@Transactional` boundary. If the
JVM crashes mid-loop, the database transaction was never committed, so PostgreSQL rolls back
everything automatically on connection loss - there's no window where some properties are
committed and others aren't. The "loop" is just how the Java code is structured; from the
database's perspective it's all one atomic unit of work until the final commit at method return.
