# ADR 0002: System design document under docs/system-design

## Status

Accepted

## Context

The chapter structure borrows from arc42 (docs.arc42.org), the most concrete,
freely available, section-by-section template at application scale. Other
candidates were considered and rejected:

| Alternative | What it is | Why not chosen |
| --- | --- | --- |
| IEEE/ISO 42010 | A conceptual meta-model (stakeholders, concerns, viewpoints) | No prescribed headings; nothing to fill out directly |
| C4 model | A diagramming notation | Not a document structure; usually paired with a template like arc42, not a replacement for one |
| Kruchten's 4+1 / RUP Software Architecture Document | A view-based template coupled to RUP, a specific development process | Some sections (e.g. Use-Case View) presuppose RUP's own process artifacts this project doesn't follow; RUP itself is largely superseded by agile practice |
| TOGAF Architecture Definition Document | An enterprise architecture framework artifact | Enterprise-wide scope, disproportionate to a single service |

arc42 itself is explicitly designed to be tailored: its own guidance sanctions
adopting only the sections a project needs rather than all twelve verbatim,
which is the approach taken here. Where a section name is borrowed directly,
it uses arc42's canonical wording verbatim (`crosscutting-concepts`, matching
arc42 section 8's actual title, "Crosscutting Concepts") rather than a
plausible-looking paraphrase.

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

## Decision

`docs/system-design/` holds the template's System Design Document, sharded
into one Markdown file per chapter with `docs/system-design/index.md` as the
table of contents. Chapters are numeric-prefixed so a plain directory
listing reflects reading order without relying on `index.md`. The shard
files are the only hand-maintained source; any future single-file "master"
document assembled from them (for example to feed an AsciiDoc/PDF export) is
a generated build artifact, never edited directly.

An AsciiDoc-based conversion to PDF is a desired future step, explicitly out
of scope for this change: no conversion tooling is introduced here.

## Consequences

A future contributor adding to the system design document should add a new
numeric-prefixed shard file and an `index.md` entry, not grow an existing
chapter past the point it stops being one topic. The deferred AsciiDoc/PDF
export remains an open, undesigned piece of work; it should get its own ADR
once a converter and pipeline are chosen.
