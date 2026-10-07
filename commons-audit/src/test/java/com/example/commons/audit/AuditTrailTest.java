package com.example.commons.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tests the audit trail against its real schema. Each change runs in a transaction that
 * really commits or rolls back, because when the row and the log event are written
 * depends on it; the test's own transaction is therefore disabled and the table is
 * emptied after each test.
 */
@DataJpaTest(properties = { "spring.liquibase.change-log=classpath:db/changelog/audit-test-changelog.yaml",
		"spring.jpa.hibernate.ddl-auto=validate" })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuditTrailTest {

	private static final AuditAction UPDATE_THING = AuditAction.iam("update_thing", "user", "change", "Thing update");

	private static final Instant NOW = Instant.parse("2026-10-08T02:14:07Z");

	@Autowired
	private AuditTrailEventRepository events;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final Logger logger = (Logger) LoggerFactory.getLogger(AuditTrail.class);

	private final ListAppender<ILoggingEvent> logEvents = new ListAppender<>();

	private TransactionTemplate transaction;

	private AuditTrail trail;

	record ThingDetails(String colour, int count) {
	}

	@BeforeEach
	void setUp() {
		this.transaction = new TransactionTemplate(this.transactionManager);
		this.trail = new AuditTrail(this.events, this.transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", null, "x"));
		this.logEvents.start();
		this.logger.addAppender(this.logEvents);
	}

	@AfterEach
	void tearDown() {
		this.logger.detachAppender(this.logEvents);
		this.logEvents.stop();
		SecurityContextHolder.clearContext();
		this.jdbcTemplate.update("DELETE FROM audit_event");
	}

	@Test
	void recordsAChangeAsARowAndALogEventThatNameEachOther() {
		AuditRecord recorded = this.transaction.execute(status -> this.trail.record(event().build()));

		assertThat(this.trail.find(AuditQuery.where())).singleElement().satisfies(record -> {
			assertThat(record.id()).isEqualTo(recorded.id());
			assertThat(record.occurredAt()).isEqualTo(NOW);
			assertThat(record.actor()).isEqualTo("alice");
			assertThat(record.action()).isEqualTo("update_thing");
			assertThat(record.outcome()).isEqualTo(AuditOutcome.SUCCESS);
			assertThat(record.target().name()).isEqualTo("bob");
			assertThat(record.target().fullName()).isEqualTo("Bob Lee");
			assertThat(record.reasonNote()).isEqualTo("see ticket");
			assertThat(record.details(ThingDetails.class)).isEqualTo(new ThingDetails("red", 2));
		});
		assertThat(this.logEvents.list).singleElement().satisfies(log -> {
			assertThat(log.getLevel()).isEqualTo(Level.INFO);
			assertThat(log.getFormattedMessage()).isEqualTo("Thing update");
		});
		assertThat(logged()).contains("event.category=\"[iam]\"")
			.contains("event.type=\"[user, change]\"")
			.contains("event.action=\"update_thing\"")
			.contains("event.outcome=\"success\"")
			.contains("event.reason=\"other\"")
			.contains("event.id=\"" + recorded.id() + "\"")
			.contains("event.created=\"2026-10-08T02:14:07Z\"")
			.contains("user.name=\"alice\"")
			.contains("related.user=\"[alice, bob]\"")
			.contains("user.target.name=\"bob\"");
	}

	@Test
	void keepsTheFullNameTheNoteAndTheDetailsOutOfTheLog() {
		this.transaction.execute(status -> this.trail.record(event().build()));

		assertThat(logged()).doesNotContain("Bob Lee").doesNotContain("see ticket").doesNotContain("red");
	}

	@Test
	void leavesNeitherARowNorALogEventForAChangeThatIsRolledBack() {
		this.transaction.executeWithoutResult(status -> {
			this.trail.record(event().build());
			status.setRollbackOnly();
		});

		assertThat(this.trail.find(AuditQuery.where().anyOutcome())).isEmpty();
		assertThat(this.logEvents.list).isEmpty();
	}

	@Test
	void keepsARefusalWhenTheCallersTransactionRollsBack() {
		AccessDeniedException thrown = this.transaction.execute(status -> {
			AccessDeniedException exception = this.trail.reject(event().reason("own_account").build(),
					() -> new AccessDeniedException("refused"));
			status.setRollbackOnly();
			return exception;
		});

		assertThat(thrown).hasMessage("refused");
		assertThat(this.trail.find(AuditQuery.where())).as("successful events only, by default").isEmpty();
		assertThat(this.trail.find(AuditQuery.where().anyOutcome())).singleElement().satisfies(record -> {
			assertThat(record.action()).isEqualTo("update_thing");
			assertThat(record.outcome()).isEqualTo(AuditOutcome.FAILURE);
			assertThat(record.reasonCode()).isEqualTo("own_account");
		});
		assertThat(this.logEvents.list).singleElement().satisfies(log -> {
			assertThat(log.getLevel()).isEqualTo(Level.WARN);
			assertThat(log.getFormattedMessage()).isEqualTo("Thing update rejected");
		});
		assertThat(logged()).contains("event.outcome=\"failure\"").contains("event.reason=\"own_account\"");
	}

	@Test
	void logsARefusalAtOnceWithoutWaitingForTheCallersCommit() {
		this.transaction.executeWithoutResult(status -> {
			this.trail.reject(event().reason("own_account").build(), () -> new AccessDeniedException("refused"));
			assertThat(this.logEvents.list).hasSize(1);
		});
	}

	@Test
	void needsAReasonToRecordARefusal() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.trail.reject(event().reason(null).build(), IllegalStateException::new));
	}

	@Test
	void refusesLogFieldsThatTheTrailOwnsOrThatNamePersonalData() {
		for (String key : List.of("event.action", "user.name", "related.user", "user.email", "user.full_name",
				"reason.note")) {
			assertThatIllegalArgumentException().as(key).isThrownBy(() -> event().log(key, "x"));
		}
	}

	@Test
	void readsEventsBackByActionTargetAndTime() {
		this.trail.record(event().build());
		this.trail.record(
				AuditEvent
					.of(AuditAction.configuration("update_setting", "Settings update"),
							AuditTarget.of("SETTING", "settings", "settings"))
					.build());

		assertThat(this.trail.find(AuditQuery.where().action("update_thing").targetType("THING")))
			.extracting(AuditRecord::action)
			.containsExactly("update_thing");
		assertThat(this.trail.find(AuditQuery.where().targetIds(List.of("settings")))).extracting(AuditRecord::action)
			.containsExactly("update_setting");
		assertThat(this.trail.find(AuditQuery.where().since(NOW.plusSeconds(1)))).isEmpty();
		assertThat(this.trail.find(AuditQuery.where().ids(List.of()))).isEmpty();
	}

	@Test
	void searchesEventsByTextAndOutcome() {
		this.trail.record(event().build());
		this.trail.reject(event().reason("own_account").build(), IllegalStateException::new);

		assertThat(this.trail.search(new AuditSearch("ALI", null, "bo", "thing", null, null, null),
				PageRequest.of(0, 10, Sort.by("occurredAt"))))
			.hasSize(2);
		assertThat(this.trail.search(new AuditSearch(null, "THING", null, null, AuditOutcome.FAILURE, null, null),
				PageRequest.of(0, 10)))
			.extracting(AuditRecord::reasonCode)
			.containsExactly("own_account");
		assertThat(
				this.trail.search(new AuditSearch(null, null, null, "100%", null, null, null), PageRequest.of(0, 10)))
			.isEmpty();
	}

	@Test
	void readsDetailsAsAMapForAListing() {
		this.trail.record(event().build());

		assertThat(this.trail.find(AuditQuery.where()).get(0).details(Object.class))
			.isEqualTo(Map.of("colour", "red", "count", 2));
	}

	private static AuditEvent.Builder event() {
		return AuditEvent.of(UPDATE_THING, AuditTarget.user("THING", "8d3e5c1a", "bob", "Bob Lee"))
			.reason("other", "see ticket")
			.details(new ThingDetails("red", 2))
			.log("user.target.name", "bob");
	}

	private String logged() {
		return this.logEvents.list.stream()
			.map(event -> event.getLevel() + " " + event.getKeyValuePairs())
			.collect(Collectors.joining("\n"));
	}

}
