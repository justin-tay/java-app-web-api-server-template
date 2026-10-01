package com.example.commons.security.oauth2;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties.Provider;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties.Registration;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;

/**
 * Every call to a provider with an {@code ssl-bundle} trusts that bundle's certificate,
 * and the same call to a provider without one does not, as the JVM's default trust does
 * not include the test server's certificate. The two registrations point at the same
 * server, so only the provider's bundle differs.
 */
class ProviderTrustRoutingTest {

	private static final String TRUSTED = "trusted";

	private static final String UNTRUSTED = "untrusted";

	private TlsTestServer server;

	private ProviderTrust trust;

	private RSAKey signingKey;

	@BeforeEach
	void setUp(@TempDir Path directory) throws Exception {
		this.server = TlsTestServer.start(directory);
		this.signingKey = new RSAKeyGenerator(2048).keyUse(KeyUse.SIGNATURE)
			.algorithm(JWSAlgorithm.RS256)
			.keyID("sig")
			.generate();
		this.server.respondWithJson("/token",
				() -> "{\"access_token\":\"the-token\",\"token_type\":\"Bearer\",\"expires_in\":300}");
		this.server.respondWithJson("/userinfo", () -> "{\"sub\":\"alice\"}");
		this.server.respondWithJson("/jwks", () -> new JWKSet(this.signingKey.toPublicJWK()).toString());

		OAuth2ClientProperties clientProperties = new OAuth2ClientProperties();
		for (String id : new String[] { TRUSTED, UNTRUSTED }) {
			clientProperties.getProvider().put(id, new Provider());
			Registration registration = new Registration();
			registration.setClientId("client");
			clientProperties.getRegistration().put(id, registration);
		}
		OAuth2ClientProviderProperties properties = new OAuth2ClientProviderProperties();
		OAuth2ClientProviderProperties.Provider provider = new OAuth2ClientProviderProperties.Provider();
		provider.setSslBundle("private-ca");
		properties.getProvider().put(TRUSTED, provider);
		this.trust = new ProviderTrust(clientProperties, properties,
				new DefaultSslBundleRegistry("private-ca", this.server.trustingBundle()));
	}

	@AfterEach
	void stopServer() {
		this.server.close();
	}

	@Test
	void theTokenClientTrustsTheBundleOfTheRegistrationsProvider() {
		var client = this.trust.accessTokenResponseClient(c -> {
		});

		assertThat(client.getTokenResponse(tokenRequest(TRUSTED)).getAccessToken().getTokenValue())
			.isEqualTo("the-token");
		assertThatException().isThrownBy(() -> client.getTokenResponse(tokenRequest(UNTRUSTED)));
	}

	@Test
	void theTokenClientAppliesItsCustomizerToTheClientItBuildsForABundle() {
		int[] customized = { 0 };
		var client = this.trust.accessTokenResponseClient(c -> customized[0]++);

		client.getTokenResponse(tokenRequest(TRUSTED));

		// the default client, and the one built for the provider with a bundle
		assertThat(customized[0]).isEqualTo(2);
	}

	@Test
	void theUserServiceTrustsTheBundleOfTheRegistrationsProvider() {
		var service = this.trust.userService();

		assertThat(service.loadUser(userRequest(TRUSTED)).getName()).isEqualTo("alice");
		assertThatException().isThrownBy(() -> service.loadUser(userRequest(UNTRUSTED)));
	}

	@Test
	void theIdTokenDecoderTrustsTheBundleItIsGiven() throws Exception {
		String idToken = idToken().serialize();

		JwtDecoder trusting = OidcIdTokenDecoders.create(registration(TRUSTED), null,
				this.trust.forRegistration(TRUSTED).orElseThrow());
		JwtDecoder notTrusting = OidcIdTokenDecoders.create(registration(UNTRUSTED), null,
				this.trust.forRegistration(UNTRUSTED).orElse(null));

		assertThat(trusting.decode(idToken).getSubject()).isEqualTo("alice");
		assertThatException().isThrownBy(() -> notTrusting.decode(idToken));
	}

	private ClientRegistration registration(String registrationId) {
		return ClientRegistration.withRegistrationId(registrationId)
			.clientId("client")
			.clientSecret("secret")
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
			.authorizationUri(this.server.baseUrl() + "/auth")
			.tokenUri(this.server.baseUrl() + "/token")
			.userInfoUri(this.server.baseUrl() + "/userinfo")
			.userNameAttributeName("sub")
			.issuerUri("https://issuer.example.test")
			.jwkSetUri(this.server.baseUrl() + "/jwks")
			.build();
	}

	private OAuth2AuthorizationCodeGrantRequest tokenRequest(String registrationId) {
		OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
			.authorizationUri(this.server.baseUrl() + "/auth")
			.clientId("client")
			.redirectUri("https://app.example.test/callback")
			.state("state")
			.build();
		OAuth2AuthorizationResponse authorizationResponse = OAuth2AuthorizationResponse.success("code")
			.redirectUri("https://app.example.test/callback")
			.state("state")
			.build();
		return new OAuth2AuthorizationCodeGrantRequest(registration(registrationId),
				new OAuth2AuthorizationExchange(authorizationRequest, authorizationResponse));
	}

	private OAuth2UserRequest userRequest(String registrationId) {
		OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "the-token",
				Instant.now(), Instant.now().plusSeconds(300));
		return new OAuth2UserRequest(registration(registrationId), accessToken);
	}

	private SignedJWT idToken() throws Exception {
		Instant now = Instant.now();
		SignedJWT signed = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("sig").build(),
				new JWTClaimsSet.Builder().issuer("https://issuer.example.test")
					.subject("alice")
					.audience("client")
					.issueTime(Date.from(now))
					.expirationTime(Date.from(now.plusSeconds(300)))
					.build());
		signed.sign(new RSASSASigner(this.signingKey));
		return signed;
	}

}
