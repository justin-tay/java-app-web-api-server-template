# 1. Introduction & Goals

## Purpose

This is an opinionated template for a Java web API server that services
browser clients. It exists to give a new project a working, defensible
starting point for authentication, authorization, sessions, logging, and API
error handling, rather than have every project rediscover the same decisions.
It is a template to be forked and adapted, not a finished product: several
decisions are deliberately left to the adopting deployment (see
[Deployment View](07-deployment-view.md) and the "Deployment
decision required" rows throughout [Security](08-crosscutting-concepts/security/README.md)).

## Audience

- Developers adopting or extending this template.
- AI coding agents operating on this codebase, which can load individual
  chapters instead of the whole document.
- Reviewers assessing the template's security and architecture posture.

## Scope

This document describes the template's current-state architecture: what
exists and why. It does not restate content already recorded elsewhere:

- **Why** a specific technical decision was made belongs in
  [docs/adr/](../adr/README.md); this document links to individual ADRs
  rather than duplicating their rationale (see
  [Architecture Decisions](09-architecture-decisions.md)).
- Feature-level requirements and task breakdowns belong in
  [docs/specifications/](../specifications/); this document describes the
  system as a whole, not one feature.
- External standards this project is evaluated against belong in
  [docs/standards/](../standards/); this document describes how the system
  meets them, not the standards' own text.
