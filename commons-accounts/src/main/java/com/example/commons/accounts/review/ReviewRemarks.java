package com.example.commons.accounts.review;

import java.util.ArrayList;
import java.util.List;

import com.example.commons.accounts.domain.AccountReviewOutcome;

/**
 * The remark shown beside a review outcome, derived from the frozen data so it is never
 * stored: no changes, the roles added and removed, or the removal with its reason.
 */
final class ReviewRemarks {

	private ReviewRemarks() {
	}

	/**
	 * Returns the remark for a decided item.
	 * @param outcome the item's outcome
	 * @param before the roles before the decision
	 * @param after the roles after it, or null for a removal
	 * @param removalReason the reason code of a removal, or null
	 * @return the remark, or null while the item is pending
	 */
	static String remark(AccountReviewOutcome outcome, List<String> before, List<String> after, String removalReason) {
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
