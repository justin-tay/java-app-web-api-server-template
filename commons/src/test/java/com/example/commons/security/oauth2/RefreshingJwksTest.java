package com.example.commons.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;

class RefreshingJwksTest {

	private static final Duration REFRESH_INTERVAL = Duration.ofHours(1);

	@TempDir
	Path directory;

	private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

	@Test
	void signsWithTheFirstSigKeyThatHasAPrivatePart() throws Exception {
		ECKey retired = signatureKey();
		ECKey signing = signatureKey();
		ECKey next = signatureKey();
		RefreshingJwks jwks = jwks(write("sig.json", retired.toPublicJWK(), signing, next));

		assertThat(jwks.signingKey()).map(JWK::getKeyID).hasValue(signing.getKeyID());
	}

	@Test
	void publishesEverySigKeyWithoutPrivateParts() throws Exception {
		ECKey retired = signatureKey();
		ECKey signing = signatureKey();
		ECKey next = signatureKey();
		RefreshingJwks jwks = jwks(write("sig.json", retired.toPublicJWK(), signing, next));

		JWKSet published = jwks.publicJwks();

		assertThat(published.getKeys()).extracting(JWK::getKeyID)
			.containsExactly(retired.getKeyID(), signing.getKeyID(), next.getKeyID());
		assertThat(published.getKeys()).noneMatch(JWK::isPrivate);
	}

	@Test
	void publishesEveryEncKeyButTheFirstWhenThereAreThree() throws Exception {
		ECKey oldest = encryptionKey();
		ECKey current = encryptionKey();
		ECKey newest = encryptionKey();
		RefreshingJwks jwks = jwks(write("sig.json", signatureKey()), write("enc.json", oldest, current, newest));

		assertThat(jwks.publicJwks().getKeys()).filteredOn(key -> KeyUse.ENCRYPTION.equals(key.getKeyUse()))
			.extracting(JWK::getKeyID)
			.containsExactly(current.getKeyID(), newest.getKeyID());
		assertThat(jwks.decryptionKeys(oldest.getKeyID())).extracting(JWK::getKeyID).containsExactly(oldest.getKeyID());
	}

	@Test
	void publishesEveryEncKeyWhenThereAreTwo() throws Exception {
		ECKey first = encryptionKey();
		ECKey second = encryptionKey();
		RefreshingJwks jwks = jwks(write("sig.json", signatureKey()), write("enc.json", first, second));

		assertThat(jwks.publicJwks().getKeys()).filteredOn(key -> KeyUse.ENCRYPTION.equals(key.getKeyUse()))
			.extracting(JWK::getKeyID)
			.containsExactly(first.getKeyID(), second.getKeyID());
		assertThat(jwks.hasEncryptionKeys()).isTrue();
	}

	@Test
	void acceptsBothUsesInASingleLocation() throws Exception {
		ECKey signing = signatureKey();
		ECKey encryption = encryptionKey();
		RefreshingJwks jwks = jwks(write("jwks.json", signing, encryption));

		assertThat(jwks.signingKey()).map(JWK::getKeyID).hasValue(signing.getKeyID());
		assertThat(jwks.decryptionKeys(null)).extracting(JWK::getKeyID).containsExactly(encryption.getKeyID());
	}

	@Test
	void hasNoEncryptionKeysWithoutAnEncLocation() throws Exception {
		RefreshingJwks jwks = jwks(write("sig.json", signatureKey()));

		assertThat(jwks.hasEncryptionKeys()).isFalse();
		assertThat(jwks.decryptionKeys(null)).isEmpty();
	}

	@Test
	void acceptsAnEmptyJwksAndReportsTheLocation() throws Exception {
		Resource empty = write("sig.json");
		RefreshingJwks jwks = jwks(empty);

		assertThat(jwks.state().signingKeyId()).isNull();
		assertThat(jwks.state().emptyLocations()).containsExactly(empty.getDescription());
	}

	@Test
	void refreshPicksUpRotatedKeys() throws Exception {
		ECKey first = signatureKey();
		ECKey second = signatureKey();
		Resource location = write("sig.json", first, second);
		RefreshingJwks jwks = jwks(location);
		ECKey third = signatureKey();
		write("sig.json", first.toPublicJWK(), second, third);
		this.clock.advance(Duration.ofMinutes(5));

		assertThat(jwks.refresh()).isTrue();

		assertThat(jwks.signingKey()).map(JWK::getKeyID).hasValue(second.getKeyID());
		assertThat(jwks.state().refreshedAt()).isEqualTo(this.clock.instant());
	}

	@Test
	void failedRefreshKeepsTheLastGoodKeys() throws Exception {
		ECKey signing = signatureKey();
		RefreshingJwks jwks = jwks(write("sig.json", signing));
		Instant firstRead = this.clock.instant();
		Files.writeString(this.directory.resolve("sig.json"), "not a JWKS");
		this.clock.advance(Duration.ofMinutes(5));

		assertThat(jwks.refresh()).isFalse();

		assertThat(jwks.signingKey()).map(JWK::getKeyID).hasValue(signing.getKeyID());
		assertThat(jwks.state().refreshedAt()).isEqualTo(firstRead);
		assertThat(jwks.state().lastRefreshFailure()).isEqualTo(this.clock.instant());
	}

	@Test
	void successfulRefreshClearsTheLastFailure() throws Exception {
		ECKey signing = signatureKey();
		RefreshingJwks jwks = jwks(write("sig.json", signing));
		Files.writeString(this.directory.resolve("sig.json"), "not a JWKS");
		jwks.refresh();
		write("sig.json", signing);

		jwks.refresh();

		assertThat(jwks.state().lastRefreshFailure()).isNull();
	}

