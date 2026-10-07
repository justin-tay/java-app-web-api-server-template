# ADR 0040: A generic audit trail in its own commons module

## Status

Accepted. Partly supersedes [ADR 0030](0030-business-audit-trail-table.md): the table, the
logger and what is recorded. Extends [ADR 0021](0021-authorisation-change-audit-log-events.md):
review and settings events are now logged too. Changes R4 of the
[account review requirements](../specifications/account-review/requirements.md): a refused
review action is recorded under the action it attempted.

## Context

[ADR 0030](0030-business-audit-trail-table.md) added the `account_audit_event` table beside the
ECS log events of [ADR 0021](0021-authorisation-change-audit-log-events.md), both written by
`AccountAuditLogger` in `commons-accounts`. Using it showed four problems.

* **Refused review actions are lost.** The review saves its `review_rejected` row in the
  caller's transaction and then throws `AccessDeniedException`, which rolls the row back. A
  test passes only because `@DataJpaTest` runs it inside an outer transaction.
* **Each caller must know which output a method feeds.** The user and role methods write a row
  and a log event, `record(...)` writes only a row, and the `*Rejected` methods write only a
  log event. So review and settings changes are never logged, and refused administration
  changes never reach the table.
* **The review reads the table's JSON back.** The removed population parses the `privileged`,
  `department` and `createdAt` keys of a removal's details, and the review looks up suspension
  events by action name. Those keys are a schema nothing declares; renaming one empties a
  review column without a failing compile.
* **The trail is not about accounts.** It already holds settings and review targets, and an
  adopter's own features need the same trail, but it lives in `commons-accounts` and is named
  after accounts.

## Decision

* **Module.** A new optional commons module, `commons-audit` (package
  `com.example.commons.audit`), holds the audit trail. `commons-accounts` depends on it. It
  depends on `commons` and on nothing in accounts; target types are free strings. The name
  avoids "auditing", which in Spring means the `@CreatedBy` columns.
* **One entry point.** A concrete `AuditTrail` bean with four methods: `record` for a change
  that was made, `reject` for one a business rule refused, `find` for exact reads and `search`
  for the paginated listing. It is replaced through `@ConditionalOnMissingBean`, not through a
  Java interface, because only one adapter is real.
* **Actions are constants.** Each audit action is an `AuditAction` constant that fixes its name,
  ECS `event.category` and `event.type`, and log message, so no call site chooses them. Account
  and review actions are `iam`; settings actions are `configuration` with type `change`.
* **Every event is a row and a log event.** `record` writes the row in the caller's transaction
  and logs at `INFO` after commit, so a rolled-back change leaves neither. `reject` writes the
  row in its own transaction, which survives the caller's rollback, and logs at `WARN` at once;
  it returns the caller's exception to throw, so the record and the throw are one statement.
  The log event carries the row's public ID and time as `event.id` and `event.created`.
* **Refusals.** The table gains an `outcome` column, `success` or `failure`. A refusal is
  recorded under the action it attempted with outcome `failure` and its reason, as the log
  events already do, so `review_rejected` goes. If the refusal's row cannot be written, the
  error is logged and the caller's 403 or 409 still returns, because nothing changed and the
  log event is already written. A failed row for a change propagates and rolls the change back.
* **Two payloads.** A call passes `details` for the row and `log` fields for the log event.
  The target's full name, the note and the details never reach the log. `log` refuses the keys
  the module owns (`event.*`, `user.name`, `related.user`) and a short list of personal-data
  keys.
* **Typed details and reads.** Each action's details are a Java record, read back with
  `details(Type.class)`, so the record is the contract between writer and reader. `find`
  returns successful events unless asked otherwise; `search` returns every outcome.
* **Account vocabulary.** The before-and-after diffs, the `user.target.*` and `role.*` log
  fields and the typed reads the review needs (removals since a date, suspensions of given
  accounts, removal reasons) stay in `commons-accounts`, which owns their details records. The
  `/admin/audit-events` listing stays there too, behind `audit:read`.
* **Schema.** The table is `audit_event` and its entity `AuditTrailEvent`, avoiding Spring Boot
  Actuator's `AuditEvent`. `AccountReviewItem` refers to a removal's event by its public ID.
  `revoke_sessions` gains a row like every other event. The log-only mode without a table goes.
  The schema is edited in place and development databases are recreated, as in
  [ADR 0037](0037-account-review-populations-and-stored-report.md); no data is migrated.

Rejected alternatives:

* **Actuator's `AuditEventRepository`.** Its principal, type and data map lose the target,
  outcome and reason columns, and defining one switches on Spring Security's authentication
  audit listeners, which would fill the trail with sign-ins that
  [ADR 0008](0008-session-lifecycle-audit-identifiers.md) already logs.
* **An `@Audit` annotation or published Spring events.** An annotation cannot capture the state
  before a change or a reason chosen inside the method; an event returns no row ID and loses
  the record silently when no listener is registered.
* **Ports for the store and the log.** JPA is the only store, and SLF4J is already the seam for
  the log. When a second consumer appears, such as a SIEM forwarder or an outbox, publishing a
  recorded-event Spring event is the seam to add.
* **The trail in `commons`, or as a package of `commons-accounts`.** The first gives every
  backend a table it may not use; the second keeps an adopter's own features from using the
  trail without accounts.

## Consequences

A refused action is always recorded in the table, and every row has a log event that names it,
so the in-application trail can be checked against the logs. Review decisions, completion,
report downloads and settings changes now appear in the logs, which the event reference
documents. Renaming a detail key is a compile error instead of an empty review column.

A refusal briefly holds a second database connection for its own transaction. A caller must
still choose `record` or `reject`, and must keep personal data out of the `log` fields, which
the deny list checks only by key name.

Clients of `/admin/audit-events` see the new `outcome` field and the attempted action in place
of `review_rejected`. Logger configuration that named `AccountAuditLogger` must name
`com.example.commons.audit.AuditTrail`.
