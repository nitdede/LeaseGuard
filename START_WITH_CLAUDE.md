# Initial Prompt for Claude

Read the repository-root `CLAUDE.md` completely. Then read `docs/product-brief.md`, `docs/architecture.md`, `docs/data-dictionary.md`, and both demo CSV files.

Do not write application code yet. First present:

1. Your understanding of the business problem and target user
2. The MVP and explicit non-goals
3. Proposed screens and the end-to-end demo flow
4. Domain model and database tables
5. Package structure
6. CSV preview, validation, and idempotent-import design
7. Risk-score calculation design and boundary cases
8. Implementation phases
9. Key assumptions and trade-offs
10. Likely Staff-level interview questions
11. Exact Docker Compose services, health checks, volume behavior, and developer/reviewer startup flows
12. Flyway schema-creation lifecycle and Hibernate schema-validation configuration
13. Demo-data import, reimport, persistence, and explicit reset behavior
14. Unit, integration, and web-test plan
15. README outline and code-comment/Javadoc standards

Verify current compatible versions of major dependencies against official documentation. After presenting the plan, stop and obtain my approval. Do not generate or modify application code before approval.
