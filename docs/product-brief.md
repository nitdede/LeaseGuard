# LeaseGuard Product Brief

## Problem

Commercial real estate asset and leasing managers manage leases across multiple properties using spreadsheets and disconnected systems. An expiration calendar alone is insufficient. Teams need to know which upcoming lease event creates the greatest rental-income and negotiation-timeline risk.

## Users

- Primary persona: Portfolio Leasing Manager
- Portfolio assumption: 10–500 properties and 100–20,000 active leases
- MVP demo: 8 synthetic properties and 30 leases

## Value proposition

LeaseGuard transforms raw lease data into an actionable, explainable priority list so teams can reduce missed notice deadlines, late negotiations, and avoidable rental exposure.

## MVP user journey

1. A manager selects a synthetic/example CSV file.
2. The system validates the file and displays a preview, warnings, and errors.
3. A valid file is imported in one atomic transaction.
4. The dashboard displays portfolio exposure and high-risk leases.
5. The manager uses filters to produce a focused action list.
6. The manager opens a lease and reviews the reasons behind its score.
7. The manager assigns an owner and records an action/status and note.
8. The updated action is reflected in the risk score and dashboard.

## Acceptance criteria

- The provided valid CSV imports without manual modification.
- Reimporting the same file or lease IDs does not create duplicates.
- The invalid CSV produces row- and field-specific errors, and no partial lease data is imported.
- Dashboard calculations are derived from imported data.
- Every non-zero risk score has at least one visible reason.
- Date-boundary tests cover exactly 90, 180, and 365 days.
- A user can assign a lease and update its workflow status.
- Action history behaves as an append-only audit trail.
- The application runs locally through Docker Compose, and README commands are verified.
- Flyway creates the schema on the first startup and does not recreate tables on subsequent startups.
- Demo data is imported explicitly and is not silently reloaded on every application restart.
- Docker Compose restarts preserve data; an explicit documented volume-removal command resets it.
- Unit, PostgreSQL integration, and focused MVC tests cover critical behavior.

## Success criteria for the take-home

- A reviewer can understand the problem, user, and business value within five minutes.
- A reviewer can run the application within ten minutes.
- The demo shows a complete CSV-to-prioritized-action workflow.
- The candidate can defend the scoring, import, data-model, and architecture trade-offs.

## Non-goals

PDF extraction, AI scoring, live notifications, market-data integration, enterprise authentication, lease accounting, and distributed architecture are outside the MVP.
