# Requirements: Account Lifecycle and Periodic Account Review

> **Superseded in part.** The review is now two reviews, one for the privileged accounts and one
> for the rest, each with its own interval setting, and a reviewer removes roles and never adds
> them; see [ADR 0038](../../adr/0038-role-permission-model-and-account-review-classes.md). The
> role names and the group editing below describe the earlier model.

## Overview

Local user accounts that are no longer used must not stay usable indefinitely, and
a person other than the account's administrators must periodically confirm that
each account is still needed and that the access it holds is correct. The
application suspends accounts that have been inactive for a configurable time,
removes them after a longer time, and asks account reviewers to review the account
population in a review task that opens in a fixed month of the year. A reviewer
confirms, re-groups or removes each active account, and confirms the suspended and
removed populations as evidence that the lifecycle automation works. When the work
is done, the review task completes by itself and a PDF report is stored as audit
evidence. Every change to users, groups, roles, settings and review decisions is
recorded in a business audit trail that can be read in the application.

This specification extends [Local User Authorisation](../user-authorisation/requirements.md).
Where the two differ, this one governs: a user's `enabled` flag is replaced by a
lifecycle status, and deleting a user requires a reason. It is also the contract
for the front end, which is built separately; see [Screens](#screens).

## Terminology

- **Account:** A local user record.
- **Lifecycle status:** `active` or `suspended`. A suspended account cannot sign in.
- **Never signed in:** An active account with no `lastLoginAt`. The administration
  list selects it with the `neverSignedIn` filter, which combines with `status`
  (ADR 0033). It is not a status.
- **Inactivity clock:** The later of `lastLoginAt` and `inactivityClockStartedAt`,
  the time the account was created or last unsuspended. `lastLoginAt` is never set
  by anything other than a sign-in.
- **Removal:** Permanent deletion of the account row, its group memberships and its
  passkeys. Nothing is kept except the audit trail and review records.
- **Department:** An optional free-text label on an account, such as `Finance`.
- **Groups:** The groups an account belongs to. Roles come only from groups, so the
  review shows and edits groups, and "access" in this document means group
  membership.
- **Review interval:** N months between review tasks, where N is the
  `review.intervalMonths` setting: 1, 3, 6 or 12.
- **Review month:** A calendar month in which a review task is created: a month
  whose number minus one is a multiple of N. N=3 gives January, April, July and
  October.
- **Review period:** The review month itself, from its first to its last day.
- **Review task:** The obligation to review the account population in one review
  period. A task of type `account_review`.
- **Review item:** One active or suspended account's entry in a review task, in its category.
- **Review outcome:** `pending`, `confirmed`, `confirmed_groups_edited` or `removed`.
- **Population:** The set of removed accounts since the previous review, which
  a reviewer confirms as a whole.
- **Population confirmation:** The record that a reviewer reviewed a population,
  with the list as it was at that moment.
- **Review report:** The PDF stored when a task completes.
- **Frozen:** Copied into a review record at a defined moment and never changed
  afterwards. R13 lists what is frozen and when.
- **Account reviewer:** A user holding the `ACCOUNT_REVIEWER` role.
- **Settings administrator:** A user holding the `SETTINGS_MANAGE` role.

## User roles

- **Administrator:** Manages users, groups and roles (`USER_MANAGE`, `GROUP_MANAGE`,
  `ROLE_MANAGE`), as before.
- **Account reviewer:** Reviews accounts, edits the groups of an account under
  review, removes accounts from within a review task, confirms populations,
  downloads review reports and reads the audit trail. Cannot suspend or unsuspend
  from a review task.
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
   reason code, optional note, username, name, department, last login time and the
   groups and roles the account held. The system SHALL NOT modify the Keycloak
   account.
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
3. WHEN the system removes an account that has a pending review item in an open
   task, THEN R6.8 SHALL apply in the same transaction.
4. WHEN the jobs run on several instances at once, THEN each account SHALL be
   handled once and no instance SHALL fail because another instance acted first.
5. WHEN the check runs, THEN it SHALL run at most every `commons.accounts.inactivity.check-interval`
   (default one hour), a deployment property because it tunes the job, not policy.
6. WHEN the system removes or suspends the last enabled holder of `USER_MANAGE`,
   THEN it SHALL still do so. The risk is documented and the recovery is the
   bootstrap changeset (see Constraints).

### R3: Application settings

**User story:** As a settings administrator, I want to configure the thresholds and
the review schedule in the application, so that policy changes need no deployment.

1. WHEN settings are read, THEN the system SHALL return `inactivity.enabled`,
   `inactivity.suspendAfterDays`, `inactivity.removeAfterDays`, `review.enabled`
   and `review.intervalMonths`.
2. WHEN a migration runs in any environment, THEN it SHALL seed `inactivity.enabled`
   true, `suspendAfterDays` 90, `removeAfterDays` 180, `review.enabled` true and
   `review.intervalMonths` 3.
3. WHEN settings are updated, THEN the system SHALL require both day counts to be
   positive, `removeAfterDays` to be greater than `suspendAfterDays`, and
   `intervalMonths` to be 1, 3, 6 or 12, otherwise reject the request as invalid.
4. WHEN settings are updated, THEN the system SHALL record who changed them, and
   the old and new values, in the audit trail.
5. WHEN the `dev` context is applied, THEN the system SHALL set both
   `inactivity.enabled` and `review.enabled` to false, so development and test
   data is not suspended, removed or put into a review unless a test enables it.
6. WHEN a caller lacks `SETTINGS_MANAGE`, THEN the system SHALL deny reading and
   changing settings.
7. WHEN `review.enabled` is false, THEN the system SHALL create no review task. It
   SHALL NOT affect tasks that already exist, which stay open and workable.

### R4: Business audit trail

**User story:** As an account reviewer or administrator, I want to see who changed
which user, group, role, setting or review in the application, so that changes can
be questioned without access to the logs.

1. WHEN a user, group or role is created, updated or deleted, a user is suspended,
   unsuspended or removed, a user's groups change, a setting changes, or a review
   action in R6 to R12 happens, THEN the system SHALL append an audit event in the
   same transaction as the change. The only exception is a report download, which
   changes nothing and is recorded in its own transaction.
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
7. WHEN the review actions are recorded, THEN their actions SHALL be
   `create_review_task`, `review_rejected` for an attempt on the reviewer's own account,
   `confirm_review_item`, `edit_review_item_groups`, `remove_review_item`,
   `confirm_review_population`, `complete_review_task` and `export_review_report`,
   each with target type `review`.

### R5: Review tasks

**User story:** As an account reviewer, I want a task to appear in each review
month, so that the review happens regularly.

1. WHEN `review.enabled` is true, the current month in the application time zone is
   a review month, and no task of type `account_review` has the first day of that
   month as its start date, THEN the system SHALL create one with status `open`,
   `startDate` the first day of the month and `dueDate` its last day.
2. WHEN review months are computed, THEN the system SHALL use the application time
   zone and the rule `(month - 1) mod N = 0`, so N=1 gives every month, N=3 gives
   January, April, July and October, N=6 gives January and July, and N=12 gives
   January.
3. WHEN a task is created, THEN the system SHALL create one review item for every
   account whose status is `active` or `suspended` at that moment, in the category of that status, with outcome `pending`.
4. WHEN an account is created after the task, or changes status after the task is
   created, THEN it SHALL NOT be given an item; it is reviewed in the next task that
   finds it in that status.
5. WHEN the next review month starts and the previous task is still open, THEN the
   system SHALL create the new task anyway. The previous task stays open, workable
   and overdue, and the two tasks SHALL NOT share any state.
6. WHEN `dueDate` is before today and the task is open, THEN the task SHALL be
   overdue.
7. WHEN `review.intervalMonths` changes, THEN existing tasks SHALL keep their dates
   and the new value SHALL apply from the next time the job decides whether to
   create a task.
8. WHEN two instances try to create the same month's task, THEN exactly one SHALL
   succeed and the other SHALL do nothing.
9. WHEN `review.enabled` is switched on in a month that is not a review month, THEN
   no task SHALL be created until the next review month; when it is switched on in
   a review month and no task exists for it, THEN one SHALL be created at the next
   job run and can therefore be due within days.

### R6: Review items and the active accounts

**User story:** As an account reviewer, I want to see each active account with its
department, groups and last login, so that I can judge whether it and its access
are still correct.

1. WHEN a reviewer opens a task, THEN the system SHALL show the active accounts, the
   suspended accounts and the removed population as three categories. A pending
   item shows live account data, not data frozen when the task was created.
2. WHEN the active category is shown, THEN each row SHALL show the username, name,
   department, groups, last login time, outcome, a remark and the `ownAccount` flag.
   A decided row SHALL show the frozen data in R13 instead of live data. The remark
   is derived: `No changes`, the groups added and removed, or `Account removed`
   with the reason code.
3. WHEN the items are listed, THEN the system SHALL support filters for category,
   outcome, department, group and a text search over username and name, sorting
   by username, name, department, last login time and decision time, and
   pagination as in ADR 0027.
4. WHEN the active category is shown, THEN it SHALL contain the decided items whose
   outcome is not `removed`, whatever later happens to their account, and the `pending`
   items whose account exists and is `active`. A removed item never returns to it.
5. WHEN the task is open, THEN the system SHALL report progress as the number of
   items in the active category that are not `pending`, over the number of items in
   the category.
6. WHEN a reviewer asks for the groups they may assign, THEN the system SHALL return
   only the groups whose roles the reviewer holds, as in ADR 0022.
7. WHEN a decided item's account is later changed by anyone, THEN the item and its
   frozen data SHALL NOT change.
8. WHEN an account with a `pending` item in an open task is removed by anyone other
   than through this review, THEN the system SHALL in the same transaction set the
   item to `removed`, record the remover as the decider, freeze the evidence as in
   R13, and point the item at the removal's audit event.
9. WHEN an account with a `pending` item is suspended, THEN the item SHALL stay
   `pending` and leave the active category while the account is suspended, and
   SHALL NOT count towards completion. If the account is unsuspended before the task
   completes, THEN it SHALL return to the active category.

### R7: Review decisions

**User story:** As an account reviewer, I want to confirm, re-group or remove one
or many accounts, so that I can finish the review.

1. WHEN a reviewer confirms one or more `pending` items in the active category, THEN
   the system SHALL set them `confirmed`, meaning the account is still needed and
   its groups are correct, and freeze the evidence as in R13.
2. WHEN a reviewer removes one or more `pending` items with a reason code, THEN the
   system SHALL remove each account as in R1.5 and set the item `removed`.
3. WHEN a reviewer sets the groups of one `pending` item to a different set, THEN
   the system SHALL apply the change as an administrator's group change under
   ADR 0022, record it in the audit trail with the groups added and removed, set the
   item `confirmed_groups_edited` and freeze the evidence with the groups before and
   after.
4. WHEN the groups in a request equal the account's current groups, or include a
   group the reviewer may not assign, THEN the system SHALL reject it as invalid or
   denied and change nothing.
5. WHEN a request to confirm or remove contains several items, THEN the system SHALL
   apply all or none: if any item is already decided, belongs to another task, is
   not in the active category, or is the reviewer's own account, THEN it SHALL
   reject the whole request with the offending item IDs.
6. WHEN a decision is made, THEN the system SHALL append an audit event with the
   task, item, decision, actor and reason, one per account.
7. WHEN a task is `completed`, THEN the system SHALL reject every decision on it.
8. WHEN a reviewer attempts to suspend or unsuspend an account from a task, THEN the
   system SHALL offer no way to do so.

### R8: Segregation of duties and authorisation

**User story:** As an auditor, I want reviewers kept apart from what they review.

1. WHEN a reviewer attempts to confirm, remove or edit the groups of the item for
   their own account, THEN the system SHALL reject it with `403` and record it as a
   failure. The item SHALL be flagged `ownAccount` so a client can disable its
   actions.
2. WHEN the only account reviewer's own item remains `pending`, THEN the task SHALL
   stay open and become overdue; no bypass exists.
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
7. WHEN a reviewer edits an account's groups in a task, THEN ADR 0022 SHALL apply:
   the reviewer may add only groups whose roles they hold, and the self-modification
   guard applies as in R8.1.

### R9: Dashboard and task summary

**User story:** As an account reviewer, I want to see my review tasks and whether
one is due, so that I do not miss one.

1. WHEN a caller lists tasks, THEN the system SHALL return for each its type,
   status (`open` or `completed`), start date, due date, completion time, who
   completed it, whether it is overdue, the counts of items by outcome, whether each
   population is confirmed, and whether a stored report exists.
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
   Reviewers`, and the users in the table below, each with a name, email, department
   and the username that Keycloak holds.
4. WHEN Keycloak test identities are provisioned, THEN the seed script SHALL create
   the same users with the same names and emails, first name and last name set so
   the identity provider does not ask for profile completion, and password
   `password`. The department is not held in Keycloak.
5. WHEN the module schema is changed for the review redesign, THEN it SHALL be
   edited in place, as the earlier schema changes were, and development databases
   SHALL be recreated. No review data from an earlier schema is migrated.

| Username | Name | Department | Groups |
|---|---|---|---|
| `admin` | Alan Tan | IT | Administrators |
| `user` | Mary Goh | Finance | Users |
| `multi-group-user` | Grace Lee | Operations | Administrators, Users |
| `account-reviewer-1` | Rachel Lim | Compliance | Account Reviewers |
| `account-reviewer-2` | Ravi Nair | Compliance | Account Reviewers |

`test-user` is renamed `user`.

### R11: Removed population

**User story:** As an account reviewer, I want to review the removed accounts
and confirm them as a whole, so that there is evidence that suspension and
removal are working as intended.

1. WHEN the suspended category is shown for an open task, THEN each row SHALL show the
   facts of an active row and the account's creation time, and also the suspension time,
   reason code and note, and the actor who suspended it (`system` or a username), as
   they were when the task was created. A reviewer SHALL decide a suspended account as
   an active one: confirm it, remove some of its roles, or remove it. A reviewer SHALL
   NOT unsuspend it (ADR 0039).
2. WHEN the removed population is shown for an open task, THEN it SHALL list every
   account removed since the previous task's removed population was confirmed, or
   since the previous task's start date if it was not, or every recorded removal for
   the first task, whoever removed it, with the username, name, department, removal
   time, reason code and note, and the actor, taken from the removal's audit event.
   The rows are read-only.
3. WHEN a reviewer confirms a population with an optional note of at most 200
   characters, THEN the system SHALL freeze the list as shown at that moment, record
   the reviewer, the time, the note and the number of entries, and make that
   population read-only for the task. An empty population can be confirmed.
4. WHEN a population is already confirmed, THEN a second confirmation SHALL be
   rejected with `409`.
5. WHEN a population is confirmed, THEN the system SHALL append an audit event with
   the task, population, count and note.
6. WHEN a population is listed after confirmation, THEN the system SHALL return the
   frozen entries, with the same filters, sorting and pagination as the live list,
   and later suspensions, unsuspensions and removals SHALL NOT change them.
7. WHEN a task is completed, THEN the removed population SHALL be confirmed.

### R12: Completion and report

**User story:** As an auditor, I want the review to complete when the work is done
and a report kept that is not regenerated from changing data.

1. WHEN no item in the active or suspended category is `pending` and the removed population is
   confirmed, THEN the system SHALL complete the task: set it `completed`, record the
   completion time and the user whose action made it true, generate the report,
   store it, and append an audit event with its hash. A task with no active or suspended items is
   complete once the removed population is confirmed.
2. WHEN a reviewer action can make the condition in R12.1 true, THEN the system SHALL
   evaluate it in the same transaction, so that completing and storing the report
   succeed or fail together with the action.
3. WHEN a change outside the review, such as a removal by the inactivity job or a
   suspension, makes the condition true, THEN the scheduled job SHALL complete the
   task at its next run with the completer `system`, and SHALL retry on failure.
4. WHEN a task is completed, THEN the stored report SHALL be written once. The system
   SHALL offer no way to update or delete it.
5. WHEN the report is generated, THEN it SHALL contain, for the task: the review
   period, task start and due dates, completion time and completer, the generation
   time and the generating user; a summary of the counts of confirmed, confirmed with
   groups edited, removed and total; a breakdown by department; every active item
   with name, username, department, groups after the review, outcome, remark and
   decision time; and for each population its list, the confirming reviewer, the
   time, the note and the count.
6. WHEN a client downloads a report in `pdf`, `xlsx` or `csv`, THEN the system SHALL
   return the stored PDF for a completed task, and for the other formats generate
   them from the frozen records. For an open task it SHALL generate the file from
   current data and mark it as a draft.
7. WHEN the `xlsx` is produced, THEN it SHALL hold one sheet per section of R12.5.
   WHEN the `csv` is produced, THEN it SHALL hold one row per active item with the
   columns of the detailed table.
8. WHEN any report is downloaded, THEN the system SHALL append an audit event with
   the task, format, whether it was a draft and the downloader.
9. WHEN the stored report is recorded, THEN the system SHALL keep its SHA-256 hash,
   size, generation time and generating user beside it.

### R13: Frozen data

**User story:** As an auditor, I want to know which data in a review is frozen and
when, so that I can rely on a completed review without the live data.

1. WHEN a task is created, THEN the system SHALL freeze on each item the account's
   public identifier, username and name, and on the task its type and dates.
2. WHEN an item is decided, or set `removed` under R6.8, THEN the system SHALL freeze
   on it the department, last login time, the groups before the decision, and the
   groups after (none for a removal), together with the outcome, the decision time
   and the decider. A pending item carries none of these and shows live data.
3. WHEN an item is removed, THEN the system SHALL NOT copy the removal reason onto
   it; it SHALL keep a reference to the removal's audit event, which is the single
   record of the reason, note and actor.
4. WHEN a population is confirmed, THEN its entries SHALL be frozen as in R11.3.
5. WHEN a task completes, THEN the report SHALL be frozen as in R12.4, and the task,
   its items, its population entries and confirmations SHALL accept no further
   change.
6. WHEN a group is renamed or deleted, or an account is renamed, moved to another
   department or removed, after a record was frozen, THEN the frozen record SHALL NOT
   change. Group names are frozen as text, not as references.
7. WHEN an account is removed after its item was decided, THEN the item SHALL remain
   with its decided outcome and SHALL NOT become `removed`; the removal appears in the
   next removed population.

### R14: Department

**User story:** As an account reviewer, I want to see and filter by department, so
that I can review accounts in context.

1. WHEN an administrator creates or updates a user, THEN the system SHALL accept an
   optional `department` of at most 100 characters and return it on user responses.
2. WHEN the administration user list is requested, THEN the system SHALL support a
   `department` filter and include the department in the search text.
3. WHEN departments are listed for a filter control, THEN the system SHALL return the
   distinct non-empty values in use: for administrators, those of the accounts, and for a
   reviewer, those shown in the task.

## Screens

The front end is built separately. Each screen below lists what it needs and which
rules it must reflect; the REST contract is in [design.md](design.md#rest-api).

**Dashboard.** A table of tasks (R9.1) with filters for type and status and a
status badge that shows `open`, `overdue` or `completed`. A badge in the page
chrome uses the task summary (R9.3). Selecting a row opens the task.

**Task detail.** A header with the review period, due date and progress (R6.5), and
three tabs.

- *Active accounts.* Columns: user, department, groups, last login, outcome and
  actions. Row actions: Confirm, Edit Groups and Remove. Confirm Selected applies to
  the selected rows. Remove asks for a reason code and an optional note. Edit Groups
  shows the assignable groups (R6.6) and saves the full set, which confirms the row.
  Outcome chips are `Pending`, `Confirmed`, `Confirmed (Groups Edited)` and
  `Removed`. A decided row has no actions. A row with `ownAccount` has its actions
  disabled with an explanation. Filters: search, department, group, last login.
- *Suspended accounts.* The same table and actions as the active accounts, with the
  suspension shown (R11.1).
- *Removed accounts.* A read-only list (R11) and a Confirm button with an optional note. After confirmation the list is the frozen one and
  shows who confirmed it and when.

When the task is completed, the screen is read-only and offers the report downloads
(R12.6). A task that is open offers draft downloads, labelled as drafts.

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
- A task of a review month is not created retroactively when the feature is off for
  that month; a missed review month leaves no task.
- The first removed population covers every removal in the audit trail, so it can
  be long. Audit retention bounds it.
- Direct database access can bypass every rule here; the audit table is not
  tamper-proof against the application's own database account.
- The default report renderer draws text with a built-in font, so characters outside
  Western European scripts are not drawn in the PDF; they are intact in the xlsx and csv.
  An application that needs them supplies its own renderer.
- Out of scope: email or push notification, approval workflows, restoring a removed
  account, physical purging or retention of audit events, task types other than the
  account review, a force-complete of a task, and generated OpenAPI clients.
