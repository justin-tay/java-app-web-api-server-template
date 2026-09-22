# System Design Document

The template's system design document, sharded into one chapter per file so
both humans and AI agents can load only the chapter they need. Chapters are
numeric-prefixed so the reading order is visible directly in a file listing,
without relying on this index. The chapter structure follows the arc42
template (docs.arc42.org).

Individual chapter files are the source of truth; there is no single-file or
PDF master to keep in sync (see [Risks and Technical Debt](11-risks-and-technical-debt.md)
for the deferred export-pipeline item).

| Chapter | Content | Status | Last Updated |
| --- | --- | --- | --- |
| [01. Introduction & Goals](01-introduction.md) | Purpose of the template, requirements overview, quality goals, stakeholders. | Draft | 2026-09-22 |
| [02. Constraints](02-constraints.md) | Technical, organizational, and process conditions the design must work within. | Draft | 2026-09-22 |
| [03. Context and Scope](03-context-and-scope.md) | External actors and systems the application integrates with. | Draft | 2026-09-22 |
| [04. Solution Strategy](04-solution-strategy.md) | Why the template's most consequential decisions reinforce each other. | Draft | 2026-09-22 |
| [05. Building Block View](05-building-block-view.md) | Codebase decomposition, domain model, and the security filter chain. | Draft | 2026-09-22 |
| [06. Runtime View](06-runtime-view.md) | How a request moves through the system: login, authority refresh, logout, error handling. | Draft | 2026-09-22 |
| [07. Deployment View](07-deployment-view.md) | How the application is configured, run, and operated. | Draft | 2026-09-22 |
| [08. Crosscutting Concepts](08-crosscutting-concepts/README.md) | Concerns that cut across components: [logging and observability](08-crosscutting-concepts/06-logging-and-monitoring/README.md) and [security](08-crosscutting-concepts/02-security-and-authentication/README.md). | Thorough | (unchanged) |
| [09. Architecture Decisions](09-architecture-decisions.md) | Summary of the most consequential architecture decisions, linking to the full ADRs. | Draft | 2026-09-22 |
| [10. Quality Requirements](10-quality-requirements.md) | Non-functional requirements and how the template addresses them. | Draft | 2026-09-22 |
| [11. Risks and Technical Debt](11-risks-and-technical-debt.md) | Identified risks and technical debt items. | Draft | 2026-09-22 |
| [12. Glossary](12-glossary.md) | Domain and technical terms this document uses. | Draft | 2026-09-22 |
| [13. References](13-references.md) | Links to architecture decisions, specifications, and retained standards outside this document. | Draft | 2026-09-22 |

**Status key:** Stub (template only) | Draft (pre-filled from codebase analysis, needs review) | Thorough (mature, hand-authored) | Adequate | Exemplary

**Profile:** Thorough (all chapters generated). Sections marked
`<!-- arc42-manual: ... -->` in each chapter need human input the codebase
analysis could not supply; see the per-chapter placeholders, summarized in
the generation report for this initialization.

Chapter 08 was not regenerated: it already holds mature, hand-authored
security and logging documentation and is out of scope for this tooling.
