# ADR 0013: ADR template

**Status:** Accepted

## Decision

ADRs in this repository follow Michael Nygard's original five-part template
(Title, Status, Context, Decision, Consequences:
https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions),
adapted to this specific shape:

```
# ADR NNNN: Title

**Status:** Accepted

## Decision

...

## Context

...

## Consequences

...
```

- **Title** is the `#` heading itself (`ADR NNNN: Title`), not a separate
  section.
- **Status** is a bold line directly under the title, not a `##` heading.
  `Accepted` is the only status used to date; a superseding ADR should say so
  explicitly in its own Context and update the superseded ADR to note it, per
  [docs/adr/README.md](README.md).
- **Decision** comes before **Context**, reversing Nygard's own ordering.
  This repo consistently leads with the decision, so a reader scanning for
  the outcome gets it immediately and can treat Context as supporting detail
  to read only if needed.
- **Consequences** is last, matching Nygard's template.
- An optional **Delivery contract** section may appear between Context and
  Consequences, only when a decision imposes a concrete, checkable rollout or
  delivery process that isn't itself a consequence (used by
  [ADR 0001](0001-database-schema-management.md), which specifies what a CI
  migration job must do). Most ADRs will not need it.

For guidance on writing a good ADR beyond this shape (keeping each ADR to one
decision, writing timestamped and immutable records, what makes a good
Context or Consequences section), see
[architecture-decision-record/architecture-decision-record](https://github.com/architecture-decision-record/architecture-decision-record),
which collects Nygard's and several other ADR templates along with practical
suggestions for writing them well.

## Context

All 12 ADRs already in this repository (0001-0012) independently converged
on this same shape without it ever being written down; a contributor, human
or AI, had no reference for the expected format beyond inferring it from
precedent. An editorial review of the existing docs surfaced this gap.

Separately, `mattpocock-skills`' `domain-modeling` skill defaults to a much
lighter ADR template (a single 1-3 sentence paragraph, with Status,
Considered Options, and Consequences all optional). That lighter template is
reasonable for its own intended use, but adopting it here would break with
this repository's own established, more thorough convention rather than
build on it. This ADR exists so the deliberate choice to keep this
repository's own format is itself recorded, not just assumed.

## Consequences

New ADRs should follow this template rather than a lighter one. Tooling or
skills that write ADRs into this repository (including `domain-modeling`)
should use this shape, not their own defaults.