	@Test
	void onDemandRefreshIsRateLimited() throws Exception {
		ECKey first = signatureKey();
		RefreshingJwks jwks = jwks(write("sig.json", first));

		assertThat(jwks.refreshNow()).as("the first on-demand refresh").isTrue();
		this.clock.advance(RefreshingJwks.ON_DEMAND_REFRESH_INTERVAL.minusSeconds(1));
		assertThat(jwks.refreshNow()).as("within the rate limit").isFalse();
		this.clock.advance(Duration.ofSeconds(1));
		assertThat(jwks.refreshNow()).as("after the rate limit").isTrue();
	}

	@Test
	void signingKeyReadsTheLocationsAgainWhenThereIsNone() throws Exception {
		RefreshingJwks jwks = jwks(write("sig.json"));
		ECKey signing = signatureKey();
		write("sig.json", signing);

		assertThat(jwks.signingKey()).map(JWK::getKeyID).hasValue(signing.getKeyID());
	}

	@Test
	void decryptionKeysMatchTheKid() throws Exception {
		ECKey first = encryptionKey();
		ECKey second = encryptionKey();
		RefreshingJwks jwks = jwks(write("sig.json", signatureKey()), write("enc.json", first, second));

		assertThat(jwks.decryptionKeys(second.getKeyID())).extracting(JWK::getKeyID).containsExactly(second.getKeyID());
		assertThat(jwks.decryptionKeys("unknown")).isEmpty();
	}

	@Test
	void startupFailsWhenALocationCannotBeRead() {
		Resource missing = new FileSystemResource(this.directory.resolve("missing.json"));

		assertThatIllegalStateException().isThrownBy(() -> jwks(missing))
			.withMessageContaining(missing.getDescription());
	}

	@Test
	void startupFailsWithoutQuotingAnInvalidJwks() throws Exception {
		Files.writeString(this.directory.resolve("sig.json"), "{\"keys\":[{\"d\":\"secret\"");

		assertThatIllegalStateException()
			.isThrownBy(() -> jwks(new FileSystemResource(this.directory.resolve("sig.json"))))
			.withMessageContaining("is not a valid JWKS")
			.withMessageNotContaining("secret");
	}

	@Test
	void rejectsAKeyWithoutAKid() throws Exception {
		ECKey key = new ECKeyGenerator(Curve.P_256).keyUse(KeyUse.SIGNATURE).generate();

		assertThatIllegalStateException().isThrownBy(() -> jwks(write("sig.json", key)))
			.withMessageContaining("without a kid");
	}

	@Test
	void rejectsADuplicateKidAcrossLocations() throws Exception {
		ECKey signing = signatureKey();
		ECKey encryption = new ECKeyGenerator(Curve.P_256).keyUse(KeyUse.ENCRYPTION)
			.algorithm(JWEAlgorithm.ECDH_ES_A128KW)
			.keyID(signing.getKeyID())
			.generate();

		assertThatIllegalStateException()
			.isThrownBy(() -> jwks(write("sig.json", signing), write("enc.json", encryption)))
			.withMessageContaining("a kid another key has");
	}

	@Test
	void rejectsAKeyWithoutAUse() throws Exception {
		ECKey key = new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();

		assertThatIllegalStateException().isThrownBy(() -> jwks(write("sig.json", key)))
			.withMessageContaining("whose use is not");
	}

	@Test
	void rejectsOneUseComingFromTwoLocations() throws Exception {
		assertThatIllegalStateException()
			.isThrownBy(() -> jwks(write("a.json", signatureKey()), write("b.json", signatureKey())))
			.withMessageContaining("each use must come from one location");
	}

	@Test
	void rejectsBothUsesInOneOfSeveralLocations() throws Exception {
		assertThatIllegalStateException()
			.isThrownBy(() -> jwks(write("a.json", signatureKey(), encryptionKey()), write("b.json")))
			.withMessageContaining("each holds one use");
	}

	@Test
	void stopsItsSchedulerWhenStopped() throws Exception {
		RefreshingJwks jwks = jwks(write("sig.json", signatureKey()));

		jwks.start();
		assertThat(jwks.isRunning()).isTrue();
		jwks.stop();

		assertThat(jwks.isRunning()).isFalse();
	}

	private RefreshingJwks jwks(Resource... locations) {
		return new RefreshingJwks(List.of(locations), REFRESH_INTERVAL, this.clock);
	}

	private Resource write(String name, JWK... keys) throws IOException {
		Path path = this.directory.resolve(name);
		Files.writeString(path, new JWKSet(List.of(keys)).toString(false), StandardCharsets.UTF_8);
		return new FileSystemResource(path);
	}

	static ECKey signatureKey() throws JOSEException {
		return new ECKeyGenerator(Curve.P_256).keyUse(KeyUse.SIGNATURE)
			.algorithm(JWSAlgorithm.ES256)
			.keyIDFromThumbprint(true)
			.generate();
	}

	static ECKey encryptionKey() throws JOSEException {
		return new ECKeyGenerator(Curve.P_256).keyUse(KeyUse.ENCRYPTION)
			.algorithm(JWEAlgorithm.ECDH_ES_A128KW)
			.keyIDFromThumbprint(true)
			.generate();
	}

	static final class MutableClock extends Clock {

		private Instant instant;

		MutableClock(Instant instant) {
			this.instant = instant;
		}

		void advance(Duration duration) {
			this.instant = this.instant.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return this.instant;
		}

	}

}
