package com.example.commons.security.oauth2;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Discovery over TLS with a certificate no one but the test trusts, as a Keycloak behind
 * a private CA is.
 */
class OidcDiscoveryClientTlsTest {

	private TlsTestServer server;

	private String issuer;

	@BeforeEach
	void startServer(@TempDir Path directory) throws Exception {
		this.server = TlsTestServer.start(directory);
		this.issuer = this.server.baseUrl() + "/realms/test";
		this.server.respondWithJson("/realms/test/.well-known/openid-configuration",
				() -> "{\"issuer\":\"" + this.issuer + "\"}");
	}

	@AfterEach
	void stopServer() {
		this.server.close();
	}

	@Test
	void trustsTheCertificateOfItsBundle() {
		OidcDiscoveryClient client = new OidcDiscoveryClient(Duration.ofSeconds(2), Duration.ofSeconds(2),
				issuerUri -> this.server.trustingBundle());

		assertThat(client.fetch(this.issuer)).containsEntry("issuer", this.issuer);
	}

	@Test
	void treatsACertificateTheJvmDoesNotTrustAsDefinitive() {
		OidcDiscoveryClient client = new OidcDiscoveryClient(Duration.ofSeconds(2), Duration.ofSeconds(2));

		assertThatExceptionOfType(DiscoveryException.class).isThrownBy(() -> client.fetch(this.issuer))
			.satisfies(ex -> assertThat(ex.isDefinitive()).isTrue())
			.withMessageContaining("certificate");
	}

}
