# ADR 0039: Review suspended accounts one by one, like active accounts

## Status

Accepted. Partly supersedes [ADR 0037](0037-account-review-populations-and-stored-report.md)
and [ADR 0038](0038-role-permission-model-and-account-review-classes.md): the suspended
accounts are no longer a population that a reviewer confirms as a whole.

## Context

[ADR 0037](0037-account-review-populations-and-stored-report.md) reviews the active accounts
one by one and confirms the suspended and the removed accounts as two populations. That fits
removed accounts, which no longer exist and can only be listed as evidence. It does not fit
suspended accounts. A suspended account still exists, holds roles and can be unsuspended, so
a reviewer should decide whether it is still needed and whether its roles are right, as for
an active account. A bulk confirmation of the list does not show that anyone looked at each
account.

## Decision

* **Items.** A task has one item for every active and every suspended account of its class
  at task creation. The item's `category`, `ACTIVE` or `SUSPENDED`, is the status the account
  had then and does not change. A suspended item also keeps the suspension as it was at
  task creation: when, by whom, the reason code and the note.
* **Decisions.** A suspended item takes the same outcomes as an active one: confirmed,
  confirmed after roles were removed, or removed. A reviewer cannot unsuspend an account from
  a task, because that adds access and is not the reviewer's to do.
* **Live while the status holds.** A pending item is live only while its account still has the
  status of its category. An account that is unsuspended, or that is suspended after task
  creation, leaves its list and stops blocking completion. It is reviewed in the next task
  that finds it in that status. A decided item stays in its list.
* **Populations.** Only the removed accounts remain a population. The suspended population,
  its confirmation and its permission use are gone; `review:confirm-population` now confirms
  the removed population only.
* **Completion.** A task completes when no active item and no suspended item is pending and
  the removed population is confirmed.
* **API.** The items endpoint takes a `category` of `active` (the default) or `suspended`.
  A task reports its counts and its progress for each category, as `active` and `suspended`,
  and the confirmation of the removed population as `removed`. The population endpoints
  accept `removed` only.
* **Report.** The report has a summary, a breakdown by department and a detailed table for
  each category, and the removed accounts as a confirmed list. Every table shows when the
  account was created and when it last signed in. The suspended table adds when and by whom
  the account was suspended and why, and its remarks include the suspension note. The csv is
  one file with a category column for the active, suspended and removed accounts, and the xlsx
  keeps a sheet for each category.
* **Account creation time.** A removed account's creation time is read from its removal's audit
  event, so `createdAt` joins the details a removal records. A removal recorded before this
  change shows no creation time.
* **Schema.** The schema is edited in place and development databases are recreated, as in
  [ADR 0037](0037-account-review-populations-and-stored-report.md); no data is migrated.
  `account_review_item` gains `category`, `account_created_at` and the suspension columns, and
  `account_review_population_entry` gains `created_at`.

## Consequences

A reviewer attests each suspended account the same way as an active one, and the stored report
shows the outcome per account instead of one confirmation. The review has more items to decide
where an organisation suspends many accounts, which is the point. A suspended account that is
removed through a review is counted as a removal in the removed population, as an active one is.
