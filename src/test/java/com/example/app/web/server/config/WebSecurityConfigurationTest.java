package com.example.app.web.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.client.assertj.RestTestClientResponse;

import com.example.app.web.server.test.RestTestClientITSupport;

/**
 * Tests that the WebSecurityConfiguration is properly set.
 */
public class WebSecurityConfigurationTest extends RestTestClientITSupport {

	@Test
	void oidcIdTokenValidatorRejectsUnexpectedIssuerAudienceAndAuthorizedParty() {
		ClientRegistration clientRegistration = clientRegistration();
		Instant now = Instant.now();
		Jwt validIdToken = idToken(now, "https://issuer.example.test", List.of("client-id"), null);
		Jwt unexpectedIssuer = idToken(now, "https://other-issuer.example.test", List.of("client-id"), null);
		Jwt unexpectedAudience = idToken(now, "https://issuer.example.test", List.of("other-client"), null);
		Jwt unexpectedAuthorizedParty = idToken(now, "https://issuer.example.test",
				List.of("client-id", "other-client"), "other-client");

		assertThat(WebSecurityConfiguration.oidcIdTokenValidator(clientRegistration).validate(validIdToken).hasErrors())
			.isFalse();
		assertThat(WebSecurityConfiguration.oidcIdTokenValidator(clientRegistration)
			.validate(unexpectedIssuer)
			.hasErrors()).isTrue();
		assertThat(WebSecurityConfiguration.oidcIdTokenValidator(clientRegistration)
			.validate(unexpectedAudience)
			.hasErrors()).isTrue();
		assertThat(WebSecurityConfiguration.oidcIdTokenValidator(clientRegistration)
			.validate(unexpectedAuthorizedParty)
			.hasErrors()).isTrue();
	}

	@Test
	void unauthenticatedRequestCreatesIdSessionCookie() {
		assertThat(RestTestClientResponse.from(this.restTestClient.get().uri("/account").exchange()))
			.hasStatus3xxRedirection()
			.cookies()
			.containsCookie("id")
			.doesNotContainCookie("JSESSIONID");
	}

	@Test
	void idSessionCookieIsNonPersistentHttpOnlyAndSameSiteLax() {
		assertThat(RestTestClientResponse.from(this.restTestClient.get().uri("/account").exchange()))
			.hasStatus3xxRedirection()
			.cookies()
			.hasCookieSatisfying("id", cookie -> {
				assertThat(cookie.getMaxAge()).isEqualTo(-1);
				assertThat(cookie.isHttpOnly()).isTrue();
				assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
			});
	}

	@Test
	void csrfFailureReturnsActionableProblemDetail() {
		assertThat(RestTestClientResponse.from(this.restTestClient.post().uri("/account").exchange()))
			.hasStatus(HttpStatus.FORBIDDEN)
			.hasContentTypeCompatibleWith("application/problem+json")
			.bodyJson()
			.isLenientlyEqualTo("""
					{
					  "type": "urn:problem:csrf-validation-failed",
					  "title": "Forbidden",
					  "status": 403,
					  "detail": "The request could not be verified. Refresh the page and try again."
					}
					""");
	}

	@Test
	void responseHasContentSecurityPolicy() {
		assertThat(RestTestClientResponse.from(this.restTestClient.get().uri("/oauth2/jwks").exchange())).hasStatusOk()
			.hasHeader("Content-Security-Policy",
					"base-uri 'none';default-src 'none';form-action 'none';frame-ancestors 'none'");
	}

	@Test
	void responseHasReferrerAndPermissionsPolicies() {
		assertThat(RestTestClientResponse.from(this.restTestClient.get().uri("/oauth2/jwks").exchange())).hasStatusOk()
			.hasHeader("Referrer-Policy", "strict-origin-when-cross-origin")
			.hasHeader("Permissions-Policy", "camera=(), geolocation=(), microphone=(), payment=(), usb=()");
	}

	private ClientRegistration clientRegistration() {
		return ClientRegistration.withRegistrationId("test")
			.clientId("client-id")
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("https://client.example.test/login/oauth2/code/test")
			.authorizationUri("https://issuer.example.test/authorize")
			.tokenUri("https://issuer.example.test/token")
			.jwkSetUri("https://issuer.example.test/jwks")
			.issuerUri("https://issuer.example.test")
			.build();
	}

	private Jwt idToken(Instant now, String issuer, List<String> audience, String authorizedParty) {
		Jwt.Builder builder = Jwt.withTokenValue("id-token")
			.header("alg", "RS256")
			.issuer(issuer)
			.subject("subject")
			.audience(audience)
			.issuedAt(now.minusSeconds(1))
			.expiresAt(now.plusSeconds(60));
		if (authorizedParty != null) {
			builder.claim("azp", authorizedParty);
		}
		return builder.build();
	}

}
