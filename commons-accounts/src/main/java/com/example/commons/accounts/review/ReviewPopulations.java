package com.example.commons.accounts.review;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import com.example.commons.accounts.domain.AccountAuditEvent;
import com.example.commons.accounts.domain.AccountAuditEventRepository;
import com.example.commons.accounts.domain.AccountReviewAttestation;
import com.example.commons.accounts.domain.AccountReviewAttestationRepository;
import com.example.commons.accounts.domain.AccountReviewPopulationEntry;
import com.example.commons.accounts.domain.AccountReviewPopulationEntryRepository;
import com.example.commons.accounts.domain.AccountStatus;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.accounts.domain.ReviewPopulation;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.domain.TaskRepository;
import com.example.commons.accounts.review.ReviewDtos.PopulationEntryResponse;
import com.example.commons.accounts.review.ReviewDtos.PopulationStatus;
import com.example.commons.accounts.review.ReviewDtos.Populations;
import com.example.commons.web.problem.ConflictException;

/**
 * The suspended and removed populations of an account review task: live until a reviewer
 * confirms one, then frozen as it was when confirmed (see docs/adr/0038). It owns the
 * live queries, the frozen entries and the stored confirmation, so nothing else reads the
 * confirmation tables. It runs inside the caller's transaction.
 */
public class ReviewPopulations {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final TypeReference<Map<String, Object>> DETAILS = new TypeReference<>() {
	};

	private final TaskRepository tasks;

	private final AccountReviewAttestationRepository attestations;

	private final AccountReviewPopulationEntryRepository entries;

	private final AppUserRepository users;

	private final AccountAuditEventRepository auditEvents;

	private final Clock clock;

	private final ZoneId zone;

	public ReviewPopulations(TaskRepository tasks, AccountReviewAttestationRepository attestations,
			AccountReviewPopulationEntryRepository entries, AppUserRepository users,
			AccountAuditEventRepository auditEvents, Clock clock, ZoneId zone) {
		this.tasks = tasks;
		this.attestations = attestations;
		this.entries = entries;
		this.users = users;
		this.auditEvents = auditEvents;
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
						entry.getFullName(), entry.getDepartment(), entry.getLastLoginAt(), entry.getLastActivityAt(),
						entry.getOccurredAt(), entry.getActor(), entry.getReasonCode(), entry.getReasonNote()))
				.toList();
		}
		return population == ReviewPopulation.SUSPENDED ? liveSuspended(task) : liveRemoved(task);
	}

	/**
	 * Returns whether each population of a task has been confirmed, and by whom.
	 * @param task the task
	 * @return the status of both populations
	 */
	public Populations status(Task task) {
		Map<ReviewPopulation, AccountReviewAttestation> attested = new HashMap<>();
		for (AccountReviewAttestation attestation : this.attestations.findByTaskId(task.getId())) {
			attested.put(attestation.getPopulation(), attestation);
		}
		return new Populations(status(attested.get(ReviewPopulation.SUSPENDED)),
				status(attested.get(ReviewPopulation.REMOVED)));
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
					row.department(), row.lastLoginAt(), row.lastActivityAt(), row.occurredAt(), row.actor(),
					row.reasonCode(), row.reasonNote()))
			.toList());
		return rows.size();
	}

	/**
	 * Returns the suspended accounts of the task's class: those whose roles hold a
	 * privileged permission for the privileged review, and the others for the
	 * non-privileged one.
	 */
	private List<PopulationEntryResponse> liveSuspended(Task task) {
		boolean privileged = task.isPrivilegedReview();
		List<AppUser> suspended = this.users.findByStatus(AccountStatus.SUSPENDED)
			.stream()
			.filter(user -> user.isPrivileged() == privileged)
			.toList();
		Map<String, AccountAuditEvent> latest = new HashMap<>();
		if (!suspended.isEmpty()) {
			for (AccountAuditEvent event : this.auditEvents.findByActionAndTargetIdIn("suspend_user",
					suspended.stream().map(user -> user.getPublicId().toString()).toList())) {
				latest.merge(event.getTargetId(), event,
						(first, second) -> second.getOccurredAt().isAfter(first.getOccurredAt()) ? second : first);
			}
		}
		return suspended.stream()
			.map(user -> new PopulationEntryResponse(user.getPublicId(), user.getUsername(), user.getName(),
					user.getDepartment(), user.getLastLoginAt(), user.lastActivityAt(), user.getSuspendedAt(),
					Optional.ofNullable(latest.get(user.getPublicId().toString()))
						.map(AccountAuditEvent::getActor)
						.orElse(null),
					user.getSuspensionReasonCode(), user.getSuspensionNote()))
			.toList();
	}

	/**
	 * Returns the removals since the previous task's removed population was confirmed, or
	 * since that task started if it never was, or every recorded removal for the first
	 * task, so no removal falls between two reviews, and only the removals of the task's
	 * class: whether the account was privileged is recorded with the removal, and a
	 * removal that records nothing counts as not privileged.
	 */
	private List<PopulationEntryResponse> liveRemoved(Task task) {
		Instant since = this.tasks
			.findFirstByTypeAndStartDateBeforeOrderByStartDateDesc(task.getType(), task.getStartDate())
			.map(previous -> this.attestations.findByTaskIdAndPopulation(previous.getId(), ReviewPopulation.REMOVED)
				.map(AccountReviewAttestation::getConfirmedAt)
				.orElseGet(() -> previous.getStartDate().atStartOfDay(this.zone).toInstant()))
			.orElse(null);
		Specification<AccountAuditEvent> specification = (root, query, builder) -> builder
			.and(builder.equal(root.get("action"), "delete_user"), builder.equal(root.get("targetType"), "USER"));
		if (since != null) {
			specification = specification
				.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("occurredAt"), since));
		}
		boolean privileged = task.isPrivilegedReview();
		return this.auditEvents.findAll(specification, Sort.by("occurredAt"))
			.stream()
			.filter(event -> Boolean.TRUE.equals(details(event).get("privileged")) == privileged)
			.map(ReviewPopulations::removedEntry)
			.toList();
	}

	private static Map<String, Object> details(AccountAuditEvent event) {
		return event.getDetails() == null ? Map.of() : JSON.readValue(event.getDetails(), DETAILS);
	}

	private static PopulationEntryResponse removedEntry(AccountAuditEvent event) {
		Map<String, Object> details = details(event);
		Object lastLogin = details.get("lastLoginAt");
		Object lastActivity = details.get("lastActivityAt");
		return new PopulationEntryResponse(UUID.fromString(event.getTargetId()), event.getTargetName(),
				event.getTargetFullName(), (String) details.get("department"),
				lastLogin == null ? null : Instant.parse(lastLogin.toString()),
				lastActivity == null ? null : Instant.parse(lastActivity.toString()), event.getOccurredAt(),
				event.getActor(), event.getReasonCode(), event.getReasonNote());
	}

	private static PopulationStatus status(AccountReviewAttestation attestation) {
		return attestation == null ? new PopulationStatus(false, null, null, null, null)
				: new PopulationStatus(true, attestation.getConfirmedBy(), attestation.getConfirmedAt(),
						attestation.getNote(), attestation.getEntryCount());
	}

}
