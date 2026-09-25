package com.example.commons.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;

class JwksDecryptionKeySelectorTest {

	@TempDir
	Path directory;

	@Test
	@SuppressWarnings("unchecked")
	void selectsThePrivateKeyTheKidNames() throws Exception {
		ECKey first = RefreshingJwksTest.encryptionKey();
		ECKey second = RefreshingJwksTest.encryptionKey();
		JwksDecryptionKeySelector selector = selector(first, second);

		List<Key> keys = (List<Key>) selector.selectJWEKeys(header(second.getKeyID(), EncryptionMethod.A128CBC_HS256),
				null);

		assertThat(keys).containsExactly(second.toECPrivateKey());
	}

	@Test
	void acceptsEveryRfc7518ContentEncryptionMethod() throws Exception {
		ECKey key = RefreshingJwksTest.encryptionKey();
		JwksDecryptionKeySelector selector = selector(key);

		for (EncryptionMethod method : JwksDecryptionKeySelector.CONTENT_ENCRYPTION_METHODS) {
			assertThat(selector.selectJWEKeys(header(key.getKeyID(), method), null)).as(method.getName()).hasSize(1);
		}
		assertThat(JwksDecryptionKeySelector.CONTENT_ENCRYPTION_METHODS).hasSize(6);
	}

	@Test
	void rejectsAnotherKeyManagementAlgorithm() throws Exception {
		ECKey key = RefreshingJwksTest.encryptionKey();
		JwksDecryptionKeySelector selector = selector(key);
		JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.ECDH_ES_A256KW, EncryptionMethod.A128GCM)
			.keyID(key.getKeyID())
			.build();

		assertThat(selector.selectJWEKeys(header, null)).isEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void readsTheJwksAgainForAnUnknownKid() throws Exception {
		ECKey first = RefreshingJwksTest.encryptionKey();
		JwksDecryptionKeySelector selector = selector(first);
		ECKey rotated = RefreshingJwksTest.encryptionKey();
		write("enc.json", first, rotated);

		List<Key> keys = (List<Key>) selector.selectJWEKeys(header(rotated.getKeyID(), EncryptionMethod.A128GCM), null);

		assertThat(keys).containsExactly(rotated.toECPrivateKey());
	}

	private JwksDecryptionKeySelector selector(JWK... encryptionKeys) throws Exception {
		RefreshingJwks jwks = new RefreshingJwks(
				List.of(write("sig.json", RefreshingJwksTest.signatureKey()), write("enc.json", encryptionKeys)),
				Duration.ofHours(1), Clock.systemUTC());
		return new JwksDecryptionKeySelector(jwks);
	}

	private static JWEHeader header(String kid, EncryptionMethod method) {
		return new JWEHeader.Builder(JWEAlgorithm.ECDH_ES_A128KW, method).keyID(kid).build();
	}

	private Resource write(String name, JWK... keys) throws IOException {
		Path path = this.directory.resolve(name);
		Files.writeString(path, new JWKSet(List.of(keys)).toString(false));
		return new FileSystemResource(path);
	}

}
