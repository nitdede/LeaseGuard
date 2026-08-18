# Assumptions and Trade-offs

This document records decisions made during implementation that were not fully specified in
`CLAUDE.md` or `docs/data-dictionary.md`, along with the reasoning, so they can be defended or
revisited deliberately rather than rediscovered by reading code.

## Package structure: package-by-layer, not package-by-capability

**This is a mid-project reversal, recorded here on purpose rather than silently overwritten.**
The application was originally built with one package per business capability
(`com.leaseguard.lease`, `.portfolio`, `.importdata`, `.action`, `.dashboard`, `.shared`), each
containing its own entities, services, repositories, and a `web` sub-package for its controller.
`CLAUDE.md` originally mandated exactly this ("organize packages by business capability").

Nitesh explicitly asked for a conventional layered structure instead - `controller/`, `service/`,
`repository/`, `model/`, `dto/`, `exception/`, `config/` - so every file's package tells you what
*kind* of class it is, not which feature it belongs to. Both are legitimate, widely-used
conventions in real Spring codebases; this was a judgment call, not a correctness issue, so the
refactor was done deliberately with `CLAUDE.md`'s "Architecture boundaries" section updated to
match, rather than leaving the code and its own governing document contradicting each other.

**What was gained:** immediate discoverability of "what kind of thing is this" from the package
name - `controller/` has every route handler, `service/` has every piece of business logic,
`repository/` has every data-access interface, `model/` has every JPA entity, `dto/` has every
view/filter/computed-value type. Familiar to engineers coming from a traditional layered
background.

**What was given up:** several types that were previously package-private "internal
implementation detail, not part of the public API" (e.g. `ImportIssue`'s `error(...)` and
`warning(...)` factory methods, several CSV-parsing collaborator classes) had to become `public`
once their callers and their own type ended up in different top-level packages - Java has no
"visible to this one other package" access level, only `public` or same-package. Feature-package
boundaries had given real, compiler-enforced encapsulation between capabilities; layer-package
boundaries don't provide that between features (a `service` class can technically reach into any
`repository`, `model`, or `dto` regardless of which feature it's "supposed" to belong to). At this
codebase's size that trade-off is a reasonable one; it would be worth reconsidering if the number
of features grew enough that "everything in `service/`" became difficult to navigate.

**Verification after the refactor:** full test suite (75/75) re-run and passing, plus a live
`docker compose up --build` smoke test (import → commit → dashboard → lease detail → 404 handling)
confirming byte-for-byte identical behavior to before the refactor - this was a structural
reorganization, not a behavior change.

## Import policy decisions

**Property conflict = error, tenant conflict = warning.** A property's name/address/type/size
differing from a prior import (or from another row in the same file) for the same
`property_external_id` blocks the whole batch. A tenant's name differing for the same
`tenant_external_id` is a non-blocking warning; the import proceeds and the newest name wins.
Rationale: property attributes are structural (used for dashboard grouping, rent-per-square-foot
context); drift there usually signals a data error worth stopping for. A tenant's display name
changing (e.g., a rebrand, a data-entry correction) is lower-stakes and shouldn't block an
otherwise-valid file.

**"Unresolved" means status != RENEWED**, uniformly, for both the expired-lease rule and the
renewal-notice-passed rule. `TENANT_EXITING` is deliberately *not* treated as resolved - the
point of that status is to keep financial exposure visible while the tenant is on their way out.

**Duplicate `lease_external_id` detection uses the raw CSV value**, not the parsed row. A row can
fail unrelated field validation (e.g., an invalid `property_type`) and still need to be flagged
as a duplicate of another row - otherwise only the "cleaner" of two duplicate rows would show the
duplicate error, which is confusing in the preview report.

**No `import_errors` table.** Row-level errors are shown immediately in the preview response and
the batch is rejected wholesale on commit, so nothing invalid is ever persisted. Storing rejected
rows in a table would add schema and query surface without a corresponding feature (nothing in
the MVP journey re-reads historical import failures).

**`import_batches` rows are written only for successful commits.** A failed batch persists no
business data and there is no in-progress "batch" identity worth remembering; recording failed
attempts would require a separate not-fully-designed audit trail for a case the UI already
surfaces synchronously.

**Idempotency comparison ignores decimal scale.** `500000` and `500000.00` are treated as the
same `annual_base_rent` (`BigDecimal.compareTo`, not `equals`), since CSV formatting differences
shouldn't manufacture a spurious CHANGED row.

## Risk scoring decisions

**Score is not capped**, only floored at 0. A lease can score above 100 (e.g., 110) when several
rules stack; the risk *level* (HIGH at 70+) is what drives the UI, so an uncapped raw score still
sorts and displays sensibly and preserves the individual reason weights for transparency.

