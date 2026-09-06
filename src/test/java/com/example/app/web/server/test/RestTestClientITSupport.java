package com.example.app.web.server.test;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Support for integration tests that make requests to an embedded server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class RestTestClientITSupport {

	@LocalServerPort
	private int port;

	protected RestTestClient restTestClient;

	@BeforeEach
	void setUpRestClient() {
		this.restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:" + this.port).build();
	}

}
