package com.example.commons.crypto;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The ECS log events of the operations: stable field names, and nothing from the data,
 * the keys or the exception messages.
 */
class CryptoLogTest {

	private final ListAppender<ILoggingEvent> events = new ListAppender<>();

	private final Logger logger = (Logger) LoggerFactory.getLogger(CryptoLog.class);

	private Level level;

	@BeforeEach
	void attach() {
		this.level = this.logger.getLevel();
		this.logger.setLevel(Level.DEBUG);
		this.events.start();
		this.logger.addAppender(this.events);
	}

	@AfterEach
	void detach() {
		this.logger.detachAppender(this.events);
		this.logger.setLevel(this.level);
	}

	private Map<String, Object> fields(ILoggingEvent event) {
		return event.getKeyValuePairs().stream().collect(Collectors.toMap((pair) -> pair.key, (pair) -> pair.value));
	}

	@Test
	void aSuccessIsADebugEventWithTheEcsFields() {
		String result = CryptoLog.call("decrypt_message", "cms", () -> "plain");

		assertThat(result).isEqualTo("plain");
		assertThat(this.events.list).hasSize(1);
		ILoggingEvent event = this.events.list.get(0);
		assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
		assertThat(fields(event)).containsEntry("event.category", List.of("process"))
			.containsEntry("event.type", List.of("info"))
			.containsEntry("event.action", "decrypt_message")
			.containsEntry("event.outcome", "success")
			.containsEntry("crypto.format", "cms")
			.doesNotContainKey("event.reason");
	}

	@Test
	void aFailureIsAWarningWithAReasonAndTheExceptionTypeButNotItsMessage() {
		assertThatThrownBy(() -> CryptoLog.run("decrypt_message", "openpgp", () -> {
			throw new IntegrityException("secret-looking detail 1234");
		})).isInstanceOf(IntegrityException.class);

		ILoggingEvent event = this.events.list.get(0);
		assertThat(event.getLevel()).isEqualTo(Level.WARN);
		assertThat(fields(event)).containsEntry("event.type", List.of("info"))
			.containsEntry("event.outcome", "failure")
			.containsEntry("event.reason", "integrity_check_failed")
			.containsEntry("error.type", IntegrityException.class.getName());
		assertThat(event.getFormattedMessage()).doesNotContain("1234");
		assertThat(event.getThrowableProxy()).isNull();
	}

	@Test
	void eachKindOfFailureHasItsOwnReason() {
		assertThat(reason(new NoMatchingKeyException("x"))).isEqualTo("no_matching_key");
		assertThat(reason(new PinRequiredException("x"))).isEqualTo("pin_required");
		assertThat(reason(new TokenUnavailableException("x", null))).isEqualTo("token_unavailable");
		assertThat(reason(new CryptoException("x"))).isEqualTo("failed");
		assertThat(reason(new IllegalStateException("x"))).isEqualTo("failed");
	}

	private String reason(RuntimeException failure) {
		this.events.list.clear();
		assertThatThrownBy(() -> CryptoLog.run("sign_message", "cms", () -> {
			throw failure;
		})).isSameAs(failure);
		return (String) fields(this.events.list.get(0)).get("event.reason");
	}

	@Test
	void aVerifyResultWithAnInvalidSignatureIsAFailure() {
		VerifyResult result = new VerifyResult(List.of(new SignatureResult("id", SignatureStatus.INVALID, null)));

		assertThat(CryptoLog.call("verify_signature", "openpgp", () -> result)).isSameAs(result);

		ILoggingEvent event = this.events.list.get(0);
		assertThat(event.getLevel()).isEqualTo(Level.WARN);
		assertThat(fields(event)).containsEntry("event.outcome", "failure")
			.containsEntry("event.reason", "signature_not_valid");
	}

}
