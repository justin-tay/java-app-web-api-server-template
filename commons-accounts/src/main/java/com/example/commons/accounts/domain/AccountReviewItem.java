package com.example.commons.accounts.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.type.SqlTypes;

/**
 * One active account's entry in an account review task (see docs/adr/0037). The identity
 * is frozen when the task is created. While the item is pending the account is read live
 * through {@link #getUser()}. When the item is decided, or the account is removed outside
 * the review, the evidence columns are written once: the department, last login time and
 * the groups before and after. A decided item never changes afterwards.
 *
 * <p>
 * The item holds {@code userPublicId} and {@code username} as plain values with no
 * foreign key to the account, so it outlives it. {@code removalAuditEventId} points at
 * the removal's audit event, which is the one record of the reason, note and actor.
 */
@Entity
@Table(name = "account_review_item")
public class AccountReviewItem extends AbstractIdentifiedEntity {

	private Long taskId;

	@Column(name = "user_public_id")
	@JdbcTypeCode(SqlTypes.UUID)
	private UUID userPublicId;

	private String username;

	private String fullName;

	@Enumerated(EnumType.STRING)
	private AccountReviewOutcome outcome;

	private Instant decidedAt;

	private String decidedBy;

	private Long removalAuditEventId;

	private String department;

	private Instant lastLoginAt;

	@Convert(converter = GroupNamesConverter.class)
	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	private List<String> groupsBefore;

	@Convert(converter = GroupNamesConverter.class)
	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	private List<String> groupsAfter;

	@ManyToOne
	@JoinColumn(name = "user_public_id", referencedColumnName = "publicId", insertable = false, updatable = false,
			foreignKey = @jakarta.persistence.ForeignKey(jakarta.persistence.ConstraintMode.NO_CONSTRAINT))
	@NotFound(action = NotFoundAction.IGNORE)
	private AppUser user;

	protected AccountReviewItem() {
	}

	public AccountReviewItem(Long taskId, AppUser user) {
		this.taskId = taskId;
		this.userPublicId = user.getPublicId();
		this.user = user;
		this.username = user.getUsername();
		this.fullName = user.getName();
		this.outcome = AccountReviewOutcome.PENDING;
	}

	public Long getTaskId() {
		return this.taskId;
	}

	public UUID getUserPublicId() {
		return this.userPublicId;
	}

	public String getUsername() {
		return this.username;
	}

	public String getFullName() {
		return this.fullName;
	}

	public AccountReviewOutcome getOutcome() {
		return this.outcome;
	}

	public boolean isPending() {
		return this.outcome == AccountReviewOutcome.PENDING;
	}

	public Instant getDecidedAt() {
		return this.decidedAt;
	}

	public String getDecidedBy() {
		return this.decidedBy;
	}

	public Long getRemovalAuditEventId() {
		return this.removalAuditEventId;
	}

	public String getDepartment() {
		return this.department;
	}

	public Instant getLastLoginAt() {
		return this.lastLoginAt;
	}

	public List<String> getGroupsBefore() {
		return this.groupsBefore;
	}

	public List<String> getGroupsAfter() {
		return this.groupsAfter;
	}

	/**
	 * Returns the live account, or null once it has been removed.
	 */
	public AppUser getUser() {
		return this.user;
	}

	/**
	 * Confirms the account and its groups, freezing the evidence.
	 * @param account the account as it stands now
	 * @param groups the names of the groups it holds
	 */
	public void confirm(AppUser account, List<String> groups, String by, Instant at) {
		decide(AccountReviewOutcome.CONFIRMED, account, groups, groups, by, at);
	}

	/**
	 * Records that the reviewer changed the groups, which confirms the account in the
	 * same step.
	 */
	public void confirmWithGroupsEdited(AppUser account, List<String> before, List<String> after, String by,
			Instant at) {
		decide(AccountReviewOutcome.CONFIRMED_GROUPS_EDITED, account, before, after, by, at);
	}

	/**
	 * Records that the account is being removed, whether by the reviewer or by anyone
	 * else. The evidence is read from the account, so a caller must call this before the
	 * account is deleted.
	 * @param removalAuditEventId the removal's audit event
	 */
	public void remove(AppUser account, List<String> groups, String by, Instant at, Long removalAuditEventId) {
		decide(AccountReviewOutcome.REMOVED, account, groups, null, by, at);
		this.removalAuditEventId = removalAuditEventId;
		// The account is about to be deleted; letting go of it keeps the session
		// consistent.
		this.user = null;
	}

	private void decide(AccountReviewOutcome result, AppUser account, List<String> before, List<String> after,
			String by, Instant at) {
		if (!isPending()) {
			throw new IllegalStateException("A decided review item cannot change");
		}
		this.outcome = result;
		this.decidedAt = at;
		this.decidedBy = by;
		this.department = account.getDepartment();
		this.lastLoginAt = account.getLastLoginAt();
		this.groupsBefore = List.copyOf(before);
		this.groupsAfter = after == null ? null : List.copyOf(after);
	}

}
