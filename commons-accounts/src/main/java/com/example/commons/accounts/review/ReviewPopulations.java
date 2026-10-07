package com.example.commons.accounts.review;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import com.example.commons.accounts.audit.AccountAudit;
import com.example.commons.accounts.audit.AccountAudit.Removal;
import com.example.commons.accounts.domain.AccountReviewAttestation;
import com.example.commons.accounts.domain.AccountReviewAttestationRepository;
import com.example.commons.accounts.domain.AccountReviewPopulationEntry;
import com.example.commons.accounts.domain.AccountReviewPopulationEntryRepository;
import com.example.commons.accounts.domain.ReviewPopulation;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.domain.TaskRepository;
import com.example.commons.accounts.review.ReviewDtos.PopulationEntryResponse;
import com.example.commons.accounts.review.ReviewDtos.PopulationStatus;
import com.example.commons.web.problem.ConflictException;

/**
 * The removed population of an account review task: live until a reviewer confirms it,
 * then frozen as it was when confirmed (see docs/adr/0038 and docs/adr/0039). It owns the
 * live query, the frozen entries and the stored confirmation, so nothing else reads the
 * confirmation tables. It runs inside the caller's transaction.
 */
public class ReviewPopulations {

	private final TaskRepository tasks;

	private final AccountReviewAttestationRepository attestations;

	private final AccountReviewPopulationEntryRepository entries;

	private final AccountAudit audit;

	private final Clock clock;

	private final ZoneId zone;

	public ReviewPopulations(TaskRepository tasks, AccountReviewAttestationRepository attestations,
			AccountReviewPopulationEntryRepository entries, AccountAudit audit, Clock clock, ZoneId zone) {
		this.tasks = tasks;
		this.attestations = attestations;
		this.entries = entries;
		this.audit = audit;
		this.clock = clock;
		this.zone = zone;
	}

	/**
	 * Returns a population as it is now, or as it was when confirmed. A review lists the
	 * accounts of its own class.
	 * @param task the task
	 * @param population the population
	 * @return the rows
	 */
	public List<PopulationEntryResponse> rows(Task task, ReviewPopulation population) {
		Optional<AccountReviewAttestation> attestation = this.attestations.findByTaskIdAndPopulation(task.getId(),
				population);
		if (attestation.isPresent()) {
			return this.entries.findByAttestationId(attestation.get().getId())
				.stream()
				.map(entry -> new PopulationEntryResponse(entry.getUserPublicId(), entry.getUsername(),
						entry.getFullName(), entry.getDepartment(), entry.getCreatedAt(), entry.getLastLoginAt(),
						entry.getLastActivityAt(), entry.getOccurredAt(), entry.getActor(), entry.getReasonCode(),
						entry.getReasonNote()))
				.toList();
		}
		return liveRemoved(task);
	}

	/**
	 * Returns whether the removed population of a task has been confirmed, and by whom.
	 * @param task the task
	 * @return the status of the population
	 */
	public PopulationStatus status(Task task) {
		return status(this.attestations.findByTaskIdAndPopulation(task.getId(), ReviewPopulation.REMOVED).orElse(null));
	}

	/**
	 * Returns a population with its confirmation, if it has been confirmed.
	 * @param task the task
	 * @param population the population
	 * @return the section the report shows
	 */
	public ReviewReportModel.PopulationSection section(Task task, ReviewPopulation population) {
		Optional<AccountReviewAttestation> attestation = this.attestations.findByTaskIdAndPopulation(task.getId(),
				population);
		return new ReviewReportModel.PopulationSection(attestation.isPresent(),
				attestation.map(AccountReviewAttestation::getConfirmedBy).orElse(null),
				attestation.map(AccountReviewAttestation::getConfirmedAt).orElse(null),
				attestation.map(AccountReviewAttestation::getNote).orElse(null), rows(task, population));
	}

	/**
	 * Returns whether every population of a task has been confirmed.
	 * @param task the task
	 * @return whether they are all confirmed
	 */
	public boolean allConfirmed(Task task) {
		return this.attestations.findByTaskId(task.getId()).size() >= ReviewPopulation.values().length;
	}

	/**
	 * Confirms a population, once: the list as it is now is frozen.
	 * @param task the task
	 * @param population the population
	 * @param actor who confirms it
	 * @param note the optional note
	 * @return the number of accounts in the frozen list
	 * @throws ConflictException if the population is already confirmed
	 */
	public int confirm(Task task, ReviewPopulation population, String actor, String note) {
		if (this.attestations.findByTaskIdAndPopulation(task.getId(), population).isPresent()) {
			throw new ConflictException("The population is already confirmed.");
		}
		List<PopulationEntryResponse> rows = rows(task, population);
		AccountReviewAttestation attestation = this.attestations.save(
				new AccountReviewAttestation(task.getId(), population, actor, this.clock.instant(), note, rows.size()));
		this.entries.saveAll(rows.stream()
			.map(row -> new AccountReviewPopulationEntry(attestation.getId(), row.userId(), row.username(), row.name(),
					row.department(), row.createdAt(), row.lastLoginAt(), row.lastActivityAt(), row.occurredAt(),
					row.actor(), row.reasonCode(), row.reasonNote()))
			.toList());
		return rows.size();
	}

	/**
	 * Returns the removals since the previous task's removed population was confirmed, or
	 * since that task started if it never was, or every recorded removal for the first
	 * task, so no removal falls between two reviews, and only the removals of the task's
	 * class.
	 */
	private List<PopulationEntryResponse> liveRemoved(Task task) {
		Instant since = this.tasks
			.findFirstByTypeAndStartDateBeforeOrderByStartDateDesc(task.getType(), task.getStartDate())
			.map(previous -> this.attestations.findByTaskIdAndPopulation(previous.getId(), ReviewPopulation.REMOVED)
				.map(AccountReviewAttestation::getConfirmedAt)
				.orElseGet(() -> previous.getStartDate().atStartOfDay(this.zone).toInstant()))
			.orElse(null);
		return this.audit.removalsSince(since, task.isPrivilegedReview())
			.stream()
			.map(ReviewPopulations::removedEntry)
			.toList();
	}

	private static PopulationEntryResponse removedEntry(Removal removal) {
		return new PopulationEntryResponse(removal.userId(), removal.username(), removal.name(), removal.department(),
				removal.createdAt(), removal.lastLoginAt(), removal.lastActivityAt(), removal.occurredAt(),
				removal.actor(), removal.reasonCode(), removal.reasonNote());
	}

	private static PopulationStatus status(AccountReviewAttestation attestation) {
		return attestation == null ? new PopulationStatus(false, null, null, null, null)
				: new PopulationStatus(true, attestation.getConfirmedBy(), attestation.getConfirmedAt(),
						attestation.getNote(), attestation.getEntryCount());
	}

}
