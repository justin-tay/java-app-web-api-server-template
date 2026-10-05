package com.example.commons.accounts.domain;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A reviewer's confirmation of the suspended or the removed population of a task, made
 * once. It is written once and has no way to change (see docs/adr/0037); the list as it
 * was at that moment is held in {@link AccountReviewPopulationEntry} rows.
 */
@Entity
@Table(name = "account_review_attestation")
public class AccountReviewAttestation extends AbstractIdentifiedEntity {

	private Long taskId;

	@Enumerated(EnumType.STRING)
	private ReviewPopulation population;

	private String confirmedBy;

	private Instant confirmedAt;

	private String note;

	private int entryCount;

	protected AccountReviewAttestation() {
	}

	public AccountReviewAttestation(Long taskId, ReviewPopulation population, String confirmedBy, Instant confirmedAt,
			String note, int entryCount) {
		this.taskId = taskId;
		this.population = population;
		this.confirmedBy = confirmedBy;
		this.confirmedAt = confirmedAt;
		this.note = note;
		this.entryCount = entryCount;
	}

	public Long getTaskId() {
		return this.taskId;
	}

	public ReviewPopulation getPopulation() {
		return this.population;
	}

	public String getConfirmedBy() {
		return this.confirmedBy;
	}

	public Instant getConfirmedAt() {
		return this.confirmedAt;
	}

	public String getNote() {
		return this.note;
	}

	public int getEntryCount() {
		return this.entryCount;
	}

}
