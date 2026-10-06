# ADR 0032: Periodic account review as a task with per-account items

## Status

Accepted. The window, items, decisions, categories and completion are superseded by
[ADR 0037](0037-account-review-populations-and-stored-report.md), and the single review interval
by the two reviews of [ADR 0038](0038-role-permission-model-and-account-review-classes.md).

## Context

Automated suspension and removal ([ADR 0031](0031-inactive-account-suspension-and-removal.md))
do not show whether an account is still needed. Governance practice asks a person
other than the account's administrators to confirm each account on a regular schedule.
The review must be dated, finishable, auditable and not performed on one's own
account. A reviewer may open it mid-window, so data frozen at the start may be stale,
and accounts can be removed while it is open.

Other tasks are likely later, so the dashboard should not be specific to this one.

## Decision

* **Task.** A generic `task` table (type, status `OPEN` or `COMPLETED`, start and due
  date, completion time and user) holds the review as type `account_review`. The
  dashboard and summary are written against `task`. Anything specific stays in
  `review_item`. No task engine, assignment or plug-in registry exists until a second
  type does.
* **Window.** The review interval is a window of N months, calendar-aligned in the
  application time zone, with the task's start and due dates the first and last day
  of the window. A scheduled job creates it on the first run in the window. A
  `(type, start_date)` unique constraint makes concurrent instances safe. An unfinished
  task suppresses the next one and is overdue.
* **Items.** One `review_item` per existing account when the task is created, with a
  review status of `PENDING_VERIFICATION`, `VERIFIED` or `REMOVED`. Items have no
  foreign key to the account. Accounts created later wait for the next window.
* **Snapshot.** The scope is fixed at creation, the view is live, and the data the
  reviewer saw is frozen on the item when they decide. A snapshot of everything at
  creation was rejected as stale by mid-window, and one taken when a reviewer first
  opens the task as hard to explain and racy between reviewers.
* **Categories.** Active and suspended come from the live account. Removed is read-only
  and comes from removal audit events within the window, so automated and manual
  removals both appear.
* **Decisions.** Verify and remove, singly or in a batch that applies entirely or not at
  all. Suspend and unsuspend are recorded actions that leave the review status unchanged.
  Removal needs a reason code. All of them go through the shared lifecycle service.
* **Segregation.** A reviewer cannot act on their own item. If they are the only
  reviewer, the task stays open and overdue instead of offering a bypass. Review and
  settings endpoints need a recent login ([ADR 0023](0023-recent-login-for-administration-changes.md)).
* **Notification.** There is no notification feature. A summary endpoint (open count,
  earliest due date, overdue count) lets the client show a badge.

## Consequences

Reviewers always decide on current data, and the evidence of what they saw survives the
account's removal.

An account created just after a window starts is first reviewed in the next window. A
review window that has overdue work blocks the next, which is visible on the dashboard
and in the warning log.

`ACCOUNT_REVIEWER` is exempt from ADR 0022's rule against granting a role the actor does
not hold, so an administrator can maintain the `Account Reviewers` group without being a
reviewer. The exemption trades prevention for detection: self-modification is still
blocked and every grant is in the audit trail that reviewers read, but two administrators
could collude to grant reviewer access. That risk is accepted for the small number of
administrators a deployment is expected to have.

Email or other notification, restoring removed accounts and other task types are left
for later changes.
