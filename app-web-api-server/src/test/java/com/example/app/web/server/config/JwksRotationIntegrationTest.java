package com.example.app.web.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.example.app.web.server.test.OidcLoginITSupport;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;

/**
 * Integration test for rotated {@code enc} keys (see docs/adr/0020), with the JWKS in two
 * files laid out as {@code cdk-jwks-secret} lays out its two secrets: {@code sig} keys in
 * one, and three {@code enc} keys in the other, of which all but the first are published.
 * When the identity provider encrypts to a key published by a rotation the application
 * has not read yet, the application reads its JWKS again and decrypts.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JwksRotationIntegrationTest extends OidcLoginITSupport {

	private static final Path DIRECTORY = createDirectory();

	private static final Path SIGNATURE_JWKS = DIRECTORY.resolve("sig.json");

	private static final Path ENCRYPTION_JWKS = DIRECTORY.resolve("enc.json");

	private static final ECKey OLDEST = encryptionKey();

	private static final ECKey CURRENT = encryptionKey();

	private static final ECKey NEWEST = encryptionKey();

	@DynamicPropertySource
	static void jwksLocations(DynamicPropertyRegistry registry) {
		write(SIGNATURE_JWKS, testJwksKeys(KeyUse.SIGNATURE));
		write(ENCRYPTION_JWKS, List.of(OLDEST, CURRENT, NEWEST));
		registry.add("commons.security.oauth2.jwks[0]", () -> SIGNATURE_JWKS.toUri().toString());
		registry.add("commons.security.oauth2.jwks[1]", () -> ENCRYPTION_JWKS.toUri().toString());
	}

	@Test
	@Order(1)
	void publishesEveryEncKeyButTheFirstOfThree() {
		assertThat(publishedEncryptionKeyIds()).containsExactly(CURRENT.getKeyID(), NEWEST.getKeyID());
	}

	@Test
	@Order(2)
	void readsTheJwksAgainForAnIdTokenEncryptedToAKeyItHasNotReadYet() throws Exception {
		ECKey rotated = encryptionKey();
		write(ENCRYPTION_JWKS, List.of(CURRENT, NEWEST, rotated));
		encryptIdTokensTo(rotated.toPublicJWK());

		String sessionCookie = login();

		assertThat(sessionCount(sessionId(sessionCookie))).isEqualTo(1);
		assertThat(publishedEncryptionKeyIds()).containsExactly(NEWEST.getKeyID(), rotated.getKeyID());
	}

	private List<String> publishedEncryptionKeyIds() {
		String body = this.restTestClient.get()
			.uri("/oauth2/jwks")
			.exchange()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		try {
			return JWKSet.parse(body)
				.getKeys()
				.stream()
				.filter(key -> KeyUse.ENCRYPTION.equals(key.getKeyUse()))
				.map(JWK::getKeyID)
				.toList();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static List<JWK> testJwksKeys(KeyUse use) {
		try (InputStream inputStream = new ClassPathResource("jwks.json").getInputStream()) {
			return JWKSet.load(inputStream).getKeys().stream().filter(key -> use.equals(key.getKeyUse())).toList();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void write(Path path, List<? extends JWK> keys) {
		try {
			Files.writeString(path, new JWKSet(List.copyOf(keys)).toString(false));
			path.toFile().deleteOnExit();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static ECKey encryptionKey() {
		try {
			return new ECKeyGenerator(Curve.P_256).keyUse(KeyUse.ENCRYPTION)
				.algorithm(JWEAlgorithm.ECDH_ES_A128KW)
				.keyIDFromThumbprint(true)
				.generate();
		}
		catch (JOSEException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static Path createDirectory() {
		try {
			Path directory = Files.createTempDirectory("jwks-rotation");
			directory.toFile().deleteOnExit();
			return directory;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
