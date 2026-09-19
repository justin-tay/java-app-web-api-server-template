# ADR 0003: "Control implementation" terminology for standard-to-status mappings

## Status

Accepted

## Context

The documents that map an external standard's requirements to this
template's implementation status had no single, consistent name for that
concept.

## Decision

Documents and sections that map an external standard's requirements to this
template's implementation status are called **control implementation**,
recorded as the project's canonical vocabulary for the concept.

The name is borrowed from OSCAL (NIST's Open Security Controls Assessment
Language): its Component Definition model uses "control implementation" for
exactly this concept, a component describing how it satisfies a control
catalog's requirements. This template is architecturally closer to an OSCAL
"component" (a reusable unit meant to be forked into other systems) than to a
full OSCAL System Security Plan (SSP), which would need system-specific
content this template cannot supply, such as boundaries, users, and points
of contact. Adopting the term is not adopting OSCAL's JSON/XML format; these
documents remain Markdown.

## Consequences

A document entirely devoted to one standard's mapping keeps a plain topic
title and states in its first paragraph that it *is* the control
implementation; a document where the mapping is only one part of a larger
narrative carries an explicit control implementation section heading
instead. A future OSCAL migration, if ever undertaken, would still need its
own ADR; this decision only fixes vocabulary, not file format.
