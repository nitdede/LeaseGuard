# Future Roadmap

Ordered by business value. Nothing here is implemented in the MVP; each item lists the trigger
that would justify building it, per the "don't build for hypothetical scale" guidance in
`CLAUDE.md`.

## 1. Authentication, authorization, and multi-tenancy

Real users, roles (asset manager vs. portfolio manager vs. read-only executive), and audit trails
tied to real identities instead of a free-text "actor name" field. **Trigger:** moving beyond a
single-reviewer demo to any shared or multi-portfolio deployment.

## 2. Persisted, precomputed risk scores

Move from on-read scoring to a materialized view or a scheduled recomputation job, with scores
recalculated on data change and on a nightly "as of date" rollover. **Trigger:** profiling shows
on-read scoring is a measurable bottleneck, realistically in the tens-of-thousands-of-leases
range or higher, or when historical score trending becomes a product requirement.

## 3. Staged/chunked CSV import for large files

Replace the single-transaction, in-memory import with a staged pipeline (upload -> validate ->
stage rows -> background commit) with progress reporting. **Trigger:** files large enough that
synchronous request/response import latency becomes unacceptable to users, or file sizes that no
longer fit comfortably in the current in-memory validation pass.

## 4. Real notification delivery

Email or Slack alerts for overdue renewal notices and newly HIGH-risk leases, backed by a durable
outbox so notifications survive a crash between decision and delivery. **Trigger:** the product
moves from a pull dashboard to a push workflow tool.

## 5. Market and comparable-lease data integration

Pull market rent benchmarks to flag leases priced meaningfully below market, in addition to the
current date/exposure-driven scoring. **Trigger:** access to a licensed CRE market-data feed and
a validated hypothesis that this materially changes prioritization versus the current rules.

## 6. Lease accounting and CAM reconciliation

Track common-area-maintenance charges, percentage rent, escalations, and full GL-facing
accounting - a different, much larger product surface than portfolio risk prioritization.
**Trigger:** a decision to expand LeaseGuard from a risk/priority tool into a billing system,
which is a distinct build, not an incremental one.

## 7. Canonical integration contracts for multiple source systems

If lease data starts arriving from more than one upstream system (not just a single CSV export),
introduce a canonical import contract and reconciliation logic for conflicting records across
sources. **Trigger:** a second real data source appears.

## 8. Import error persistence and history

Store `ImportError` rows so a user can review why a past import attempt was rejected without
re-uploading the file. **Trigger:** real usage shows people revisit failed-import history often
enough to justify the extra schema and query surface (the MVP's synchronous preview already
covers the common case).

## 9. Read-only REST/JSON API with OpenAPI docs

A small `/api/**` surface (lease list, lease detail, dashboard summary) documented via
springdoc, for a future mobile client, BI export, or third-party integration. **Trigger:** an
actual consumer of the data outside the Thymeleaf UI.
