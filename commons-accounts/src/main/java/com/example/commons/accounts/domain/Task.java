package com.example.commons.accounts.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Something a person has to do by a date, such as the periodic account review (see
 * docs/adr/0032). The dashboard and the summary are written against this generic header;
 * whatever is specific to a type of task is held in tables of its own.
 */
@Entity
@Table(name = "task")
public class Task extends AbstractIdentifiedEntity {

	/** The type of the review of the privileged accounts (see docs/adr/0038). */
	public static final String PRIVILEGED_ACCOUNT_REVIEW = "privileged_account_review";

	/** The type of the review of the accounts that are not privileged. */
	public static final String NON_PRIVILEGED_ACCOUNT_REVIEW = "non_privileged_account_review";

	/** The types of the periodic account reviews. */
	public static final java.util.List<String> ACCOUNT_REVIEWS = java.util.List.of(PRIVILEGED_ACCOUNT_REVIEW,
			NON_PRIVILEGED_ACCOUNT_REVIEW);

	private String type;

	@Enumerated(EnumType.STRING)
	private TaskStatus status;

	private LocalDate startDate;

	private LocalDate dueDate;

	private Instant createdAt;

	private Instant completedAt;

	private String completedBy;

	protected Task() {
	}

	public Task(String type, LocalDate startDate, LocalDate dueDate, Instant createdAt) {
		this.type = type;
		this.status = TaskStatus.OPEN;
		this.startDate = startDate;
		this.dueDate = dueDate;
		this.createdAt = createdAt;
	}

	public String getType() {
		return this.type;
	}

	public TaskStatus getStatus() {
		return this.status;
	}

	public LocalDate getStartDate() {
		return this.startDate;
	}

	public LocalDate getDueDate() {
		return this.dueDate;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getCompletedAt() {
		return this.completedAt;
	}

	public String getCompletedBy() {
		return this.completedBy;
	}

	public boolean isOpen() {
		return this.status == TaskStatus.OPEN;
	}

	public void complete(Instant at, String by) {
		this.status = TaskStatus.COMPLETED;
		this.completedAt = at;
		this.completedBy = by;
	}

}
