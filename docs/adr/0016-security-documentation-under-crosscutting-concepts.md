# ADR 0016: Security documentation under Crosscutting Concepts

## Status

Accepted

## Context

`docs/system-design/06-security/` mixed two different kinds of content in
the same files: architecture and runtime narrative (the OIDC login
sequence, the session design, the group-role-authority model) alongside
security assessment (OWASP/CIS control-implementation tables). Separating
those required deciding where the assessment content, and the documents
built entirely around it, should live once the narrative parts moved into
their own architectural chapters.

Two placements were considered:

| Alternative | What it means | Why not chosen |
| --- | --- | --- |
| Security stays its own top-level chapter | Unchanged from the prior structure: a numbered chapter alongside Building Block View, Runtime View, etc. | Every document in this chapter (`authentication.md`, `sessions.md`, `headers.md`, `hardening.md`, `asvs.md`) is a control implementation mapping an external standard to implementation status, not an architectural view describing how the system is built or behaves. Giving it its own top-level chapter treats a cross-cutting concern as if it were a structural or behavioral view like the others. |
| Nest security documents under Crosscutting Concepts | Security becomes a subfolder alongside the existing logging subfolder, both under one Crosscutting Concepts chapter. | Chosen. |

## Decision

Security documentation is a subsection of Crosscutting Concepts, not its
own top-level chapter, because security controls are genuinely cross-cutting
rather than a distinct architectural view: they apply across every
component and layer rather than describing one building block or one
runtime scenario. This mirrors how logging and observability are already
documented in the same chapter for the same reason.

The architectural and runtime content that used to be blended into the
security documents (the OIDC sequence diagram, the request-handling
sequence, the local user/group/role data model) moves out to Runtime View
and Building Block View respectively. What remains under security is
exclusively control-implementation content: standard-to-implementation
status mappings and their required production/deployment decisions.

## Consequences

A future document that maps an external standard or control catalog to
this template's implementation status belongs under Crosscutting Concepts,
next to the existing security and logging documents, not as its own
top-level chapter. A future document that instead describes how a part of
the system is structured or behaves belongs in Building Block View or
Runtime View, even if it happens to be security-relevant, the same way the
OIDC sequence diagram and the authorization data model were moved out of
the security documents by this change.
