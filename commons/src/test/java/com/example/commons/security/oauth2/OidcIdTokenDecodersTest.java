package com.example.commons.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWEDecryptionKeySelector;
import com.nimbusds.jose.proc.JWEKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * An ID token that arrives encrypted is decrypted and verified, and its token value,
 * which the application sends to the provider as the {@code id_token_hint} at logout, is
 * the signed JWT inside it, not the JWE that only the application can read.
 */
class OidcIdTokenDecodersTest {

	private static final String ISSUER = "https://keycloak.example.com/realms/test";

	private RSAKey signingKey;

	private RSAKey encryptionKey;

	private HttpServer jwksServer;

	private JwtDecoder decoder;

	@BeforeEach
	void setUp() throws Exception {
		this.signingKey = new RSAKeyGenerator(2048).keyUse(KeyUse.SIGNATURE)
			.algorithm(JWSAlgorithm.RS256)
			.keyID("sig")
			.generate();
		this.encryptionKey = new RSAKeyGenerator(2048).keyUse(KeyUse.ENCRYPTION)
			.algorithm(JWEAlgorithm.RSA_OAEP_256)
			.keyID("enc")
			.generate();
		byte[] jwks = new JWKSet(this.signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
		this.jwksServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		this.jwksServer.createContext("/jwks", exchange -> {
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, jwks.length);
			exchange.getResponseBody().write(jwks);
			exchange.close();
		});
		this.jwksServer.start();
		ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
			.clientId("client")
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
			.authorizationUri(ISSUER + "/auth")
			.tokenUri(ISSUER + "/token")
			.issuerUri(ISSUER)
			.jwkSetUri("http://localhost:" + this.jwksServer.getAddress().getPort() + "/jwks")
			.build();
		JWEKeySelector<SecurityContext> keySelector = new JWEDecryptionKeySelector<>(JWEAlgorithm.RSA_OAEP_256,
				EncryptionMethod.A256GCM, new ImmutableJWKSet<>(new JWKSet(this.encryptionKey)));
		this.decoder = OidcIdTokenDecoders.create(registration, new IdTokenDecryption() {
			@Override
			public JWEKeySelector<SecurityContext> keySelector() {
				return keySelector;
			}

			@Override
			public boolean isRequired() {
				return true;
			}
		});
	}

	@AfterEach
	void tearDown() {
		this.jwksServer.stop(0);
	}

	@Test
	void anEncryptedIdTokensTokenValueIsTheSignedJwtInsideIt() throws Exception {
		SignedJWT signed = signedIdToken();

		Jwt jwt = this.decoder.decode(encrypt(signed));

		assertThat(jwt.getTokenValue()).isEqualTo(signed.serialize());
		assertThat(jwt.getTokenValue().chars().filter(c -> c == '.').count()).isEqualTo(2);
		assertThat(jwt.getSubject()).isEqualTo("alice");
		assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", "sig");
	}

	private SignedJWT signedIdToken() throws Exception {
		Instant now = Instant.now();
		SignedJWT signed = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("sig").build(),
				new JWTClaimsSet.Builder().issuer(ISSUER)
					.subject("alice")
					.audience("client")
					.issueTime(Date.from(now))
					.expirationTime(Date.from(now.plusSeconds(300)))
					.build());
		signed.sign(new RSASSASigner(this.signingKey));
		return signed;
	}

	private String encrypt(SignedJWT signed) throws Exception {
		// Nest the signed JWT as the payload, as an OpenID Provider does.
		com.nimbusds.jose.JWEObject nested = new com.nimbusds.jose.JWEObject(
				new JWEHeader.Builder(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM).contentType("JWT")
					.keyID("enc")
					.build(),
				new com.nimbusds.jose.Payload(signed));
		nested.encrypt(new RSAEncrypter(this.encryptionKey.toRSAPublicKey()));
		return nested.serialize();
	}

}
