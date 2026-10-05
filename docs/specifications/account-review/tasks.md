# Implementation Plan: Account Lifecycle and Periodic Account Review

## Sequence

The lifecycle, audit, settings and inactivity work was delivered first. The review
redesign (R5 to R14) follows in the order below: schema and domain, then the review
service and scheduler, then the report, then the endpoints, then documentation and
verification. Each task is independently testable and ordered by its dependencies.

## Delivered earlier

- [x] 1. Lifecycle status, `account_audit_event`, `app_setting`, reference data (R1, R3, R4, R10.1, R10.2)
- [x] 2. `AccountLifecycleService`, `AccountAuditLogger`, `status` in the domain and API (R1, R4)
- [x] 3. `SettingsService`, the settings endpoint and `InactiveUserSuspender` (R2, R3)
- [x] 4. The first review model: window tasks, per-account items, verify and remove, endpoints and recent-login enforcement (superseded by tasks 6 to 10)
- [x] 5. Development seed and Keycloak seed users (R10.3, R10.4)

## Review redesign

- [x] 6. Schema and domain

- [x] 6.1 Edit the module schema in place
  - Add `app_user.department`; replace `review_item` with `account_review_item` as in
    design.md; add `account_review_attestation`, `account_review_population_entry` and
    `account_review_report`; regenerate the SQL scripts for each database.
  - Completion: `DatabaseChangelogTest` and the containerised script tests pass.
  - _Requirements: R10.5, R11, R12, R13, R14_

- [x] 6.2 Add the entities and repositories
  - `AccountReviewItem` with outcome and evidence columns, the attestation, entry and
    report entities; the report repository exposes no update or delete.
  - _Requirements: R12.4, R13_

- [x] 6.3 Add `department`
  - Domain, admin create and update, list filter and search, the `/admin/departments`
    list, and the seed users.
  - _Requirements: R10.3, R14_

- [x] 7. Review service and scheduler

- [x] 7.1 Review months and task creation
  - `intervalMonths` limited to 1, 3, 6 or 12; the `(month - 1) mod N` rule; items for
    active accounts only; no suppression by an open task; the `(type, start_date)` guard.
  - _Requirements: R3.3, R3.7, R5_

- [x] 7.2 Decisions and group edit
  - Confirm, remove and groups edit with the frozen evidence, the all-or-nothing batch,
    the self-review rule, the assignable groups endpoint, and removal of the item
    suspend and unsuspend actions.
  - _Requirements: R6, R7, R8_

- [x] 7.3 Outside changes
  - A removal outside the review sets a pending item `removed`; a suspended pending item
    leaves the active category and the completion test.
  - _Requirements: R2.3, R6.8, R6.9_

- [x] 7.4 Populations
  - Live lists, the removed population's lower bound, confirmation that freezes the
    entries, the conflict on a second confirmation.
  - _Requirements: R11_

- [x] 7.5 Completion
  - The same-transaction evaluation after reviewer actions, the scheduler's evaluation,
    and the `system` completer.
  - _Requirements: R12.1 to R12.3_

- [x] 8. Report

- [x] 8.1 Add `ReportDocument` and `ReviewReportRenderer`
  - OpenPDF base with title block, summary tiles, tables, page numbers and the draft
    marker; Apache POI streaming xlsx; csv. Produce a sample PDF for approval of the look
    before the rest is wired.
  - _Requirements: R12.5, R12.7_

- [x] 8.2 Store and serve the report
  - Store the PDF with hash, size and generator at completion; serve it for a completed
    task and generate drafts for an open one; audit every download.
  - _Requirements: R12.4, R12.6, R12.8, R12.9_

- [x] 9. Endpoints and security

- [x] 9.1 Update the controllers and DTOs
  - Task counts and populations, items with department and groups, decisions, groups
    edit, populations, report download, problem types.
  - _Requirements: R6, R7, R9, R11, R12_

- [x] 9.2 Add the review audit events
  - The six actions in R4.7 with their details.
  - _Requirements: R4.7_

- [ ] 10. Tests, documentation and verification

- [x] 10.1 Update and add tests
  - The frozen data, mid-task, report and migration tests listed in design.md; update
    the existing review tests and the demo sample data test for the new model.
  - _Requirements: all_

- [ ] 10.2 Update the system design documents
  - Domain model, building blocks, authorisation, operations, the event reference and
    the README mention; add the stored report and populations; mark ADR 0032 as partly
    superseded.
  - _Requirements: all_

- [ ] 10.3 Full verification
  - Run the Maven suite and formatter; exercise a review end to end against the local
    stack with two reviewers: confirm, edit groups, remove, the self-review block, both
    population confirmations, completion, and the stored PDF.
  - _Requirements: all_
