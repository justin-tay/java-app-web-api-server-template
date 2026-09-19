# System Design Document

This is the template's System Design Document: a description of the system
as it currently exists, sharded into one chapter per file so both humans and
AI agents can load only the chapter they need. Chapters are numeric-prefixed
so the reading order is visible directly in a file listing, without relying
on this index.

Individual chapter files are the source of truth. If a single-file or PDF
form of this document is ever generated (see chapter 9 and
[ADR 0002](../adr/0002-system-design-document.md)), it is assembled from
these files and is not itself hand-edited.

| Chapter | Content |
| --- | --- |
| [01. Introduction & Goals](01-introduction.md) | Purpose of the template, its scope, and the audience for this document. |
| [02. Constraints](02-constraints.md) | Technical, organizational, and process conditions the design must work within. |
| [03. Context and Scope](03-context-and-scope.md) | External actors and systems the application integrates with. |
| [04. Solution Strategy](04-solution-strategy.md) | Why the template's most consequential decisions reinforce each other. |
| [05. Building Block View](05-building-block-view.md) | Technology stack, how the codebase is organized into components, and the persisted data model. |
| [06. Runtime View](06-runtime-view.md) | How a request moves through the system, including the OIDC login sequence. |
| [07. Deployment View](07-deployment-view.md) | How the application is configured, run, and operated. |
| [08. Crosscutting Concepts](08-crosscutting-concepts/) | Concerns that cut across components rather than belonging to one: [logging and observability](08-crosscutting-concepts/logging/README.md) and [security](08-crosscutting-concepts/security/README.md). |
| [09. Architecture Decisions](09-architecture-decisions.md) | Summary of the most consequential architecture decisions, linking to the full ADRs. |
| [10. Quality Requirements](10-quality-requirements.md) | Non-functional requirements and how the template addresses them. |
| [11. Risks and Technical Debt](11-risks-and-technical-debt.md) | Index of open items already recorded across this document. |
| [12. Glossary](12-glossary.md) | Domain and technical terms this document uses. |
| [13. References](13-references.md) | Links to architecture decisions, specifications, and retained standards outside this document. |
