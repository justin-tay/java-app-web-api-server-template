# ADR 0003: "Control implementation" terminology for standard-to-status mappings

## Status

Accepted

## Context

These documents originally used "crosswalk," inherited from common (if
imprecise) industry usage. On review, "crosswalk" has an established, more
specific meaning: it names a mapping between two different standards or
frameworks to each other (for example, NIST 800-53 to ISO/IEC 27001), not a
mapping from one standard to a system's own implementation. NIST itself
publishes such crosswalks in that stricter sense. Continuing to call these
documents "crosswalks" would use the term loosely rather than accurately,
even though the loose usage is common enough in vendor and consulting
material that it would not have been actively misleading.

The choice matters beyond precision because this project is expected to
follow Singapore's IM8 reform, whose own control catalog
(`docs/standards/im8-reform-cybersecurity-control-catalog.md`) is explicitly
OSCAL/Trestle-inspired. Adopting OSCAL's own vocabulary here, rather than a
generic compliance-industry term or a name that collides with an existing
deliverable, keeps this project's documentation vocabulary aligned with the
direction it is already committed to.

## Decision

Documents and sections that map an external standard's requirements to this
template's actual implementation status (`docs/system-design/06-security/asvs.md`,
`hardening.md`, `headers.md`, and the embedded sections in `authentication.md`,
`sessions.md`, and `crosscutting-concepts/logging/README.md`) are called
**control implementation**, not "crosswalk," "OWASP review," or
"recommendation matrix." This term is recorded in [CONTEXT.md](../../CONTEXT.md)
as the project's canonical vocabulary for the concept.

The name is deliberately borrowed from OSCAL (the NIST Open Security Controls
Assessment Language): its Component Definition model uses "control
implementation" for exactly this concept, a component describing how it
satisfies a control catalog's requirements. This template is architecturally
closer to an OSCAL "component" (a reusable unit meant to be forked into other
systems) than to a full OSCAL System Security Plan (SSP), which would need
system-specific content this template cannot supply, such as boundaries,
users, and points of contact. Adopting the term is not adopting OSCAL's
JSON/XML format; these documents remain Markdown.

"Requirements Traceability Matrix" (RTM) was considered and rejected: it
already names a separate, existing contractual deliverable for this project.
Reusing it here for a different artifact would create exactly the kind of
naming collision this project has been actively removing elsewhere.

## Consequences

`asvs.md` and `hardening.md` keep plain topic names as their document titles
("ASVS", "Hardening") rather than naming the whole document after the
mapping, consistent with every other file in `docs/system-design/06-security/`;
each document's first paragraph states that it *is* the control
implementation for its respective standard. `headers.md`,
`authentication.md`, `sessions.md`, and `logging/README.md` each carry an
explicit `## ... control implementation` (or equivalently-named, for
`headers.md`) section heading marking the mapping content specifically,
since the mapping is only part of those documents rather than the whole of
them. A future OSCAL migration, if ever undertaken, would still need its own
ADR; this decision only fixes vocabulary, not file format.
