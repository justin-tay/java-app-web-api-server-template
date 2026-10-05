package com.example.commons.accounts.domain;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * The PDF stored when an account review task completes (see docs/adr/0037). It is written
 * once, so the evidence is not regenerated from data that keeps changing. It has a
 * constructor and getters but no way to change.
 */
@Entity
@Table(name = "account_review_report")
public class AccountReviewReport extends AbstractIdentifiedEntity {

	private Long taskId;

	private byte[] content;

	private long sizeBytes;

	private String sha256;

	private Instant generatedAt;

	private String generatedBy;

	protected AccountReviewReport() {
	}

	public AccountReviewReport(Long taskId, byte[] content, String sha256, Instant generatedAt, String generatedBy) {
		this.taskId = taskId;
		this.content = content.clone();
		this.sizeBytes = content.length;
		this.sha256 = sha256;
		this.generatedAt = generatedAt;
		this.generatedBy = generatedBy;
	}

	public Long getTaskId() {
		return this.taskId;
	}

	public byte[] getContent() {
		return this.content.clone();
	}

	public long getSizeBytes() {
		return this.sizeBytes;
	}

	public String getSha256() {
		return this.sha256;
	}

	public Instant getGeneratedAt() {
		return this.generatedAt;
	}

	public String getGeneratedBy() {
		return this.generatedBy;
	}

}
