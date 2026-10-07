package com.example.commons.security.authentication.passkey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

class PasskeyAuditLoggerTest {

	private final Logger logger = (Logger) LoggerFactory.getLogger(PasskeyAuditLogger.class);

	private final ListAppender<ILoggingEvent> logEvents = new ListAppender<>();

	private final PasskeyAuditLogger auditLogger = new PasskeyAuditLogger();

	@BeforeEach
	void attachAppender() {
		this.logEvents.start();
		this.logger.addAppender(this.logEvents);
	}

	@AfterEach
	void detachAppender() {
		this.logger.detachAppender(this.logEvents);
		this.logEvents.stop();
	}

	@Test
	void logsARegistrationAndARemovalAsChangesToTheOwnersCredentials() {
		this.auditLogger.registered("alice", "laptop");
		this.auditLogger.removed("alice", "laptop");

		assertThat(this.logEvents.list).hasSize(2)
			.allSatisfy(event -> assertThat(event.getKeyValuePairs()).contains(
					new KeyValuePair("event.category", List.of("iam")),
					new KeyValuePair("event.type", List.of("user", "change")),
					new KeyValuePair("event.outcome", "success"), new KeyValuePair("user.target.name", "alice"),
					new KeyValuePair("passkey.label", "laptop")));
		assertThat(this.logEvents.list)
			.extracting(event -> event.getKeyValuePairs()
				.stream()
				.filter(pair -> pair.key.equals("event.action"))
				.findFirst()
				.orElseThrow().value)
			.containsExactly("register_passkey", "remove_passkey");
	}

}
