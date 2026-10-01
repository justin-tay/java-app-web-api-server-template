# Requirements: Account Lifecycle and Periodic Account Review

## Overview

Local user accounts that are no longer used must not stay usable indefinitely, and
a person other than the account's administrators must periodically confirm that
each account is still needed. The application suspends accounts that have been
inactive for a configurable time, removes them after a longer time, and asks
account reviewers to verify the whole account population once per configurable
review window. Every change to users, groups, roles, settings and review
decisions is recorded in a business audit trail that can be read in the
application.

This specification extends [Local User Authorisation](../user-authorisation/requirements.md).
Where the two differ, this one governs: a user's `enabled` flag is replaced by a
lifecycle status, and deleting a user requires a reason. It is also the contract
for the front end, which is built separately; see [Screens](#screens).

## Terminology

- **Account:** A local user record.
- **Lifecycle status:** `active` or `suspended`. A suspended account cannot sign in.
- **Never signed in:** An active account with no `lastLoginAt`. The administration
  list selects it with the `neverSignedIn` filter, which combines with `status`
  (ADR 0033). It is not a status, and is unrelated to the review status
  `pending_verification`.
- **Inactivity clock:** The later of `lastLoginAt` and `inactivityClockStartedAt`,
  the time the account was created or last unsuspended. `lastLoginAt` is never set
  by anything other than a sign-in.
- **Removal:** Permanent deletion of the account row, its group memberships and its
  passkeys. Nothing is kept except the audit trail and review records.
- **Review window:** A calendar-aligned period of N months, where N is the
  `review.intervalMonths` setting.
- **Review task:** The obligation to review every account once in a review window.
- **Review item:** One account's entry in a review task.
- **Review status:** `pending_verification`, `verified` or `removed`.
- **Account reviewer:** A user holding the `ACCOUNT_REVIEWER` role.
- **Settings administrator:** A user holding the `SETTINGS_MANAGE` role.

## User roles

- **Administrator:** Manages users, groups and roles (`USER_MANAGE`, `GROUP_MANAGE`,
  `ROLE_MANAGE`), as before.
- **Account reviewer:** Reviews accounts, and may suspend, unsuspend and remove them
  from within a review task. Reads the audit trail.
- **Settings administrator:** Reads and changes the application settings.
- **System:** The scheduled jobs. It is recorded as the actor `system`.

## Requirements

### R1: Account lifecycle

**User story:** As an administrator, I want accounts to be active or suspended, and
removed with a recorded reason, so that the state and history of every account is
clear.

1. WHEN an account is created, THEN the system SHALL give it the status `active`
   and set `inactivityClockStartedAt` to the creation time.
2. WHEN an administrator suspends an account with a reason code, THEN the system
   SHALL set its status to `suspended`, record the suspension time, reason code and
   optional note, and end the account's sessions.
3. WHEN an administrator unsuspends an account, THEN the system SHALL set its status
   to `active`, clear the suspension data, set `inactivityClockStartedAt` to the
   unsuspension time, and leave `lastLoginAt` unchanged.
4. WHEN a suspended account's user attempts to sign in, THEN the system SHALL deny
   access as it does for any account that cannot be used.
5. WHEN an administrator removes an account with a reason code, THEN the system
   SHALL, in one transaction, delete the account, its group memberships and its
   passkeys, end its sessions, and record the removal in the audit trail with the
   reason code, optional note, username, name and the groups and roles the account
   held. The system SHALL NOT modify the Keycloak account.
6. WHEN a suspension or removal request has no reason code, or a code outside the
   fixed set, or a note longer than 200 characters, THEN the system SHALL reject it
   as invalid.
7. WHEN an administrator changes their own status or removes their own account,
   THEN the system SHALL reject it as in ADR 0022.
8. WHEN the reason codes are listed, THEN they SHALL be `inactive_account`,
   `left_organisation`, `no_longer_required`, `policy_violation` and `other`.
   `inactive_account` is reserved for the system, so the API does not accept it.

### R2: Automated inactivity handling

**User story:** As an operator, I want unused accounts to be suspended and later
removed automatically, so that stale accounts do not remain usable.

1. WHEN `inactivity.enabled` is true and an active account's inactivity clock is
   older than `inactivity.suspendAfterDays`, THEN the system SHALL suspend it with
   reason `inactive_account`, actor `system`, and end its sessions.
2. WHEN `inactivity.enabled` is true and any account's inactivity clock is older
   than `inactivity.removeAfterDays`, THEN the system SHALL remove it with reason
   `inactive_account` and actor `system`. This applies to a suspended account
   whether it was suspended by the system or by a person.
3. WHEN the system removes an account that has an open review item, THEN it SHALL
   in the same transaction mark the item `removed` with actor `system`.
4. WHEN the jobs run on several instances at once, THEN each account SHALL be
   handled once and no instance SHALL fail because another instance acted first.
5. WHEN the check runs, THEN it SHALL run at most every `commons.accounts.inactivity.check-interval`
   (default one hour), a deployment property because it tunes the job, not policy.
6. WHEN the system removes or suspends the last enabled holder of `USER_MANAGE`,
   THEN it SHALL still do so. The risk is documented and the recovery is the
   bootstrap changeset (see Constraints).

### R3: Application settings

**User story:** As a settings administrator, I want to configure the thresholds and
the review window in the application, so that policy changes need no deployment.

1. WHEN settings are read, THEN the system SHALL return `inactivity.enabled`,
   `inactivity.suspendAfterDays`, `inactivity.removeAfterDays`, `review.enabled`
   and `review.intervalMonths`.
2. WHEN a migration runs in any environment, THEN it SHALL seed `inactivity.enabled`
   true, `suspendAfterDays` 90, `removeAfterDays` 180, `review.enabled` true and
   `review.intervalMonths` 3.
3. WHEN settings are updated, THEN the system SHALL require both day counts to be
   positive, `removeAfterDays` to be greater than `suspendAfterDays`, and
   `intervalMonths` to be 1 to 12, otherwise reject the request as invalid.
4. WHEN settings are updated, THEN the system SHALL record who changed them, and
   the old and new values, in the audit trail.
5. WHEN the `dev` context is applied, THEN the system SHALL set both
   `inactivity.enabled` and `review.enabled` to false, so development and test
   data is not suspended, removed or put into a review unless a test enables it.
6. WHEN a caller lacks `SETTINGS_MANAGE`, THEN the system SHALL deny reading and
   changing settings.

### R4: Business audit trail

**User story:** As an account reviewer or administrator, I want to see who changed
which user, group, role, setting or review in the application, so that changes can
be questioned without access to the logs.

1. WHEN a user, group or role is created, updated or deleted, a user is suspended,
   unsuspended or removed, a user's groups change, a setting changes, or a review
   decision is made, THEN the system SHALL append an audit event in the same
   transaction as the change.
2. WHEN an audit event is written, THEN it SHALL record the time, actor, action,
   target type (`user`, `group`, `role`, `setting` or `review`), target ID, target
   name, reason code and note where applicable, and a details object holding the
   changed fields and the access added and removed. It SHALL NOT hold email
   addresses.
3. WHEN an account is removed, THEN its event SHALL keep the username and name so a
   reviewer can recognise the account, and the application SHALL keep no foreign
   key from audit events to accounts.
4. WHEN the existing ECS log events are written, THEN they SHALL continue unchanged.
   The table is the in-application record and the logs stay the tamper-resistant
   one (ADR 0021).
5. WHEN an account reviewer or a `USER_MANAGE` holder lists audit events, THEN the
   system SHALL support filters for actor, target type, target name, action and a
   date range, sort by time (newest first by default), and paginate as in ADR 0027.
6. WHEN a client attempts to change or delete an audit event, THEN the system SHALL
   offer no way to do so.

### R5: Review tasks

**User story:** As an account reviewer, I want a task to appear in each review
window, so that the review happens regularly.

1. WHEN `review.enabled` is true and no task of type `account_review` exists for the
   current review window, THEN the system SHALL create one with status `open`,
   `startDate` the first day of the window and `dueDate` its last day.
2. WHEN review windows are computed, THEN the system SHALL align them to the
   calendar in the application time zone: the window index is
   `floor((year * 12 + month - 1) / N)`, so N=3 gives January to March, April to
   June and so on.
3. WHEN a task is created, THEN the system SHALL create one review item for every
   account that exists at that moment, with review status `pending_verification`.
4. WHEN an account is created after the task, THEN it SHALL NOT be added to it; the
   next window's task includes it.
5. WHEN a task is still open at the start of the next window, THEN the system SHALL
   NOT create a new task and SHALL log a warning; the open task is overdue.
6. WHEN the last item of a task leaves `pending_verification`, THEN the system SHALL
   set the task to `completed` and record the completion time and the user who
   made the last decision.
7. WHEN `review.intervalMonths` changes mid-window, THEN the open task SHALL keep
   its dates and the new value SHALL apply from the next window.
8. WHEN two instances try to create the same window's task, THEN exactly one SHALL
   succeed and the other SHALL do nothing.
9. WHEN the first task is created after the feature is enabled, THEN it SHALL cover
   the current window and can therefore be due within days.

### R6: Review items and categories

**User story:** As an account reviewer, I want to see every account in the review
grouped as active, suspended and removed, so that I can judge each one.

1. WHEN a reviewer opens a task, THEN the system SHALL show each category with live
   account data, not data frozen when the task was created.
2. WHEN the active category is shown, THEN each row SHALL show the username, name
   and last login time.
3. WHEN the suspended category is shown, THEN each row SHALL show the username, name,
   suspension time and suspension reason (code and note).
4. WHEN the removed category is shown, THEN each row SHALL show the username, name,
   removal time and removal reason (code and note), taken from the removal's audit
   event, and SHALL list every account removed during the task's window, whoever
   removed it. These rows are read-only.
5. WHEN a reviewer decides an item, THEN the system SHALL freeze on the item the
   username, name, status, last login time, suspension time and reason as they were
   at that moment, plus the deciding user and time.
6. WHEN an item's account is later removed by anyone, THEN the frozen decision data
   SHALL remain.
7. WHEN the items of a task are listed, THEN the system SHALL support filters for
   category, review status and a text search over username and name, and paginate
   as in ADR 0027.

### R7: Review decisions

**User story:** As an account reviewer, I want to verify or remove one or many
accounts, and to suspend or unsuspend them, so that I can finish the review.

1. WHEN a reviewer verifies one or more `pending_verification` items, THEN the
   system SHALL set them `verified`.
2. WHEN a reviewer removes one or more `pending_verification` items with a reason
   code, THEN the system SHALL remove each account as in R1.5 and set the item
   `removed`.
3. WHEN a reviewer suspends or unsuspends an account from its item, THEN the system
   SHALL do so as in R1.2 and R1.3 and SHALL leave the item's review status
   unchanged, so the reviewer still has to verify or remove it.
4. WHEN a reviewer verifies an item whose account is suspended, THEN the system
   SHALL accept it; a suspended account can be verified as correctly suspended.
5. WHEN a request contains several items, THEN the system SHALL apply all or none:
   if any item is already decided, belongs to another task, or is the reviewer's own
   account, THEN it SHALL reject the whole request with the offending item IDs.
6. WHEN a decision is made, THEN the system SHALL append an audit event with the
   task, item, decision, actor and reason.
7. WHEN a task is `completed`, THEN the system SHALL reject further decisions on it
   except suspending and unsuspending, which are not review decisions.

### R8: Segregation of duties and authorisation

**User story:** As an auditor, I want reviewers kept apart from what they review.

1. WHEN a reviewer attempts to verify, remove, suspend, unsuspend or otherwise act
   on the item for their own account, THEN the system SHALL reject it with `403`
   and record it as a failure. The item SHALL be flagged `ownAccount` so a client
   can disable its actions.
2. WHEN the only account reviewer's own item remains `pending_verification`, THEN
   the task SHALL stay open and become overdue; no bypass exists.
3. WHEN a caller lacks `ACCOUNT_REVIEWER`, THEN the system SHALL deny the review
   endpoints, and reading the audit trail needs `ACCOUNT_REVIEWER` or `USER_MANAGE`.
4. WHEN a state-changing request is made to the review or settings endpoints, THEN
   the system SHALL require a recent sign-in as in ADR 0023.
5. WHEN a role or group is given `SETTINGS_MANAGE`, THEN ADR 0022 SHALL apply: the
   actor must hold the role it grants.
6. WHEN an administrator holding `USER_MANAGE` adds a user to a group that grants
   `ACCOUNT_REVIEWER`, or removes one, THEN the system SHALL allow it without the
   administrator holding `ACCOUNT_REVIEWER`, SHALL still reject an administrator
   changing their own groups, and SHALL record the change in the audit trail with the
   roles added or removed.

### R9: Dashboard and task summary

**User story:** As an account reviewer, I want to see my review tasks and whether
one is due, so that I do not miss one.

1. WHEN a caller lists tasks, THEN the system SHALL return for each its type,
   status (`open` or `completed`), start date, due date, completion time, who
   completed it, whether it is overdue, and the counts of items by review status.
2. WHEN tasks are listed, THEN the system SHALL support filters for type and status,
   sort by start date (newest first by default), and paginate as in ADR 0027.
3. WHEN a client asks for the task summary, THEN the system SHALL return the number
   of open tasks, the earliest due date among them, and the number overdue, so it
   can show a badge or banner without a notification feature.
4. WHEN a reviewer selects a task, THEN the system SHALL return its items as in R6.
5. WHEN task types other than `account_review` are added, THEN the dashboard and
   summary SHALL list them without change.

### R10: Seed data and migration

**User story:** As a developer, I want realistic named fixtures, and as an operator
I want an upgrade that does not delete anyone.

1. WHEN the schema migrates an existing database, THEN the system SHALL set every
   existing account's `inactivityClockStartedAt` to the migration time, map
   `enabled` true to `active` and `enabled` false to `suspended` with reason
   `other`, and so give every account a full threshold after the upgrade.
2. WHEN reference data is applied in any environment, THEN the system SHALL create
   the `ACCOUNT_REVIEWER` and `SETTINGS_MANAGE` roles, the `Account Reviewers`
   group holding `ACCOUNT_REVIEWER`, the settings defaults in R3.2, and give the
   `Administrators` group `SETTINGS_MANAGE`.
3. WHEN the `dev` context is applied, THEN the system SHALL create the groups
   `Administrators` (no change), `Users` (replacing `Test Users`) and `Account
   Reviewers`, and the users in the table below, each with a name, email, and the
   username that Keycloak holds.
4. WHEN Keycloak test identities are provisioned, THEN the seed script SHALL create
   the same users with the same names and emails, first name and last name set so
   the identity provider does not ask for profile completion, and password
   `password`.

| Username | Name | Groups |
|---|---|---|
| `admin` | Alan Tan | Administrators |
| `user` | Mary Goh | Users |
| `multi-group-user` | Grace Lee | Administrators, Users |
| `account-reviewer-1` | Rachel Lim | Account Reviewers |
| `account-reviewer-2` | Ravi Nair | Account Reviewers |

`test-user` is renamed `user`.

## Screens

The front end is built separately. Each screen below lists what it needs and which
rules it must reflect; the REST contract is in [design.md](design.md#rest-api).

**Dashboard.** A table of tasks (R9.1) with filters for type and status and a
status badge that shows `open`, `overdue` or `completed`. A badge in the page
chrome uses the task summary (R9.3). Selecting a row opens the task.

**Task detail.** A tab per category (active, suspended, removed) over the task's
items (R6). Active and suspended rows have a checkbox, a review-status chip and the
actions Verify, Remove, Suspend or Unsuspend as applicable. Verify and Remove apply
to the selected rows. Remove and Suspend ask for a reason code and an optional
note. A row with `ownAccount` has its actions disabled with an explanation. A
removed row has no actions. A completed task shows read-only.

**Settings.** A form for R3.1 with the validation in R3.3.

**Audit trail.** A filterable table (R4.5) with a details panel.

Every list screen shows loading, empty and error states, and every state-changing
call can answer `401 reauthentication-required` (ADR 0023), after which the client
sends the user to sign in again and retries.

## Constraints and edge cases

- Removal is permanent. The audit trail and the frozen review item are the only
  record afterwards. The audit trail holds a removed account's username and name
  for as long as audit events are kept; setting that retention is a deployment
  decision.
- Nothing keeps at least one administrator. If the inactivity job removes the last
  holder of `USER_MANAGE`, the recovery is the first-administrator bootstrap
  changeset described in the authorisation document. ADR 0022 already accepts the
  same risk for administrators who remove each other.
- A user created before login has no `lastLoginAt`, so their clock is their
  creation time; a user who has never signed in is removed after the same period.
- Unsuspending restarts the clock without altering `lastLoginAt`. An account that
  is unsuspended and never used is suspended again after `suspendAfterDays`.
- Direct database access can bypass every rule here; the audit table is not
  tamper-proof against the application's own database account.
- Out of scope: email or push notification, approval workflows, restoring a removed
  account, physical purging or retention of audit events, task types other than the
  account review, and generated OpenAPI clients.
