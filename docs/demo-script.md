# Demo Script

A ~5 minute walkthrough for a live or recorded demo. Assumes the app is already running via
`docker compose up --build` at http://localhost:8080.

## 1. The problem (30 seconds)

"A leasing manager tracking dozens or hundreds of leases across spreadsheets can't easily answer
'what needs my attention first?' LeaseGuard imports a lease portfolio, scores every lease against
explainable rules, and turns that into a prioritized action list."

## 2. Import the demo portfolio (1 minute)

1. Open **Import Data**.
2. Click **Preview Bundled Demo Data** - point out this uses `data/demo/lease_portfolio.csv`
   through the exact same validation path a real upload would use.
3. Show the preview: 30 total rows, 30 New, 0 Errors. "Nothing is saved yet - this is a dry run."
4. Click **Confirm Import**. You land on the dashboard with a success banner.

## 3. Show the invalid file is rejected safely (1 minute)

1. Go back to **Import Data**, upload `data/demo/lease_portfolio_invalid.csv`.
2. Point out the per-row, per-field errors: end-before-start dates, negative rent, leased
   square footage exceeding the property total, an invalid enum value, a duplicate lease ID
   within the file, a missing required field.
3. There is no **Confirm Import** button - the batch cannot be committed. Note that the dashboard
   still shows exactly 30 leases: nothing partial was written.

## 4. Reimport to show idempotency (30 seconds)

1. Preview the bundled demo data again.
2. Point out: 30 Unchanged, 0 New, 0 Changed. "Reimporting the same file never creates
   duplicates - it's keyed on `lease_external_id`."

## 5. Walk the dashboard (1 minute)

1. Point out the KPI row: total annual base rent, annual rent at risk, overdue renewal notices,
   unassigned leases, and the 90/180/365-day expiration counts.
2. Point out the risk-level donut chart and the "Highest-Risk Leases" table.
3. Click into a HIGH-risk lease.

## 6. Lease detail and explainability (1 minute)

1. On the lease detail page, show the risk score and the **Why this score?** panel - every point
   contribution has a plain-language reason (e.g., "Lease expired and is not marked renewed: +50").
2. Assign a manager, then update the status with a note. Point out the action history updates
   immediately below, newest first.

## 7. Optimistic locking (30 seconds, optional deeper dive)

1. Open the same lease in two browser tabs.
2. Submit a status change in tab 1.
3. Submit a different change in tab 2 (still showing the old version). It's rejected with a clear
   "changed by someone else" message instead of silently overwriting tab 1's change.

## 8. Filters (30 seconds)

1. Go to **Leases**, filter by risk level = HIGH and a specific city.
2. Point out pagination and that filters compose (property + status + expiration window +
   manager + risk level).

## Wrap-up talking points

- Every score is explainable and every rule is unit-tested at its exact day boundary.
- Import is atomic and idempotent; nothing partial or duplicated ever lands in the database.
- Restarting the containers preserves data; `docker compose down -v` is the explicit reset.
