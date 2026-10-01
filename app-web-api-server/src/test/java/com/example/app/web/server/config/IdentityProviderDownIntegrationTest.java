package com.example.app.web.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.example.app.web.server.test.RestTestClientITSupport;
import com.example.commons.security.oauth2.OidcDiscoveryHealthIndicator;

/**
 * Tests that the application starts while Keycloak is not listening, answers login with
 * 503 and a {@code Retry-After} header, stays live but not ready, and recovers without a
 * restart once Keycloak is up (see docs/adr/0029).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IdentityProviderDownIntegrationTest extends RestTestClientITSupport {

	private static final int KEYCLOAK_PORT = freePort();

	private static final String ISSUER = "http://127.0.0.1:" + KEYCLOAK_PORT + "/realms/test";

	private static HttpServer keycloak;

	@DynamicPropertySource
	static void issuer(DynamicPropertyRegistry registry) {
		registry.add("test.oauth2-client.static-registration", () -> "false");
		registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri", () -> ISSUER);
		registry.add("commons.security.oauth2.discovery.retry-interval", () -> "1s");
	}

	@LocalServerPort
	private int port;

	@Autowired
	private ApplicationAvailability availability;

	@Autowired
	private OidcDiscoveryHealthIndicator oidcDiscovery;

	@AfterAll
	static void stopKeycloak() {
		if (keycloak != null) {
			keycloak.stop(0);
		}
	}

	@Test
	@Order(1)
	void startsAndIsLiveButNotReadyWhileKeycloakIsDown() {
		assertThat(this.availability.getLivenessState()).isEqualTo(LivenessState.CORRECT);
		assertThat(this.oidcDiscovery.health().getStatus()).isEqualTo(Status.DOWN);
	}

	@Test
	@Order(2)
	void answersLoginWithServiceUnavailableAndRetryAfterWhileKeycloakIsDown() throws Exception {
		HttpResponse<String> response = get("/oauth2/authorization/keycloak");

		assertThat(response.statusCode()).isEqualTo(503);
		assertThat(response.headers().firstValue(HttpHeaders.RETRY_AFTER)).isPresent();
		assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElseThrow())
			.startsWith("application/problem+json");
		assertThat(response.body()).contains("urn:problem:identity-provider-unavailable");
	}

	@Test
	@Order(3)
	void recoversWithoutARestartOnceKeycloakIsUp() throws Exception {
		startKeycloak();

		await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
			HttpResponse<String> response = get("/oauth2/authorization/keycloak");
			assertThat(response.statusCode()).isEqualTo(302);
			assertThat(response.headers().firstValue(HttpHeaders.LOCATION).orElseThrow())
				.startsWith(ISSUER + "/protocol/openid-connect/auth");
		});
		assertThat(this.oidcDiscovery.health().getStatus()).isEqualTo(Status.UP);
	}

	private HttpResponse<String> get(String path) throws Exception {
		return HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER)
			.build()
			.send(HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + path)).GET().build(),
					HttpResponse.BodyHandlers.ofString());
	}

	private static void startKeycloak() throws IOException {
		String metadata = """
				{"issuer":"%1$s","authorization_endpoint":"%1$s/protocol/openid-connect/auth",
				"token_endpoint":"%1$s/protocol/openid-connect/token","jwks_uri":"%1$s/protocol/openid-connect/certs",
				"response_types_supported":["code"],"subject_types_supported":["public"],
				"id_token_signing_alg_values_supported":["RS256"],
				"token_endpoint_auth_methods_supported":["private_key_jwt"]}
				""".formatted(ISSUER);
		keycloak = HttpServer.create(new InetSocketAddress("127.0.0.1", KEYCLOAK_PORT), 0);
		keycloak.createContext("/realms/test/.well-known/openid-configuration", exchange -> {
			byte[] body = metadata.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		keycloak.start();
	}

	private static int freePort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
