# ADR 0031: Inactive account suspension and removal

## Status

Accepted

## Context

[ADR 0028](0028-user-last-login-and-dormant-account-disabling.md) disables enabled
users after a threshold, off unless an adopter sets it. Three things need to change.

* The threshold is a policy that administrators want to change without a deployment,
  and a second, longer threshold now removes the account.
* Disabling and "suspended" are the same state, but a reviewer needs to see when and
  why an account was suspended, and the system must not hide that behind a boolean.
* The clock was `updatedAt`, so any administrative edit, such as a rename, restarted
  it, and unsuspending an account had no clock of its own. Setting `lastLoginAt` on
  unsuspend would restart the clock but record a sign-in that did not happen.

Removal needs a decision of its own. A soft-deleted row could be allowed to sign in by a
coding mistake, keeps personal data for no use, and still needs every query to ignore it.

## Decision

* **Status.** `app_user.enabled` is replaced by `status` (`ACTIVE`, `SUSPENDED`), with
  `suspended_at`, a reason code and an optional 200-character note. A suspended account
  is denied sign-in as a disabled one was.
* **Clock.** `inactivity_clock_started_at` is set on creation and on unsuspend. The
  clock is the later of it and `lastLoginAt`. `lastLoginAt` is set only by a sign-in.
* **Job.** `DormantUserDisabler` becomes `InactiveUserSuspender` and uses the word
  "inactive" throughout: it suspends accounts older than `inactivity.suspendAfterDays`
  with reason `inactive_account` and removes accounts older than
  `inactivity.removeAfterDays`. It is on by default, at 90 and 180 days, because
  leaving stale accounts usable is the less secure default. It runs hourly, configured by
  `commons.accounts.inactivity.check-interval`.
* **Settings.** The thresholds, the on/off switch and the review window live in an
  `app_setting` table with typed, validated keys, edited by a settings administrator
  and audited. Development and test data turn them off through the `dev` context.
* **Hard delete.** Removal deletes the account, its memberships and its passkeys in one
  transaction and ends its sessions. The audit trail
  ([ADR 0030](0030-business-audit-trail-table.md)) and review items hold the record,
  with no foreign key to the account.
* **No exemption for the last administrator.** The job may remove the only holder of
  `USER_MANAGE`. The risk is documented; recovery is the bootstrap changeset, as ADR
  0022 already accepts for administrators who remove each other.

This supersedes the dormancy decisions of ADR 0028: the property-based threshold, off by
default, `updatedAt` as the clock, and the disable-only behaviour. Its `lastLoginAt` and
pending status stay.

## Consequences

An upgrade sets every account's clock to the migration time, so it gives everyone a full
threshold instead of removing long-idle users on the first run.

Removal is permanent and cannot be undone through the application. Removing a deployment's
only administrator needs database access to fix.

An unsuspended account that is not used is suspended again after the suspension
threshold. `UserStatus` and the `enabled` field in the administration API change, so
clients that read or write `enabled` must move to `status` and the suspend, unsuspend
and remove actions.
