package com.example.app.web.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.example.app.web.server.test.RestTestClientITSupport;

/**
 * Tests that nothing on the application port is reachable without authentication except
 * the paths deliberately opened, so that a missing final
 * {@code anyRequest().authenticated()} rule, an overly broad {@code permitAll()}, or a
 * new controller that is public by accident fails the build.
 *
 * <p>
 * Every path is probed with an anonymous {@code GET} that accepts JSON. The application's
 * authorization rules are path-based, so a protected path answers {@code 401} from the
 * security filter chain before Spring MVC routing, while a path that passed authorization
 * answers whatever Spring MVC does, such as {@code 404} or {@code 405}. Probing a
 * {@code POST}, {@code PUT}, or {@code DELETE} mapping with its own method would prove
 * nothing, because CSRF protection rejects it with {@code 403} before authorization runs.
 * Role checks on authenticated requests are covered by the tests of each rule and each
 * {@code @PreAuthorize}.
 */
class AnonymousAccessIntegrationTest extends RestTestClientITSupport {

	/**
	 * Controller paths deliberately reachable without authentication. Adding a path here
	 * makes it public, so it belongs in code review. The Actuator health endpoint is on
	 * the separate management port and covered by {@code ActuatorManagementPortTest}.
	 */
	private static final Set<String> ANONYMOUS_PATHS = Set.of("/oauth2/jwks");

	private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

	@LocalServerPort
	private int port;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Test
	void deniesAnUnmappedPathBeforeRouting() throws Exception {
		assertThat(anonymousGet("/deny-by-default-probe/" + UUID.randomUUID()))
			.as("an unmapped path must be denied (401), not routed (404)")
			.isEqualTo(HttpStatus.UNAUTHORIZED.value());
	}

	@Test
	void everyControllerPathRejectsAnonymousRequestsUnlessAllowed() throws Exception {
		Set<String> paths = controllerPaths();
		assertThat(paths).as("the allowed anonymous paths must still exist").containsAll(ANONYMOUS_PATHS);

		Map<String, Integer> reachable = new TreeMap<>();
		for (String path : paths) {
			if (ANONYMOUS_PATHS.contains(path)) {
				continue;
			}
			int status = anonymousGet(concrete(path));
			if (status != HttpStatus.UNAUTHORIZED.value()) {
				reachable.put(path, status);
			}
		}

		assertThat(reachable).as("controller paths reachable without authentication").isEmpty();
	}

	@Test
	void allowedAnonymousPathsAreReachable() throws Exception {
		for (String path : ANONYMOUS_PATHS) {
			assertThat(anonymousGet(path)).as(path).isEqualTo(HttpStatus.OK.value());
		}
	}

	private Set<String> controllerPaths() {
		Set<String> paths = new TreeSet<>();
		this.handlerMapping.getHandlerMethods().keySet().forEach(mapping -> {
			PathPatternsRequestCondition patterns = mapping.getPathPatternsCondition();
			if (patterns != null) {
				paths.addAll(patterns.getPatternValues());
			}
		});
		return paths;
	}

	/**
	 * Replaces each path variable, such as {@code {id}} or {@code {*rest}}, with a value
	 * that a plain path segment accepts.
	 */
	private static String concrete(String pathPattern) {
		return pathPattern.replaceAll("\\{\\*?[^}/]+}", "1");
	}

	private int anonymousGet(String path) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + path))
			.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			.GET()
			.build();
		return this.httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

}
