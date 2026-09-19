# 10. References

Material that informs this system design but is deliberately not duplicated
into it. See [Introduction](01-introduction.md#scope) for why these stay
separate.

## Architecture decisions

[docs/adr/](../adr/README.md) — the full, chronological log of durable
technical decisions. [Architecture Decisions](09-architecture-decisions.md) summarizes each one
with a link back here; read the ADR itself for context and consequences.

## Feature specifications

[docs/specifications/](../specifications/) — requirements, design, and task
breakdowns for individual features, at a finer grain than this document.
Currently: `user-authorisation/`.

## Retained external standards

[docs/standards/](../standards/) — external standards and control catalogs
kept as reference material rather than paraphrased:

- ISO/IEC 25010:2023 quality characteristics, used in
  [Quality Requirements](08-quality-requirements.md).
- Singapore's IM8 cybersecurity and digital service standard control
  catalogs, and IM8 risk/impact profiles.

## External systems

- [Keycloak](https://github.com/keycloak/keycloak) — the OIDC identity
  provider used for authentication; see
  [System Context](02-system-context.md) and
  [Authentication](06-security/authentication.md).
