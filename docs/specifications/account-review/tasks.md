# Implementation Plan: Account Lifecycle and Periodic Account Review

## Sequence

Schema and renames come first, then the lifecycle and audit core, the automated
job, settings, the review, the endpoints, and finally fixtures and documentation.
Each task is independently testable and ordered by its dependencies.

- [x] 1. Schema and migration

- [x] 1.1 Add the lifecycle columns
  - Add `status`, `suspended_at`, `suspension_reason_code`, `suspension_note` and
    `inactivity_clock_started_at` to `app_user`; map `enabled` true to `ACTIVE`
    and false to `SUSPENDED` with reason `other`; set the clock to the migration
    time; drop `enabled`.
  - Completion: migration applies to a populated database and no account is
    removed or suspended by the first job run.
  - _Requirements: R1, R10.1_

- [x] 1.2 Add `account_audit_event`, `app_setting`, `task` and `review_item`
  - Portable formatted SQL with the constraints and indexes in design.md; no
    foreign key from audit or review rows to `app_user`.
  - _Requirements: R4, R5, R6_

- [x] 1.3 Seed reference data
  - Add `ACCOUNT_REVIEWER`, `SETTINGS_MANAGE`, the `Account Reviewers` group, the
    `Administrators` role addition and the five setting defaults.
  - _Requirements: R3.2, R10.2_

- [x] 2. Lifecycle and audit core

- [x] 2.1 Rename and extend the audit logger
  - Rename `AdministrationAuditLogger` to `AccountAuditLogger`; append the audit
    row in the changing transaction; keep the ECS events unchanged.
  - Completion: a rolled-back change leaves no row; existing audit logging tests
    pass under the new name.
  - _Requirements: R4_

- [x] 2.2 Add `AccountLifecycleService`
  - Suspend, unsuspend and remove with the reason set, session revocation,
    cascading deletes (memberships, passkeys), the self-modification guard, and
    marking an open review item removed for the system actor.
  - _Requirements: R1, R2.3_

- [x] 2.3 Replace `enabled` in the domain and API
  - Update `AppUser`, `UserStatus`, DTOs, the administration list filters and the
    authentication paths; add the `suspend`, `unsuspend` and `remove` endpoints and
    remove the old `DELETE` and the `enabled` field.
  - _Requirements: R1_

- [x] 3. Settings and the inactivity job

- [x] 3.1 Add `SettingsService` and the settings endpoint
  - Typed settings, validation, audited updates, `SETTINGS_MANAGE`.
  - _Requirements: R3_

- [x] 3.2 Replace `DormantUserDisabler` with `InactiveUserSuspender`
  - Read settings each run; suspend then remove using `AccountLifecycleService`;
    process each account in its own transaction; rename the check-interval
    property; remove `commons.accounts.dormancy.*`.
  - Completion: controlled-clock tests for both thresholds and for two concurrent
    runs.
  - _Requirements: R2_

- [x] 4. Review

- [x] 4.1 Add the task and review item model and the scheduler
  - Window calculation, `AccountReviewScheduler`, the `(type, start_date)` guard,
    item creation for every account.
  - _Requirements: R5_

- [x] 4.2 Add `AccountReviewService`
  - Categories (including the removed category from audit events), decisions with
    all-or-nothing batches, frozen decision data, task completion, the self-review
    rule.
  - _Requirements: R6, R7, R8.1, R8.2_

- [x] 5. Endpoints and security

- [x] 5.1 Add the task, review, audit and settings controllers
  - Endpoints, DTOs, list contract, problem types, authorities.
  - _Requirements: R4.5, R8.3, R9_

- [x] 5.2 Extend recent-login enforcement
  - Cover `/account-reviews/**` and `/admin/settings`.
  - _Requirements: R8.4_

- [x] 6. Fixtures, tests and documentation

- [x] 6.1 Update the development seed and the Keycloak seed
  - Users, groups and names in R10.3; automation off in the `dev` context; update
    tests and docs that name `test-user` and `Test Users`.
  - _Requirements: R3.5, R10.3, R10.4_

- [x] 6.2 Update the system design documents
  - Authorization (new roles, bootstrapping and the removal risk), data model,
    operations (the jobs and settings) and any chapter that mentions the dormancy
    job; supersede the dormancy parts of ADR 0028 and the history-table parts of
    ADR 0021 by reference to the new ADRs.
  - _Requirements: all_

- [ ] 6.3 Full verification
  - Run the Maven suite and formatter; exercise review end to end against the local
    stack with two reviewers, including the self-review block. The Maven suite passes;
    the local stack with Keycloak has not been run.
  - _Requirements: all_
