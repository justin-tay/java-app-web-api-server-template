package com.example.commons.crypto.openpgp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.commons.crypto.CryptoLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** The operations write their ECS log event, without the data or the passphrase. */
class PgpLoggingTest {

	private final ListAppender<ILoggingEvent> events = new ListAppender<>();

	private final Logger logger = (Logger) LoggerFactory.getLogger(CryptoLog.class);

	@BeforeEach
	void attach() {
		this.logger.setLevel(Level.DEBUG);
		this.events.start();
		this.logger.addAppender(this.events);
	}

	@AfterEach
	void detach() {
		this.logger.detachAppender(this.events);
		this.logger.setLevel(null);
	}

	private static String field(ILoggingEvent event, String key) {
		return event.getKeyValuePairs()
			.stream()
			.filter((pair) -> pair.key.equals(key))
			.map((pair) -> String.valueOf(pair.value))
			.findFirst()
			.orElse(null);
	}

	@Test
	void encryptAndDecryptEachLogOneEvent() {
		ByteArrayOutputStream message = new ByteArrayOutputStream();
		Pgp.encrypt(new ByteArrayInputStream("secret data".getBytes(StandardCharsets.UTF_8)), message,
				TestPgpKeys.RSA.publicKeys());
		Pgp.decrypt(new ByteArrayInputStream(message.toByteArray()), new ByteArrayOutputStream(),
				TestPgpKeys.RSA.secretKeys(TestPgpKeys.PASSPHRASE));

		assertThat(this.events.list).extracting((event) -> field(event, "event.action"))
			.containsExactly("encrypt", "decrypt");
		assertThat(this.events.list).allSatisfy((event) -> {
			assertThat(field(event, "crypto.format")).isEqualTo("openpgp");
			assertThat(event.getFormattedMessage()).doesNotContain("secret");
		});
	}

	@Test
	void aWrongPassphraseLogsOneFailureWithTheReasonButNotThePassphrase() {
		ByteArrayOutputStream message = new ByteArrayOutputStream();
		Pgp.encrypt(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)), message,
				TestPgpKeys.RSA.publicKeys());
		this.events.list.clear();

		Throwable failure = catchThrowable(() -> Pgp.decrypt(new ByteArrayInputStream(message.toByteArray()),
				new ByteArrayOutputStream(), TestPgpKeys.RSA.secretKeys("not-the-passphrase".toCharArray())));

		assertThat(failure).isNotNull();
		assertThat(this.events.list).hasSize(1);
		assertThat(field(this.events.list.get(0), "event.reason")).isEqualTo("pin_required");
		assertThat(this.events.list.get(0).getFormattedMessage()).doesNotContain("not-the-passphrase");
	}

}
