package com.example.app.web.server.test;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.context.WebApplicationContext;

/**
 * Support for MVC integration tests that use an in-memory servlet environment.
 *
 * <p>
 * This does not start an embedded server, so it cannot verify container-managed behavior
 * such as emitted session cookies. Use {@link RestTestClientITSupport} for those tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class MockMvcITSupport {

	@Autowired
	private WebApplicationContext context;

	protected MockMvcTester mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcTester.from(this.context, builder -> builder.apply(springSecurity()).build());
	}

}
