package com.example.commons.accounts.review;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.example.commons.accounts.domain.AccountReviewCategory;
import com.example.commons.accounts.domain.AccountReviewItem;
import com.example.commons.accounts.domain.AccountReviewOutcome;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.review.ReviewDtos.Suspension;

/**
 * What a review item shows to a reader, whether in the list or in the report: a pending
 * item shows the live account, a decided one what was frozen when it was decided. The
 * remark is derived from the frozen data, so it is never stored.
 *
 * @param name the account holder's name
 * @param department the department
 * @param createdAt when the account was created
 * @param lastLoginAt when the account last signed in
 * @param roles the role names the account holds, or held after the decision
 * @param remark no changes, the roles added and removed, or the removal with its reason;
 * null while the item is pending
 * @param suspension the suspension of a suspended account, null for an active one
 */
record ReviewItemView(String name, String department, Instant createdAt, Instant lastLoginAt, List<String> roles,
		String remark, Suspension suspension) {

	/**
	 * Returns what an item shows.
	 * @param item the item
	 * @param removalReason the reason code of the item's removal, or null when it is not
	 * known or the item was not removed
	 * @return the view
	 */
	static ReviewItemView of(AccountReviewItem item, String removalReason) {
		AppUser live = item.getUser();
		boolean pending = item.isPending();
		String name = pending && live != null ? live.getName() : item.getFullName();
		String department = pending && live != null ? live.getDepartment() : item.getDepartment();
		Instant createdAt = pending && live != null ? live.getCreatedAt() : item.getAccountCreatedAt();
		List<String> roles = pending ? (live == null ? List.of() : live.roleNames())
				: item.getRolesAfter() == null ? List.of() : item.getRolesAfter();
		Suspension suspension = item.getCategory() == AccountReviewCategory.SUSPENDED
				? new Suspension(item.getSuspendedAt(), item.getSuspendedBy(), item.getSuspensionReasonCode(),
						item.getSuspensionNote())
				: null;
		Instant lastLoginAt = pending && live != null ? live.getLastLoginAt() : item.getLastLoginAt();
		return new ReviewItemView(name, department, createdAt, lastLoginAt, roles,
				remark(item.getOutcome(), item.getRolesBefore(), item.getRolesAfter(), removalReason), suspension);
	}

	private static String remark(AccountReviewOutcome outcome, List<String> before, List<String> after,
			String removalReason) {
		return switch (outcome) {
			case PENDING -> null;
			case CONFIRMED -> "No changes";
			case CONFIRMED_ROLES_EDITED -> roleChanges(before, after);
			case REMOVED -> removalReason == null ? "Account removed" : "Account removed (" + removalReason + ")";
		};
	}

	private static String roleChanges(List<String> before, List<String> after) {
		List<String> parts = new ArrayList<>();
		List<String> added = after.stream().filter(name -> !before.contains(name)).toList();
		List<String> removed = before.stream().filter(name -> !after.contains(name)).toList();
		if (!added.isEmpty()) {
			parts.add("Added " + String.join(", ", added));
		}
		if (!removed.isEmpty()) {
			parts.add("Removed " + String.join(", ", removed));
		}
		return String.join("; ", parts);
	}

}
