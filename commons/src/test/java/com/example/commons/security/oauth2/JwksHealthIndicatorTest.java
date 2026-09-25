package com.example.commons.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;

class JwksHealthIndicatorTest {

	@TempDir
	Path directory;

	@Test
	void upWithASigningKeyAndReportsKeyIdsOnly() throws Exception {
		ECKey signing = RefreshingJwksTest.signatureKey();
		ECKey encryption = RefreshingJwksTest.encryptionKey();

		Health health = health(write("sig.json", signing), write("enc.json", encryption));

		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("signingKeyId", signing.getKeyID())
			.containsEntry("signatureKeys", 1)
			.containsEntry("encryptionKeys", 1)
			.containsKey("refreshedAt")
			.doesNotContainKeys("reason", "emptyLocations", "lastRefreshFailure");
		assertThat(health.getDetails().toString()).doesNotContain(signing.getD().toString(),
				encryption.getD().toString());
	}

	@Test
	void downWithoutASigKeyWithAPrivatePart() throws Exception {
		Health health = health(write("sig.json", RefreshingJwksTest.signatureKey().toPublicJWK()));

		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsKey("reason").doesNotContainKey("signingKeyId");
	}

	@Test
	void downWhileALocationHasNoKeys() throws Exception {
		Resource encryption = write("enc.json");

		Health health = health(write("sig.json", RefreshingJwksTest.signatureKey()), encryption);

		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsEntry("emptyLocations", List.of(encryption.getDescription()));
	}

	@Test
	void reportsTheLastRefreshFailure() throws Exception {
		RefreshingJwks jwks = jwks(write("sig.json", RefreshingJwksTest.signatureKey()));
		Files.writeString(this.directory.resolve("sig.json"), "not a JWKS");
		jwks.refresh();

		Health health = new JwksHealthIndicator(jwks).health();

		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsKey("lastRefreshFailure");
	}

	private Health health(Resource... locations) {
		return new JwksHealthIndicator(jwks(locations)).health();
	}

	private RefreshingJwks jwks(Resource... locations) {
		return new RefreshingJwks(List.of(locations), Duration.ofHours(1), Clock.systemUTC());
	}

	private Resource write(String name, JWK... keys) throws IOException {
		Path path = this.directory.resolve(name);
		Files.writeString(path, new JWKSet(List.of(keys)).toString(false));
		return new FileSystemResource(path);
	}

}
