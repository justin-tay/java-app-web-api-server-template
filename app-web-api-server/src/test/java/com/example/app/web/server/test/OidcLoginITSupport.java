package com.example.app.web.server.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * Support for integration tests that need a session established by a real OIDC
 * authorization code login.
 * <p>
 * A stub OpenID Provider (token and JWKS endpoints only) runs on an ephemeral port for
 * the life of the test JVM, and the {@code keycloak} client registration points at it, so
 * the application performs the whole login, including session-fixation ID rotation,
 * through its own security filter chain. The stub signs ID tokens for the seeded local
 * user {@code test-user}, and, like Keycloak's client configured by
 * {@code bin/configure-keycloak.js}, requires PKCE with {@code S256}: its token endpoint
 * rejects a {@code code_verifier} that does not match the authorization request's
 * {@code code_challenge}.
 */
@Import(OidcLoginITSupport.StubProviderClientRegistrationConfiguration.class)
public abstract class OidcLoginITSupport extends RestTestClientITSupport {

	protected static final String CLIENT_ID = "java-app-web-api-server";

	protected static final String SUBJECT = "0f5b3c1e-provider-subject";

	protected static final String PROVIDER_SESSION_ID = "provider-session-1";

	private static final RSAKey SIGNING_KEY = signingKey();

	private static final AtomicReference<String> NONCE = new AtomicReference<>();

	private static final AtomicReference<String> CODE_CHALLENGE = new AtomicReference<>();

	private static final HttpServer PROVIDER = startProvider();

	protected static final String ISSUER = "http://localhost:" + PROVIDER.getAddress().getPort();

	@Autowired
	protected JdbcTemplate jdbcTemplate;

	@LocalServerPort
	private int port;

	protected final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

	/**
	 * Performs the authorization code flow against the stub provider.
	 * @return the rotated session cookie ({@code id=...}) of the authenticated session
	 * @throws Exception if a request fails
	 */
	protected String login() throws Exception {
		HttpResponse<Void> authorizationResponse = this.client.send(
				HttpRequest.newBuilder(uri("/oauth2/authorization/keycloak")).GET().build(),
				HttpResponse.BodyHandlers.discarding());
		assertThat(authorizationResponse.statusCode()).isEqualTo(HttpStatus.FOUND.value());
		String preLoginCookie = sessionCookie(authorizationResponse);
		Map<String, String> authorizationRequest = queryParameters(
				URI.create(authorizationResponse.headers().firstValue(HttpHeaders.LOCATION).orElseThrow()));
		assertThat(authorizationRequest).containsEntry("code_challenge_method", "S256")
			.containsEntry("redirect_uri", uri("/login/oauth2/code/keycloak").toString())
			.containsKey("code_challenge");
		NONCE.set(authorizationRequest.get("nonce"));
		CODE_CHALLENGE.set(authorizationRequest.get("code_challenge"));

		HttpResponse<Void> callbackResponse = this.client.send(HttpRequest
			.newBuilder(uri("/login/oauth2/code/keycloak?code=test-code&state="
					+ URLEncoder.encode(authorizationRequest.get("state"), StandardCharsets.UTF_8)))
			.header(HttpHeaders.COOKIE, preLoginCookie)
			.GET()
			.build(), HttpResponse.BodyHandlers.discarding());
		assertThat(callbackResponse.statusCode()).isEqualTo(HttpStatus.FOUND.value());
		assertThat(callbackResponse.headers().firstValue(HttpHeaders.LOCATION))
			.hasValueSatisfying(location -> assertThat(location).doesNotContain("error"));
		String sessionCookie = sessionCookie(callbackResponse);
		assertThat(sessionId(sessionCookie)).isNotEqualTo(sessionId(preLoginCookie));
		return sessionCookie;
	}

	protected URI uri(String path) {
		return URI.create("http://localhost:" + this.port + path);
	}

