package com.example.commons.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

/**
 * An authorized client saved on one instance must be usable on another. Spring Session
 * JDBC stores the session's attributes serialized in the database, so two instances share
 * a session by one writing the serialized attributes and the other reading them back;
 * this test does the same with two repositories that share nothing but those bytes.
 */
class SessionAuthorizedClientRepositoryTest {

	@Test
	void anAuthorizedClientSavedOnOneInstanceIsLoadedOnAnother() throws Exception {
		ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
			.clientId("client-id")
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("https://client.example.test/login/oauth2/code/keycloak")
			.authorizationUri("https://issuer.example.test/authorize")
			.tokenUri("https://issuer.example.test/token")
			.issuerUri("https://issuer.example.test")
			.build();
		Instant issuedAt = Instant.now();
		OAuth2AuthorizedClient saved = new OAuth2AuthorizedClient(registration, "user",
				new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token", issuedAt,
						issuedAt.plusSeconds(300)),
				new OAuth2RefreshToken("refresh-token", issuedAt));
		TestingAuthenticationToken principal = new TestingAuthenticationToken("user", "n/a");

		MockHttpSession sessionOnFirstInstance = new MockHttpSession();
		MockHttpServletRequest firstRequest = new MockHttpServletRequest();
		firstRequest.setSession(sessionOnFirstInstance);
		new HttpSessionOAuth2AuthorizedClientRepository().saveAuthorizedClient(saved, principal, firstRequest,
				new MockHttpServletResponse());

		MockHttpSession sessionOnSecondInstance = new MockHttpSession();
		roundTrip(sessionOnFirstInstance).forEach(sessionOnSecondInstance::setAttribute);
		MockHttpServletRequest secondRequest = new MockHttpServletRequest();
		secondRequest.setSession(sessionOnSecondInstance);
		OAuth2AuthorizedClient loaded = new HttpSessionOAuth2AuthorizedClientRepository()
			.loadAuthorizedClient("keycloak", principal, secondRequest);

		assertThat(loaded).isNotNull();
		assertThat(loaded.getPrincipalName()).isEqualTo("user");
		assertThat(loaded.getAccessToken().getTokenValue()).isEqualTo("access-token");
		assertThat(loaded.getRefreshToken().getTokenValue()).isEqualTo("refresh-token");
		assertThat(loaded.getClientRegistration().getRegistrationId()).isEqualTo("keycloak");
	}

	/**
	 * Serializes and deserializes the session's attributes as the JDBC session repository
	 * does with Java serialization.
	 */
	private static Map<String, Object> roundTrip(MockHttpSession session) throws IOException, ClassNotFoundException {
		Map<String, Object> attributes = new HashMap<>();
		for (String name : Collections.list(session.getAttributeNames())) {
			attributes.put(name, session.getAttribute(name));
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeObject(attributes);
		}
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			@SuppressWarnings("unchecked")
			Map<String, Object> restored = (Map<String, Object>) in.readObject();
			return restored;
		}
	}

}
