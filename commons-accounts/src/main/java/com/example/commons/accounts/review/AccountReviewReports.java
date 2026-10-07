package com.example.commons.accounts.review;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;

import com.example.commons.accounts.domain.AccountReviewReport;
import com.example.commons.accounts.domain.AccountReviewReportRepository;
import com.example.commons.accounts.domain.Task;
import com.example.commons.audit.AuditTrail;

/**
 * Stores the PDF report when an account review task completes and serves report downloads
 * (see docs/adr/0037). The stored PDF is written once and is what a completed task always
 * returns, so the evidence is not regenerated from data that keeps changing. The other
 * formats are rendered on demand, and any download before completion is a draft that is
 * never stored. Every download is audited.
 *
 * <p>
 * It runs inside the caller's transaction, so completing a task and storing its report
 * succeed or fail together.
 */
public class AccountReviewReports {

	/**
	 * The formats a report can be downloaded in.
	 */
	public enum Format {

		PDF("application/pdf", "pdf"),
		XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"), CSV("text/csv", "csv");

		private final String contentType;

		private final String extension;

		Format(String contentType, String extension) {
			this.contentType = contentType;
			this.extension = extension;
		}

		public String contentType() {
			return this.contentType;
		}

		public String extension() {
			return this.extension;
		}

		public static Format fromValue(String value) {
			return valueOf(value.toUpperCase(Locale.ROOT));
		}

	}

	/**
	 * A report file ready to send.
	 *
	 * @param draft whether it was generated from an open task
	 */
	public record Download(byte[] content, Format format, boolean draft) {
	}

	private final AccountReviewReportRepository stored;

	private final ReviewReportRenderer renderer;

	private final AuditTrail trail;

	private final Clock clock;

	public AccountReviewReports(AccountReviewReportRepository stored, ReviewReportRenderer renderer, AuditTrail trail,
			Clock clock) {
		this.stored = stored;
		this.renderer = renderer;
		this.trail = trail;
		this.clock = clock;
	}

	public boolean exists(Task task) {
		return this.stored.existsByTaskId(task.getId());
	}

	/**
	 * Renders and stores the PDF of a completed task, and records its hash in the audit
	 * trail.
	 * @param task the task, already completed
	 * @param model the report as it stands at completion
	 * @param by who completed the task
	 */
	void store(Task task, ReviewReportModel model, String by) {
		byte[] pdf = this.renderer.pdf(model);
		String hash = sha256(pdf);
		this.stored.save(new AccountReviewReport(task.getId(), pdf, hash, this.clock.instant(), by));
		this.trail.record(ReviewAudit.task(ReviewAudit.COMPLETE_REVIEW_TASK, task, name(task))
			.details(new ReviewAudit.TaskCompleted(task.getPublicId(), hash, pdf.length))
			.build());
	}

	/**
	 * Returns a report in a format: the stored PDF for a completed task, otherwise a file
	 * rendered from the model. The download is audited.
	 * @param task the task
	 * @param model the report model, which is draft for an open task
	 * @param format the format
	 */
	Download download(Task task, ReviewReportModel model, Format format) {
		byte[] content = switch (format) {
			case PDF -> this.stored.findByTaskId(task.getId())
				.map(AccountReviewReport::getContent)
				.orElseGet(() -> this.renderer.pdf(model));
			case XLSX -> this.renderer.xlsx(model);
			case CSV -> this.renderer.csv(model);
		};
		this.trail.record(ReviewAudit.task(ReviewAudit.EXPORT_REVIEW_REPORT, task, name(task))
			.details(new ReviewAudit.ReportExported(task.getPublicId(), format.extension(), model.draft()))
			.build());
		return new Download(content, format, model.draft());
	}

	private static String name(Task task) {
		return task.getType() + " " + task.getStartDate();
	}

	private static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
