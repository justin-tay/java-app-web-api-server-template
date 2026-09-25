package com.example.app.web.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.assertj.RestTestClientResponse;
import org.springframework.boot.test.web.server.LocalManagementPort;

import com.example.app.web.server.test.RestTestClientITSupport;
import com.example.commons.web.tomcat.TomcatHardeningAutoConfiguration;

/**
 * Tests that actuator is reachable on the separate management port only, exposes nothing
 * beyond a minimal, unauthenticated health check there, and has no endpoint mapping on
 * the application port even though the two ports share one security filter chain. See
 * docs/adr/0014-actuator-management-port.md for the rationale.
 */
class ActuatorManagementPortTest extends RestTestClientITSupport {

	@LocalManagementPort
	private int managementPort;

	private RestTestClient managementRestTestClient;

	@BeforeEach
	void setUpManagementRestClient() {
		this.managementRestTestClient = RestTestClient.bindToServer()
			.baseUrl("http://localhost:" + this.managementPort)
			.build();
	}

	@Test
	void healthIsReachableOnTheManagementPortWithoutAuthenticationAndHidesDetails() {
		RestTestClientResponse response = RestTestClientResponse
			.from(this.managementRestTestClient.get().uri("/app/health").exchange());

		assertThat(response).hasStatus(HttpStatus.OK).bodyJson().isLenientlyEqualTo("""
				{
				  "status": "UP"
				}
				""");
	}

	/**
	 * The readiness group includes the {@code jwks} contributor, which is UP once the
	 * JWKS has a signing key (see docs/adr/0020); both probes are unauthenticated.
	 */
	@Test
	void livenessAndReadinessProbesAreReachableWithoutAuthentication() {
		assertThat(
				RestTestClientResponse.from(this.managementRestTestClient.get().uri("/app/health/liveness").exchange()))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.isLenientlyEqualTo("""
					{
					  "status": "UP"
					}
					""");
		assertThat(RestTestClientResponse
			.from(this.managementRestTestClient.get().uri("/app/health/readiness").exchange())).hasStatus(HttpStatus.OK)
			.bodyJson()
			.isLenientlyEqualTo("""
					{
					  "status": "UP"
					}
					""");
	}

	/**
	 * Endpoints outside {@code management.endpoints.web.exposure.include} are also not
	 * permitted by {@code WebSecurityConfiguration}, so an unauthenticated request is
	 * turned away by the {@code authenticated()} rule before it can reach the (also
	 * absent) endpoint mapping.
	 */
	@Test
	void endpointsNotOnTheExposureListAreNotAvailableOnTheManagementPort() {
		RestTestClientResponse response = RestTestClientResponse.from(this.managementRestTestClient.get()
			.uri("/app/env")
			.header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
			.exchange());

		assertThat(response).hasStatus3xxRedirection();
	}

	/**
	 * The management port is a separate embedded server that nonetheless shares one
	 * Spring Security filter chain with the application (see docs/adr/0014), so
	 * {@code /actuator/health} is also permitted there on the application port. It still
	 * is not reachable there: actuator's endpoint mapping only activates for requests
	 * arriving on the management server, so the application port falls through to an
	 * ordinary 404.
	 */
	@Test
	void actuatorIsNotServedOnTheApplicationPort() {
		RestTestClientResponse response = RestTestClientResponse
			.from(this.restTestClient.get().uri("/app/health").exchange());

		assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
	}

	/**
	 * The management port is a separate embedded Tomcat instance (see docs/adr/0014), so
	 * {@link TomcatHardeningAutoConfiguration}'s connector hardening is not applied to it
	 * merely by being applied to the application's connector; it must hold independently.
	 * Verified at the HTTP level, since the two are distinct {@code TomcatWebServer}s and
	 * the management one is not reachable from a test wired to the main context.
	 */
	@Test
	void doesNotDiscloseServerInformationOnTheManagementPort() {
		RestTestClientResponse response = RestTestClientResponse
			.from(this.managementRestTestClient.get().uri("/app/health").exchange());

		assertThat(response.getExchangeResult().getResponseHeaders().get(HttpHeaders.SERVER)).isNull();
	}

}
