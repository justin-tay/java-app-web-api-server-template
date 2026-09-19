# ADR 0012: System design document under docs/system-design

**Status:** Accepted

## Decision

`docs/system-design/` holds the template's System Design Document, sharded
into one Markdown file per chapter with `docs/system-design/index.md` as the
table of contents. Each shard file is numeric-prefixed
(`01-introduction.md`, `02-system-context.md`, ...) so a plain directory
listing already reflects reading order, without relying on `index.md` to
establish it. The shard files are the only hand-maintained source; any future
single-file "master" document assembled from them (for example to feed an
AsciiDoc/PDF export) is a generated build artifact, never edited directly.

`docs/security/` moves to `docs/system-design/06-security/` as a nested
subfolder, keeping its existing internal structure (its own `README.md`
index and topic files) unchanged. `docs/security/logging/` moves out
separately, to `docs/system-design/05-crosscutting-concepts/logging/`, since
logging is a cross-cutting concern rather than a security-specific one. Both
directories carry the same numeric-prefix convention as the single-file
chapters, so that the ordering property above holds for the whole chapter
set, not just the files.

`docs/adr/`, `docs/specifications/`, and `docs/standards/` are unchanged and
stay outside `docs/system-design/`. The system design document references
them (an arc42-style "Architecture Decisions" chapter links to individual ADRs; a
"References" chapter links to specifications and standards) rather than
absorbing their content.

An AsciiDoc-based conversion to PDF, for a human-readable deliverable form of
the document, is a desired future step but is explicitly out of scope for
this change: no conversion tooling is introduced here.

## Context

No system-wide design document existed; the closest analogues were
`docs/adr/` (a chronological decision log, not a description of
current-state architecture) and `docs/specifications/user-authorisation/design.md` (a
single feature's design, not the system's).

The chapter structure borrows from arc42 (docs.arc42.org), the most concrete,
freely available, section-by-section template at application scale. IEEE/ISO
42010 is the formally standardized alternative, but it is a conceptual
meta-model (stakeholders, concerns, viewpoints) with no prescribed headings,
not something filled out directly. The C4 model is a diagramming notation,
not a document structure, and is commonly paired with a template like
arc42 rather than competing with it. Kruchten's 4+1/RUP Software
Architecture Document is largely historical, and TOGAF's Architecture
Definition Document targets enterprise-wide scope, disproportionate to a
single service. arc42 itself is explicitly designed to be tailored:
its own guidance sanctions adopting only the sections a project needs rather
than all twelve verbatim, which is the approach taken here. Where a section
name is borrowed directly, it uses arc42's canonical wording verbatim
(`crosscutting-concepts`, matching arc42 section 8's actual title on
docs.arc42.org, "Crosscutting Concepts") rather than a plausible-looking
paraphrase.

BMad-Method's `shard-doc` convention supplied the folder/file naming pattern
(a folder named after the document, with a generated `index.md`), but not its
source-of-truth direction. BMad hand-maintains one master file and treats the
sharded folder as a one-time, one-directional export that can silently go
stale relative to the master (BMad's own issue tracker documents this: when
both the master document and its shard folder exist, the master takes
precedence, and shards are not automatically regenerated). That is the
opposite of what this project needs: since the shards are read directly by
both humans and AI agents day to day, they must never be a second, staler
copy of some other authoritative source. Making the shard files themselves
the source of truth, and treating any single-file assembly as a disposable
build output, avoids that failure mode entirely.

`docs/security/` already existed as a coherent, well-organized unit (a
`README.md` index, topic files, and a nested `logging/` folder) before this
change. Moving it wholesale preserves that structure rather than flattening
it into an arbitrary uniform list of top-level chapters, which would have
added churn without benefit. Logging is split out to its own
`crosscutting-concepts/` chapter because it is not itself a security
control; it happened to live under `docs/security/` only because there was
previously no more suitable home for it.

## Consequences

Root `README.md`'s four deep links into `docs/security/*` collapse into a
single link to `docs/system-design/06-security/README.md`; `docs/README.md`
gains a "System design" entry ahead of the other three. Every relative link
inside the moved security files that pointed outside `docs/security/` (to
`../adr/` or `../src/`) gains one extra `../` level to account for the new
nesting depth; links to the relocated `logging/` files are rewritten to
`../05-crosscutting-concepts/logging/`. A future contributor adding to the
system design document should add a new numeric-prefixed shard file and an
`index.md` entry, not grow an existing chapter past the point it stops being
one topic. The deferred AsciiDoc/PDF export remains an open, undesigned
piece of work; it should get its own ADR once a converter and pipeline are
chosen.
