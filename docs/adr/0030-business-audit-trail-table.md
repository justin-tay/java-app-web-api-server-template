# ADR 0030: Business audit trail in a table, beside the log events

## Status

Accepted

## Context

[ADR 0021](0021-authorisation-change-audit-log-events.md) audits changes to users,
groups and roles as ECS log events and rejects a history table, because the
logs are what a breached application cannot rewrite and because Envers would
roughly double the accounts schema. It notes that adopters who need history
inside the application can add a table of their own.

Account review needs that. A reviewer must see in the application who changed
which user, group, role or setting and why, and must still see an account after
it has been permanently removed. Log retention and search are a deployment matter
that an application user cannot reach.

## Decision

Add one append-only table, `account_audit_event`, written in the same transaction as
the change it records. It holds the time, actor, action, target type, ID and name,
reason code and note, and a JSON `details` of the changed fields and the access
added and removed. It has no foreign key to `app_user`, so rows outlive removed
accounts; it keeps a removed account's username and name for recognition and never
holds an email address.

`AdministrationAuditLogger` becomes `AccountAuditLogger`, because the trail covers
accounts, settings and review decisions, not administration as a whole. It keeps
emitting the ECS events of ADR 0021 unchanged and additionally appends the row. One
table and one logger serve every target type; a table per entity was rejected as
three times the wiring for the same behaviour.

The table is read through a filterable, paginated, read-only endpoint. The
repository offers no update or delete.

## Consequences

The in-application trail is a convenience copy. The runtime database account can still
alter the table, so the logs remain the tamper-resistant record and ADR 0021's reasoning
about them stands. ADR 0021 is superseded only where it says no history table is added.

Retention of the table is the adopter's decision; nothing here purges it. It stores the
name of removed accounts until it is purged.