**Rent-concentration and risk score are computed on read**, not persisted, using one aggregate
query per request (`sumAnnualBaseRentByProperty`) plus in-memory scoring over the fetched lease
list. This is appropriate at the MVP's expected scale (tens of thousands of leases) and keeps the
score always consistent with current data with no cache-invalidation problem. `docs/architecture.md`
discusses the future move to a materialized view if profiling ever shows this is a bottleneck.

**Dashboard "annual rent at risk"** sums `annualBaseRent` across leases with risk level HIGH or
MEDIUM - the leases that actually warrant attention - rather than every dollar under management.

**"Expiring within N days" KPIs are cumulative and forward-looking**: `0 <= daysToExpiration <= N`.
Already-expired leases are intentionally excluded from these counts (they're surfaced via the
`overdueRenewalNotices` KPI and the HIGH-risk "expired" reason instead), so the two metrics don't
double-count the same leases under different labels.

## Web and API decisions

**No separate REST/JSON API.** `CLAUDE.md` allows OpenAPI/Swagger "where useful," but this
application's UI is server-rendered Thymeleaf end to end. Adding a parallel `/api/**` surface
purely to exercise OpenAPI tooling would be extra maintained surface with no consumer, which cuts
against the explicit instruction to keep the build proportional to the MVP. If a real API
consumer emerges (a mobile app, a BI export), it should be added deliberately at that point.

**Concurrency control is a manual version check, not raw JPA optimistic-lock exceptions.** The
lease-detail form carries a hidden `version` field; `LeaseService` compares it against the
currently persisted `Lease.version` before mutating and throws a friendly
`LeaseVersionConflictException` if they differ, which the controller turns into a flash message
instead of a stack trace. The real `@Version` column is still there as the durable low-level
guarantee (any raw `save()` racing outside this application would still get Hibernate's own
`ObjectOptimisticLockingFailureException`), but the application's primary UX path uses the
explicit comparison because it produces a clear, testable, user-facing message.

**Actor name has no authentication behind it.** Every write action collects a free-text "your
name" field. This is explicitly a stand-in for the non-goal of real authentication - see the
security boundary in `docs/architecture.md` and the README.

**Import preview/commit is stateless - no server-side upload cache.** The first working version
held uploaded bytes in an in-memory, token-keyed cache between `POST /import/preview` and
`POST /import/commit`, so commit could re-fetch and re-validate the original content. That was
replaced with a simpler design: the preview page echoes the exact file content back to the
browser in a hidden field, and commit re-validates that content directly. Both designs give the
same trust guarantee (commit always re-parses real file text, never a client-claimed outcome);
the second one needed no cache, no eviction policy, and no "token expired" failure mode. This
only holds up because the CSVs involved are a few KB - if uploads were expected to be large,
round-tripping full content through every page load would be the wrong trade-off and the cache
approach (or server-side staging) would come back.

**One shared `error.html` template instead of per-status pages.** `error/404.html` and
`error/500.html` started as separate files that were byte-for-byte identical except for a title
and a sentence of body text. They were merged into one `templates/error.html` parameterized by
`title`/`message` model attributes, with sensible Elvis-operator defaults so it still renders
reasonably if a caller doesn't set them (e.g., Spring Boot's own default error-view resolution
falling back to it for a case `GlobalExceptionHandler` doesn't explicitly handle).

**`LeaseActionType.NOTE_ADDED` and `ImportBatchStatus.FAILED` were removed.** Both were declared
but never actually produced anywhere in the application (verified by grepping `src/main` before
removing them) - dead enum values that implied capabilities the app doesn't have. The matching
database `CHECK` constraints were tightened to match.

## Infrastructure decisions

**PostgreSQL image pinned to `postgres:16.4-alpine`.** A mature, widely deployed major version;
newer majors (17, 18) exist but add no functionality this MVP needs, and 16 minimizes the chance
of a reviewer hitting an unfamiliar edge case.

**UI assets (Bootstrap, Chart.js) are bundled via WebJars, not CDN links.** The Docker image is
then fully self-contained - the dashboard renders correctly even if the machine running it has no
outbound internet access - which matches the "Docker must be sufficient" requirement better than
a CDN dependency would.

**Maven Surefire is configured to also pick up `*IT.java` classes** (normally the Failsafe
plugin's naming convention) so that a single `./mvnw test` runs unit, MockMvc, and Testcontainers
integration tests together, matching the exact command documented in `CLAUDE.md` and the README
rather than requiring a separate `./mvnw verify` step.
