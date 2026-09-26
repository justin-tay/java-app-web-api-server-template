# ADR 0021: Audit authorisation changes with log events, not history tables

## Status

Accepted

## Context

The administration API (`UserAdminController`, `GroupAdminController`,
`RoleAdminController`, backed by `AdministrationService`) creates, updates, and
deletes the local users, groups, and roles that every authority comes from
([ADR 0005](0005-keycloak-authentication-local-authorisation.md)). Until now no
event recorded these changes. They were visible only through the generic
request events and, when the affected user happened to have a session, a
`destroy_session` revocation. The rows recorded when they were created and last
updated, but not by whom.

Both control implementations ask for this gap to be closed with logging:

* The OWASP Logging Cheat Sheet lists "user administration actions such as
  addition or deletion of users, changes to privileges" among the events that
  must always be logged, and asks each event to record the object, the result,
  and the reason for it.
* OWASP ASVS V16.3.3 requires the security events defined in the
  documentation to be logged, and V16.4.2 and V16.4.3 require logs to be
  protected from modification and sent to a logically separate system, so
  that a breached application cannot rewrite them.

Neither asks for version history of the data itself. The alternative
considered was Hibernate Envers, which keeps a revision of every changed row
in `_AUD` tables. Under [ADR 0004](0004-database-schema-management.md) every
one of those tables, including those for the two join tables and the revision
table, would be a Liquibase changeset, roughly doubling the accounts schema.
The history would live in the database the audited application writes to,
with the runtime account able to modify it, which is the opposite of what
V16.4.3 asks for. It would also keep every past email address and
display name, including those of deleted users, indefinitely.

The one thing Envers gives that plain change events do not is the full state
at an earlier point without replaying every change since the object was
created, which matters once older logs have aged out. Only the security state
needs that: a user's access depends on their username, whether they are
enabled, their groups, and those groups' roles. The email address and display
name are read only by the administration API's responses.

## Decision

Every create, update, and delete of a user, group, or role is a defined
security event, logged by `AdministrationAuditLogger` as an ECS `iam` event
through the same structured logging as every other event
([ADR 0010](0010-ecs-structured-logging.md)). No Envers or other history table
is added.

* **Categorization.** `event.category` is `iam` and `event.type` pairs the
  object with the activity, as ECS's user field usage describes: `user` or
  `group`, or `admin` for roles, which ECS has no type for, with `creation`,
  `change`, or `deletion`. `event.action` is `create_user`, `update_group`,
  `delete_role`, and so on.
* **Self-contained state.** Following ECS's IAM convention, the actor is the
  root `user.name`, the affected user is `user.target.*` holding their state
  before the change, and `user.changes.*` holds only the values that changed.
  Groups and roles use the same shape (`group.*` and `group.changes.*`,
  `role.*` and `role.changes.*`). Any single event therefore gives the state
  before and after the change without replaying earlier events.
* **Access, in one vocabulary.** A user's effective roles are logged in the
  ECS `user.roles` field (`user.target.roles`, `user.changes.roles`), beside
  their group names. Every role value in every event is the stored role name,
  such as `USER_MANAGE`, never the `ROLE_`-prefixed Spring Security
  authority. `roles.added`, `roles.removed`, `groups.added`, and
  `groups.removed` list the access each change grants or withdraws, so one
  query finds a grant however it was made. The existing `update_session`
  event moves from `session.authorities.added` and `.removed`, which held
  prefixed authorities, to the same `roles.added` and `roles.removed`, so the
  same query also finds each session picking the change up.
* **No personal-data values.** The email address and display name are never
  logged. `user.changes.fields` names the ones that changed, using their ECS
  field names (`email`, `full_name`).
* **Blast radius.** A group change records `group.affected_user_count`, not
  the users' names, whose number is unbounded.
* **After commit.** A successful change is logged after its transaction
  commits, on the request thread, so a rolled-back change is never logged and
  the request's `http.request.id` and `trace.id` are kept. A change rejected
  by a business rule (a duplicate name, a group that still has users, a role
  still assigned to a group) is logged at once as a failure, with
  `event.reason` `username_exists`, `name_exists`, `group_has_users`, or
  `role_in_use`. Unknown IDs (404) are left to the request events.

Each user, group, and role row also records `created_by` and `updated_by`:
the name of the authenticated actor, which is the logged `user.name`, or
`system` for a change with no authenticated user and for rows seeded by
migrations. It is read from the security context directly, not through Spring
Data JPA's auditing, whose `@EnableJpaAuditing` would clash with an
application that enables it for its own entities. These columns record only
the latest change; the audit events are the history.

## Consequences

The administration API's changes are now logged with the actor, the object,
the outcome, the reason for a failure, and the access granted or withdrawn,
closing the Logging Cheat Sheet's higher-risk functionality gap and
supporting ASVS V16.3.3.

Access history reaches back only as far as the deployment's log retention.
Retention is a deployment decision and must cover the organisation's
access-review and audit period. An adopter that needs longer history, or
needs to show it inside the application, can add Envers or a history table of
its own for that reason.

Prior email addresses and display names cannot be recovered from the logs.
If an adopter makes one of them a security attribute, for example by linking
Keycloak accounts by email address, it must log that field's changes.

A user's `roles` are the roles their groups grant, whether or not the user is
enabled; `enabled` is recorded separately. Enabling a disabled user therefore
restores access without a `roles.added` entry, so a query for grants must also
match `user.changes.enabled`.

Consumers of the `update_session` event must move from
`session.authorities.added` and `.removed` to `roles.added` and
`roles.removed`, whose values no longer carry the `ROLE_` prefix.
