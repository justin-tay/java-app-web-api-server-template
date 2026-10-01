package com.example.commons.accounts.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Id;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.type.SqlTypes;

/**
 * One account's entry in an account review task (see docs/adr/0032). The scope is fixed
 * when the task is created, the view of the account is live, and the data the reviewer
 * saw is frozen on the item when they decide, so it survives the account being removed.
 *
 * <p>
 * The item holds {@code userId} and {@code username} as plain values with no foreign key
 * to the account, so it outlives it. {@link #getUser()} reads the live account and is
 * null once the account is gone.
 */
@Entity
@Table(name = "review_item")
public class ReviewItem {

	@Id
	@Column(length = 36)
	@JdbcTypeCode(SqlTypes.CHAR)
	private String id;

	@Column(length = 36)
	@JdbcTypeCode(SqlTypes.CHAR)
	private String taskId;

	@Column(name = "user_id", length = 36)
	@JdbcTypeCode(SqlTypes.CHAR)
	private String userId;

	private String username;

	private String name;

	@Enumerated(EnumType.STRING)
	private ReviewStatus reviewStatus;

	private Instant decidedAt;

	private String decidedBy;

	private String decidedAccountStatus;

	private Instant decidedLastLoginAt;

	private Instant decidedSuspendedAt;

	private String decidedReasonCode;

	private String decidedReasonNote;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", insertable = false, updatable = false,
			foreignKey = @jakarta.persistence.ForeignKey(jakarta.persistence.ConstraintMode.NO_CONSTRAINT))
	@NotFound(action = NotFoundAction.IGNORE)
	private AppUser user;

	protected ReviewItem() {
	}

	public ReviewItem(String taskId, AppUser user) {
		this.id = UUID.randomUUID().toString();
		this.taskId = taskId;
		this.userId = user.getId();
		this.user = user;
		this.username = user.getUsername();
		this.name = user.getName();
		this.reviewStatus = ReviewStatus.PENDING_VERIFICATION;
	}

	public String getId() {
		return this.id;
	}

	public String getTaskId() {
		return this.taskId;
	}

	public String getUserId() {
		return this.userId;
	}

	public String getUsername() {
		return this.username;
	}

	public String getName() {
		return this.name;
	}

	public ReviewStatus getReviewStatus() {
		return this.reviewStatus;
	}

	public boolean isPending() {
		return this.reviewStatus == ReviewStatus.PENDING_VERIFICATION;
	}

	public Instant getDecidedAt() {
		return this.decidedAt;
	}

	public String getDecidedBy() {
		return this.decidedBy;
	}

	public String getDecidedAccountStatus() {
		return this.decidedAccountStatus;
	}

	public Instant getDecidedLastLoginAt() {
		return this.decidedLastLoginAt;
	}

	public Instant getDecidedSuspendedAt() {
		return this.decidedSuspendedAt;
	}

	public String getDecidedReasonCode() {
		return this.decidedReasonCode;
	}

	public String getDecidedReasonNote() {
		return this.decidedReasonNote;
	}

	/**
	 * Returns the live account, or null once it has been removed.
	 */
	public AppUser getUser() {
		return this.user;
	}

	/**
	 * Records the decision and freezes what the reviewer saw of the account.
	 * @param status {@link ReviewStatus#VERIFIED} or {@link ReviewStatus#REMOVED}
	 * @param account the account as it stands now
	 * @param by who decided
	 * @param at when
	 * @param reasonCode the reason code given for a removal, or null
	 * @param reasonNote the note given for a removal, or null
	 */
	public void decide(ReviewStatus status, AppUser account, String by, Instant at, String reasonCode,
			String reasonNote) {
		this.reviewStatus = status;
		if (status == ReviewStatus.REMOVED) {
			// The account is about to be deleted; letting go of it keeps the session
			// consistent.
			this.user = null;
		}
		this.name = account.getName();
		this.decidedAt = at;
		this.decidedBy = by;
		this.decidedAccountStatus = account.getStatus().name().toLowerCase(java.util.Locale.ROOT);
		this.decidedLastLoginAt = account.getLastLoginAt();
		this.decidedSuspendedAt = account.getSuspendedAt();
		this.decidedReasonCode = reasonCode != null ? reasonCode : account.getSuspensionReasonCode();
		this.decidedReasonNote = reasonCode != null ? reasonNote : account.getSuspensionNote();
	}

}
