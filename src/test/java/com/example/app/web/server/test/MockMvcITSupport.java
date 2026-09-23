package com.example.app.web.server.test;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.context.WebApplicationContext;

import com.example.app.web.server.logging.LoggingContextCleanupFilter;
import com.example.app.web.server.logging.RequestCorrelationContextFilter;

/**
 * Support for MVC integration tests that use an in-memory servlet environment.
 *
 * <p>
 * This does not start an embedded server, so it cannot verify container-managed behavior
 * such as emitted session cookies. Use {@link RestTestClientITSupport} for those tests.
 * <p>
 * {@link LoggingContextCleanupFilter} and {@link RequestCorrelationContextFilter} are
 * added explicitly because they are registered as plain top-level
 * {@code FilterRegistrationBean}s (see docs/adr/0012) rather than through
 * {@code HttpSecurity}, and {@code MockMvcTester}/{@code springSecurity()} do not
 * reliably include arbitrary container-level filter registrations the way a real servlet
 * container does.
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
		this.mockMvc = MockMvcTester.from(this.context,
				builder -> builder.addFilter(this.context.getBean(LoggingContextCleanupFilter.class))
					.addFilter(this.context.getBean(RequestCorrelationContextFilter.class))
					.apply(springSecurity())
					.build());
	}

}
