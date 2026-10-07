package com.example.commons.accounts.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.commons.accounts.Permissions;
import com.example.commons.accounts.AccountsJpaTest;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.ReasonCode;
import com.example.commons.accounts.domain.ReviewPopulation;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.review.ReviewDtos.PopulationEntryResponse;
import com.example.commons.web.problem.ConflictException;

/**
 * Tests the removed population: live until confirmed, frozen after, and the range of the
 * removed population so no removal falls between two reviews.
 */
@AccountsJpaTest
class ReviewPopulationsTest extends AccountReviewTestSupport {

	private static final Instant LATER = Instant.parse("2026-10-20T10:00:00Z");

	@Test
	void confirmingFreezesTheListAndLaterChangesDoNotAlterIt() {
		user("rachel");
		AppUser alice = user("alice");
		AppUser bob = user("bob");
		authenticateAs("admin", Permissions.USER_REMOVE);
		this.lifecycle.remove(alice.getPublicId(), ReasonCode.OTHER, null);
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();

		authenticateAsReviewer("rachel");
		this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.REMOVED, "spot checked one account");
		flushAndClear();

		authenticateAs("admin", Permissions.USER_REMOVE);
		this.lifecycle.remove(bob.getPublicId(), ReasonCode.OTHER, null);
		flushAndClear();

		assertThat(population(task, ReviewPopulation.REMOVED)).extracting(PopulationEntryResponse::username)
			.containsExactly("alice");
		var status = this.populations.status(task);
		assertThat(status.confirmed()).isTrue();
		assertThat(status.confirmedBy()).isEqualTo("rachel");
		assertThat(status.note()).isEqualTo("spot checked one account");
		assertThat(status.count()).isEqualTo(1);
	}

	@Test
	void aPopulationCanBeConfirmedOnlyOnce() {
		user("rachel");
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		authenticateAsReviewer("rachel");

		this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.REMOVED, null);

		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.REMOVED, null));
		assertThat(this.attestations.findByTaskId(task.getId())).hasSize(1);
	}

	@Test
	void theRemovedPopulationOfTheFirstTaskHoldsEveryRecordedRemoval() {
		user("rachel");
		AppUser alice = user("alice", "HR");
		authenticateAs("admin", Permissions.USER_REMOVE);
		this.lifecycle.remove(alice.getPublicId(), ReasonCode.LEFT_ORGANISATION, "resigned");
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();

		List<PopulationEntryResponse> removed = population(task, ReviewPopulation.REMOVED);

		assertThat(removed).hasSize(1);
		assertThat(removed.get(0).username()).isEqualTo("alice");
		assertThat(removed.get(0).department()).isEqualTo("HR");
		assertThat(removed.get(0).createdAt()).isNotNull();
		assertThat(removed.get(0).actor()).isEqualTo("admin");
		assertThat(removed.get(0).reasonCode()).isEqualTo("left_organisation");
		assertThat(removed.get(0).reasonNote()).isEqualTo("resigned");
	}

	@Test
	void theRemovedPopulationStartsWhereThePreviousOneWasConfirmed() {
		user("rachel");
		AppUser early = user("early");
		AppUser late = user("late");
		AppUser later = user("later");
		this.clock.set(Instant.parse("2026-07-10T10:00:00Z"));
		authenticateAs("admin", Permissions.USER_REMOVE);
		this.lifecycle.remove(early.getPublicId(), ReasonCode.OTHER, null);
		Task previous = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW,
				new ReviewPeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));
		flushAndClear();

		this.clock.set(Instant.parse("2026-07-20T10:00:00Z"));
		authenticateAsReviewer("rachel");
		this.service.confirmPopulation(previous.getPublicId(), ReviewPopulation.REMOVED, null);
		flushAndClear();
		assertThat(population(previous, ReviewPopulation.REMOVED)).extracting(PopulationEntryResponse::username)
			.containsExactly("early");

		this.clock.set(Instant.parse("2026-07-25T10:00:00Z"));
		authenticateAs("admin", Permissions.USER_REMOVE);
		this.lifecycle.remove(late.getPublicId(), ReasonCode.OTHER, null);
		this.clock.set(NOW);
		this.lifecycle.remove(later.getPublicId(), ReasonCode.OTHER, null);
		Task current = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();

		assertThat(population(current, ReviewPopulation.REMOVED)).extracting(PopulationEntryResponse::username)
			.containsExactly("late", "later");
	}

	@Test
	void theRemovedPopulationStartsAtThePreviousTaskWhenItWasNeverConfirmed() {
		user("rachel");
		AppUser before = user("before");
		AppUser during = user("during");
		this.clock.set(Instant.parse("2026-06-20T10:00:00Z"));
		authenticateAs("admin", Permissions.USER_REMOVE);
		this.lifecycle.remove(before.getPublicId(), ReasonCode.OTHER, null);
		this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW,
				new ReviewPeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));
		this.clock.set(Instant.parse("2026-07-20T10:00:00Z"));
		this.lifecycle.remove(during.getPublicId(), ReasonCode.OTHER, null);
		this.clock.set(NOW);
		Task current = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();

		assertThat(population(current, ReviewPopulation.REMOVED)).extracting(PopulationEntryResponse::username)
			.containsExactly("during");
	}

	@Test
	void aRemovalMadeThroughTheReviewAppearsInTheRemovedPopulation() {
		user("rachel");
		AppUser alice = user("alice");
		authenticateAsReviewer("rachel");
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();

		this.clock.set(LATER);
		this.service.decide(task.getPublicId(), List.of(itemOf(task, "alice").getPublicId()),
				AccountReviewService.Decision.REMOVE, ReasonCode.NO_LONGER_REQUIRED, null);
		flushAndClear();

		List<PopulationEntryResponse> removed = population(task, ReviewPopulation.REMOVED);
		assertThat(removed).extracting(PopulationEntryResponse::username).containsExactly("alice");
		assertThat(removed.get(0).actor()).isEqualTo("rachel");
		assertThat(removed.get(0).occurredAt()).isEqualTo(LATER);
		assertThat(alice).isNotNull();
	}

	private List<PopulationEntryResponse> population(Task task, ReviewPopulation population) {
		return this.populations.rows(task, population)
			.stream()
			.sorted(Comparator.comparing(PopulationEntryResponse::username))
			.toList();
	}

}
