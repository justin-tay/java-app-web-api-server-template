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
 * One active or suspended account's entry in an account review task (see docs/adr/0037
 * and docs/adr/0039). The identity, the category and, for a suspended account, the
 * suspension are frozen when the task is created. While the item is pending the account
 * is read live through {@link #getUser()}. When the item is decided, or the account is
 * removed outside the review, the evidence columns are written once: the department,
 * creation, last login and last activity times and the roles before and after. A decided
 * item never changes afterwards.
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
	private AccountReviewCategory category;

	@Enumerated(EnumType.STRING)
	private AccountReviewOutcome outcome;

	private Instant decidedAt;

	private String decidedBy;

	private Long removalAuditEventId;

	private String department;

	private Instant accountCreatedAt;

	private Instant lastLoginAt;

	private Instant lastActivityAt;

	private Instant suspendedAt;

	private String suspendedBy;

	private String suspensionReasonCode;

	private String suspensionNote;

	@Convert(converter = NamesConverter.class)
	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	private List<String> rolesBefore;

	@Convert(converter = NamesConverter.class)
	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	private List<String> rolesAfter;

	/**
	 * The privileged permissions the account held when the item was created, which is why
	 * a privileged account review covers it. Null in a non-privileged review.
	 */
	@Convert(converter = NamesConverter.class)
	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	private List<String> privilegedPermissions;

	@ManyToOne
	@JoinColumn(name = "user_public_id", referencedColumnName = "publicId", insertable = false, updatable = false,
			foreignKey = @jakarta.persistence.ForeignKey(jakarta.persistence.ConstraintMode.NO_CONSTRAINT))
	@NotFound(action = NotFoundAction.IGNORE)
	private AppUser user;

	protected AccountReviewItem() {
	}

	/**
	 * Creates a pending item for an account, in the category of its status. A suspended
	 * account's suspension is copied from it.
	 * @param taskId the task
	 * @param user the account
	 * @param privilegedPermissions the privileged permissions the account holds, for a
	 * privileged account review, or null for a non-privileged one
	 * @param suspendedBy who suspended the account, or null when it is not known or the
	 * account is active
	 */
	public AccountReviewItem(Long taskId, AppUser user, List<String> privilegedPermissions, String suspendedBy) {
		this.privilegedPermissions = privilegedPermissions == null ? null : List.copyOf(privilegedPermissions);
		this.category = user.getStatus() == AccountStatus.SUSPENDED ? AccountReviewCategory.SUSPENDED
				: AccountReviewCategory.ACTIVE;
		if (this.category == AccountReviewCategory.SUSPENDED) {
			this.suspendedAt = user.getSuspendedAt();
			this.suspendedBy = suspendedBy;
			this.suspensionReasonCode = user.getSuspensionReasonCode();
			this.suspensionNote = user.getSuspensionNote();
		}
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

	public AccountReviewCategory getCategory() {
		return this.category;
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

	public Instant getAccountCreatedAt() {
		return this.accountCreatedAt;
	}

	public Instant getSuspendedAt() {
		return this.suspendedAt;
	}

	public String getSuspendedBy() {
		return this.suspendedBy;
	}

	public String getSuspensionReasonCode() {
		return this.suspensionReasonCode;
	}

	public String getSuspensionNote() {
		return this.suspensionNote;
	}

	public Instant getLastActivityAt() {
		return this.lastActivityAt;
	}

	public Instant getLastLoginAt() {
		return this.lastLoginAt;
	}

	public List<String> getRolesBefore() {
		return this.rolesBefore;
	}

	public List<String> getRolesAfter() {
		return this.rolesAfter;
	}

	public List<String> getPrivilegedPermissions() {
		return this.privilegedPermissions;
	}

	/**
	 * Returns whether the account exists and still has the status that put the item in
	 * its category.
	 */
	public boolean accountInCategory() {
		return this.user != null && this.user.getStatus() == this.category.status();
	}

	/**
	 * Returns the live account, or null once it has been removed.
	 */
	public AppUser getUser() {
		return this.user;
	}

	/**
	 * Confirms the account and its roles, freezing the evidence.
	 * @param account the account as it stands now
	 * @param roles the names of the roles it holds
	 */
	public void confirm(AppUser account, List<String> roles, String by, Instant at) {
		decide(AccountReviewOutcome.CONFIRMED, account, roles, roles, by, at);
	}

	/**
	 * Records that the reviewer removed roles, which confirms the account in the same
	 * step.
	 */
	public void confirmWithRolesEdited(AppUser account, List<String> before, List<String> after, String by,
			Instant at) {
		decide(AccountReviewOutcome.CONFIRMED_ROLES_EDITED, account, before, after, by, at);
	}

	/**
	 * Records that the account is being removed, whether by the reviewer or by anyone
	 * else. The evidence is read from the account, so a caller must call this before the
	 * account is deleted.
	 * @param removalAuditEventId the removal's audit event
	 */
	public void remove(AppUser account, List<String> roles, String by, Instant at, Long removalAuditEventId) {
		decide(AccountReviewOutcome.REMOVED, account, roles, null, by, at);
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
		this.accountCreatedAt = account.getCreatedAt();
		this.lastLoginAt = account.getLastLoginAt();
		this.lastActivityAt = account.lastActivityAt();
		this.rolesBefore = List.copyOf(before);
		this.rolesAfter = after == null ? null : List.copyOf(after);
	}

}
