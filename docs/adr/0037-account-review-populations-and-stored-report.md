# ADR 0037: Account review by populations, with a stored completion report

## Status

Accepted. Partly supersedes [ADR 0032](0032-periodic-account-review.md): the window,
items, decisions, categories and completion decisions. Partly superseded by
[ADR 0038](0038-role-permission-model-and-account-review-classes.md): the review is split into
a privileged and a non-privileged review, each covering the accounts of its own class
in the same three parts, and a reviewer edits roles by removing them.

## Context

[ADR 0032](0032-periodic-account-review.md) gave a review an N-month window, an item for
every account, verify and remove decisions, suspend and unsuspend from the task, and
completion when the last item left `pending_verification`. Using it showed four gaps.

* A reviewer must confirm two things for an active account: that it is still needed, and
  that the access it holds is correct. Verify did not let the reviewer correct the
  access. Roles come only from groups, so correcting access means changing groups.
* Suspended and removed accounts are not decided one by one. The review needs evidence
  that suspension and removal are working, which is a judgement on the whole list.
* The review window was N months wide and its removed category covered only the window,
  but the review is wanted in a fixed month, and the list must leave no gap between two
  reviews.
* The audit needs a record that is not regenerated from data that keeps changing, and
  the report in the audit evidence needs to show what the reviewer saw.

## Decision

* **Schedule.** A task is created in a review month, where `(month - 1) mod N = 0` and
  N is `review.intervalMonths` (1, 3, 6 or 12). Its period is that one month. A new
  task is created even when the previous one is unfinished, which stays open and
  overdue, because the two review different data.
* **Items.** One item per account that is active at task creation, with outcome
  `PENDING`, `CONFIRMED`, `CONFIRMED_GROUPS_EDITED` or `REMOVED`. Confirm attests the
  account and its groups together; a groups edit changes the groups and confirms in one
  step. The reviewer cannot suspend or unsuspend from a task. The own-account rule
  stays. The item table is renamed `account_review_item`.
* **Populations.** The suspended and removed accounts are confirmed as two populations,
  once each, with an optional note. Confirming freezes the list in
  `account_review_population_entry`. The removed list covers every removal since the
  previous task's removed population was confirmed, or since the previous task's start
  date if it was not, so no removal is missed.
* **Frozen data.** An item shows live data while pending and is frozen when decided,
  holding the department, last login and the groups before and after as text. The
  removal reason is not copied; the item points at the removal's audit event. Nothing in
  a decided item, a confirmed population or a completed task changes afterwards. The
  design lists each element and when it freezes.
* **Completion.** There is no complete call. The task completes when no active item is
  pending and both populations are confirmed, in the transaction of the action that made
  it true, or at the next scheduler run when an outside change did, with completer
  `system`.
* **Report.** Completing renders a PDF once and stores it, with its SHA-256, in
  `account_review_report`, so the evidence is not regenerated. The xlsx and csv are
  generated from the frozen records on demand, and any download before completion is a
  marked draft. Every download is audited.
* **Department.** `app_user` gets an optional free-text `department`, filtered by the
  distinct values in use. A separate table was rejected until departments need
  attributes of their own.
* **Libraries.** OpenPDF 2.0.x for PDF, with a small reusable `ReportDocument` base, and
  Apache POI streaming workbooks for xlsx, behind a `ReviewReportRenderer` interface so
  adopters can replace them. OpenPDF 2.0.x is the last line built for Java 17; 2.1 and
  later need Java 21. The PDF uses the built-in Helvetica font, so characters outside
  Western European scripts are not drawn in it.
* **Schema.** Edited in place and development databases recreated, as the earlier schema
  changes were; no review data is migrated.

## Consequences

A reviewer can correct access in the same step as confirming the account, and the report
shows the groups before and after. A completed review is a stored, hashed PDF that stays
the same however the accounts change.

Several tasks can be open at once, so a reviewer works the oldest first and the dashboard
shows both. An account that is suspended mid-task is excluded from the completion test
while suspended, and one removed mid-task becomes `removed` with the remover as the
decider.

The first removed population covers every recorded removal, so it can be long for an
adopter that enables the review late. Rendering a report adds a library and makes
completion fail with the action if it cannot render, which is chosen over a completed
task without evidence.

A reviewer cannot suspend or unsuspend an account from a review. Suspension is left to
the inactivity job and administrators, and the reviewer validates the result.
