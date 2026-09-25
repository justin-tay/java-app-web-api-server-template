package com.example.commons.security.oauth2;

import java.security.Key;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.proc.JWEKeySelector;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * Selects the private key that decrypts an encrypted ID token: the {@code enc} key of the
 * {@link RefreshingJwks} whose {@code kid} the JWE header names and whose {@code alg} is
 * the header's key management algorithm. So the key management algorithm is whatever the
 * configured keys are for, and the content encryption may be any of the RFC 7518 methods.
 *
 * <p>
 * A rotation publishes a new {@code enc} key straight away, so the identity provider may
 * encrypt to it before this application has read it. When no key matches, the JWKS is
 * read again once, subject to the rate limit of {@link RefreshingJwks#refreshNow()},
 * before the JWE is rejected.
 */
class JwksDecryptionKeySelector implements JWEKeySelector<SecurityContext> {

	/**
	 * The content encryption methods of RFC 7518 section 5.1.
	 */
	static final Set<EncryptionMethod> CONTENT_ENCRYPTION_METHODS = Set.of(EncryptionMethod.A128CBC_HS256,
			EncryptionMethod.A192CBC_HS384, EncryptionMethod.A256CBC_HS512, EncryptionMethod.A128GCM,
			EncryptionMethod.A192GCM, EncryptionMethod.A256GCM);

	private final RefreshingJwks jwks;

	JwksDecryptionKeySelector(RefreshingJwks jwks) {
		this.jwks = jwks;
	}

	@Override
	public List<? extends Key> selectJWEKeys(JWEHeader header, SecurityContext context) throws KeySourceException {
		if (!CONTENT_ENCRYPTION_METHODS.contains(header.getEncryptionMethod())) {
			return List.of();
		}
		List<JWK> keys = matching(header);
		if (keys.isEmpty() && this.jwks.refreshNow()) {
			keys = matching(header);
		}
		List<Key> privateKeys = new ArrayList<>();
		for (JWK key : keys) {
			privateKeys.add(privateKey(key));
		}
		return privateKeys;
	}

	private List<JWK> matching(JWEHeader header) {
		return this.jwks.decryptionKeys(header.getKeyID())
			.stream()
			.filter(key -> header.getAlgorithm().equals(key.getAlgorithm()))
			.toList();
	}

	private static Key privateKey(JWK key) throws KeySourceException {
		try {
			if (key instanceof ECKey ecKey) {
				return ecKey.toECPrivateKey();
			}
			if (key instanceof RSAKey rsaKey) {
				return rsaKey.toRSAPrivateKey();
			}
		}
		catch (JOSEException ex) {
			throw new KeySourceException("Unable to convert enc key " + key.getKeyID(), ex);
		}
		throw new KeySourceException("Unsupported enc key type " + key.getKeyType() + " for " + key.getKeyID());
	}

}
