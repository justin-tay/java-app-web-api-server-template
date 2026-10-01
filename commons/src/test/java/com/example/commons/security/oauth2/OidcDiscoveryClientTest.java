package com.example.commons.security.oauth2;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class OidcDiscoveryClientTest {

	private HttpServer server;

	private String issuer;

	private volatile int status = 200;

	private volatile String body;

	private final OidcDiscoveryClient client = new OidcDiscoveryClient(Duration.ofSeconds(1), Duration.ofSeconds(1));

	@BeforeEach
	void startServer() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.issuer = "http://127.0.0.1:" + this.server.getAddress().getPort() + "/realms/test";
		this.body = "{\"issuer\":\"" + this.issuer + "\"}";
		this.server.createContext("/realms/test/.well-known/openid-configuration", exchange -> {
			byte[] bytes = this.body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(this.status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		this.server.start();
	}

	@AfterEach
	void stopServer() {
		this.server.stop(0);
	}

	@Test
	void fetchesTheDiscoveryDocument() {
		Map<String, Object> metadata = this.client.fetch(this.issuer);

		assertThat(metadata).containsEntry("issuer", this.issuer);
	}

	@Test
	void toleratesATrailingSlashOnTheIssuer() {
		this.body = "{\"issuer\":\"" + this.issuer + "/\"}";

		assertThat(this.client.fetch(this.issuer + "/")).containsEntry("issuer", this.issuer + "/");
	}

	@Test
	void treatsAServerErrorAsTransient() {
		this.status = 503;

		assertThatExceptionOfType(DiscoveryException.class).isThrownBy(() -> this.client.fetch(this.issuer))
			.satisfies(ex -> assertThat(ex.isDefinitive()).isFalse());
	}

	@Test
	void treatsAClientErrorAsDefinitive() {
		this.status = 404;

		assertThatExceptionOfType(DiscoveryException.class).isThrownBy(() -> this.client.fetch(this.issuer))
			.satisfies(ex -> assertThat(ex.isDefinitive()).isTrue());
	}

	@Test
	void treatsAnIssuerMismatchAsDefinitive() {
		this.body = "{\"issuer\":\"https://other.example.test\"}";

		assertThatExceptionOfType(DiscoveryException.class).isThrownBy(() -> this.client.fetch(this.issuer))
			.satisfies(ex -> assertThat(ex.isDefinitive()).isTrue());
	}

	@Test
	void treatsAnUnparseableResponseAsDefinitive() {
		this.body = "<html>not json</html>";

		assertThatExceptionOfType(DiscoveryException.class).isThrownBy(() -> this.client.fetch(this.issuer))
			.satisfies(ex -> assertThat(ex.isDefinitive()).isTrue());
	}

	@Test
	void treatsARefusedConnectionAsTransient() throws IOException {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			closedPort = socket.getLocalPort();
		}

		assertThatExceptionOfType(DiscoveryException.class)
			.isThrownBy(() -> this.client.fetch("http://127.0.0.1:" + closedPort + "/realms/test"))
			.satisfies(ex -> assertThat(ex.isDefinitive()).isFalse());
	}

}
