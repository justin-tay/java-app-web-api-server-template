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
| [02. System Context](02-system-context.md) | External actors and systems the application integrates with. |
| [03. Architecture Overview](03-architecture-overview.md) | Technology stack and how the codebase is organized into components. |
| [04. Data Model](04-data-model.md) | Persisted entities and how schema is managed. |
| [05. Crosscutting Concepts](05-crosscutting-concepts/) | Concerns that cut across components rather than belonging to one: currently [logging and observability](05-crosscutting-concepts/logging/README.md). |
| [06. Security](06-security/README.md) | Security design, implementation posture, verification guidance, and event contracts. |
| [07. Deployment & Operations](07-deployment-operations.md) | How the application is configured, run, and operated. |
| [08. Quality Requirements](08-quality-requirements.md) | Non-functional requirements and how the template addresses them. |
| [09. Architecture Decisions](09-architecture-decisions.md) | Summary of the most consequential architecture decisions, linking to the full ADRs. |
| [10. References](10-references.md) | Links to architecture decisions, specifications, and retained standards outside this document. |