	protected int sessionCount(String sessionId) {
		return this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION WHERE SESSION_ID = ?",
				Integer.class, sessionId);
	}

	protected static String sessionCookie(HttpResponse<?> response) {
		return response.headers()
			.allValues(HttpHeaders.SET_COOKIE)
			.stream()
			.filter(cookie -> cookie.startsWith("id="))
			.findFirst()
			.map(cookie -> cookie.substring(0, cookie.indexOf(';')))
			.orElseThrow();
	}

	/**
	 * Decodes the Spring Session ID carried, Base64-encoded, in an {@code id} cookie.
	 * @param cookie the {@code id=...} cookie
	 * @return the session ID
	 */
	protected static String sessionId(String cookie) {
		return new String(Base64.getDecoder().decode(cookie.substring("id=".length())), StandardCharsets.UTF_8);
	}

	/**
	 * Signs claims with the stub provider's key.
	 * @param type the JOSE {@code typ} header
	 * @param claims the claims
	 * @return the serialized JWT
	 */
	protected static String sign(JOSEObjectType type, JWTClaimsSet claims) {
		try {
			SignedJWT jwt = new SignedJWT(
					new JWSHeader.Builder(JWSAlgorithm.RS256).type(type).keyID(SIGNING_KEY.getKeyID()).build(), claims);
			jwt.sign(new RSASSASigner(SIGNING_KEY));
			return jwt.serialize();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static Map<String, String> queryParameters(URI uri) {
		Map<String, String> parameters = new HashMap<>();
		for (String parameter : uri.getRawQuery().split("&")) {
			int separator = parameter.indexOf('=');
			parameters.put(URLDecoder.decode(parameter.substring(0, separator), StandardCharsets.UTF_8),
					URLDecoder.decode(parameter.substring(separator + 1), StandardCharsets.UTF_8));
		}
		return parameters;
	}

	private static String idToken() {
		Instant now = Instant.now();
		return sign(JOSEObjectType.JWT,
				new JWTClaimsSet.Builder().issuer(ISSUER)
					.subject(SUBJECT)
					.audience(CLIENT_ID)
					.issueTime(Date.from(now))
					.expirationTime(Date.from(now.plusSeconds(300)))
					.claim("nonce", NONCE.get())
					.claim("sid", PROVIDER_SESSION_ID)
					.claim("preferred_username", "test-user")
					.build());
	}

	private static RSAKey signingKey() {
		try {
			return new RSAKeyGenerator(2048).keyUse(KeyUse.SIGNATURE).keyID("stub-provider").generate();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static HttpServer startProvider() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/jwks",
					exchange -> respond(exchange, new JWKSet(SIGNING_KEY.toPublicJWK()).toString()));
			server.createContext("/token", exchange -> {
				String codeVerifier = queryParameters(
						URI.create("?" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)))
					.get("code_verifier");
				if (codeVerifier == null || !codeChallenge(codeVerifier).equals(CODE_CHALLENGE.get())) {
					respond(exchange, HttpStatus.BAD_REQUEST,
							"{\"error\":\"invalid_grant\",\"error_description\":\"PKCE verification failed\"}");
					return;
				}
				respond(exchange,
						"""
								{"access_token":"stub-access-token","token_type":"Bearer","expires_in":300,"scope":"openid","id_token":"%s"}"""
							.formatted(idToken()));
			});
			server.start();
			// Shared by every test class in the JVM, whose cached application contexts
			// all point at it, so it is stopped with the JVM rather than after a class.
			Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
			return server;
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * Computes the {@code S256} code challenge (RFC 7636 section 4.2) for a verifier.
	 * @param codeVerifier the code verifier
	 * @return the base64url-encoded SHA-256 digest of the verifier
	 */
	private static String codeChallenge(String codeVerifier) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void respond(HttpExchange exchange, String json) throws IOException {
		respond(exchange, HttpStatus.OK, json);
	}

	private static void respond(HttpExchange exchange, HttpStatus status, String json) throws IOException {
		byte[] body = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
		exchange.sendResponseHeaders(status.value(), body.length);
		try (OutputStream outputStream = exchange.getResponseBody()) {
			outputStream.write(body);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class StubProviderClientRegistrationConfiguration {

		@Bean
		ClientRegistrationRepository clientRegistrationRepository() {
			return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("keycloak")
				.clientId(CLIENT_ID)
				.clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("{baseUrl}/{action}/oauth2/code/{registrationId}")
				.scope("openid")
				.authorizationUri(ISSUER + "/auth")
				.tokenUri(ISSUER + "/token")
				.jwkSetUri(ISSUER + "/jwks")
				.issuerUri(ISSUER)
				.userNameAttributeName("preferred_username")
				.build());
		}

	}

}
