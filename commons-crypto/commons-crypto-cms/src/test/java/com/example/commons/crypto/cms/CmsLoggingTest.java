package com.example.commons.crypto.cms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.commons.crypto.CryptoLog;
import com.example.commons.crypto.PrivateKeyHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** The operations write their ECS log event, once each, without the data. */
class CmsLoggingTest {

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
	void encryptAndDecryptEachLogOneEvent() throws Exception {
		X509Certificate certificate = TestKeys.certificate("rsa");
		ByteArrayOutputStream message = new ByteArrayOutputStream();
		Cms.encrypt(new ByteArrayInputStream("secret data".getBytes(StandardCharsets.UTF_8)), message, certificate);
		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			Cms.decrypt(new ByteArrayInputStream(message.toByteArray()), new ByteArrayOutputStream(), key, certificate);
		}

		assertThat(this.events.list).extracting((event) -> field(event, "event.action"))
			.containsExactly("encrypt_message", "decrypt_message");
		assertThat(this.events.list).allSatisfy((event) -> {
			assertThat(field(event, "crypto.format")).isEqualTo("cms");
			assertThat(field(event, "event.outcome")).isEqualTo("success");
			assertThat(event.getFormattedMessage()).doesNotContain("secret");
		});
	}

	@Test
	void anAlteredMessageLogsOneFailureWithTheReason() throws Exception {
		X509Certificate certificate = TestKeys.certificate("ec");
		ByteArrayOutputStream message = new ByteArrayOutputStream();
		Cms.encrypt(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)), message, certificate);
		byte[] altered = message.toByteArray();
		altered[altered.length - 10] ^= 1;
		this.events.list.clear();

		try (PrivateKeyHandle key = TestKeys.handle("ec")) {
			assertThat(catchThrowable(() -> Cms.decrypt(new ByteArrayInputStream(altered), new ByteArrayOutputStream(),
					key, certificate)))
				.isNotNull();
		}

		assertThat(this.events.list).hasSize(1);
		assertThat(this.events.list.get(0).getLevel()).isEqualTo(Level.WARN);
		assertThat(field(this.events.list.get(0), "event.reason")).isEqualTo("integrity_check_failed");
	}

	@Test
	void decryptAndVerifyLogsOneEventNotTwo() throws Exception {
		X509Certificate certificate = TestKeys.certificate("rsa");
		ByteArrayOutputStream signed = new ByteArrayOutputStream();
		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			CmsEncryptor.create()
				.signAndEncrypt(SignRequest.builder().signer(key, certificate).build(),
						EncryptRequest.builder().recipients(CmsRecipient.certificate(certificate)).build(),
						new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)), signed);
			this.events.list.clear();

			CmsDecryptor.create()
				.decryptAndVerify(DecryptRequest.builder().key(key, certificate).build(),
						VerifyRequest.builder().certificates(certificate).build(),
						new ByteArrayInputStream(signed.toByteArray()), new ByteArrayOutputStream());
		}

		assertThat(this.events.list).extracting((event) -> field(event, "event.action"))
			.containsExactly("decrypt_and_verify_message");
	}

}
