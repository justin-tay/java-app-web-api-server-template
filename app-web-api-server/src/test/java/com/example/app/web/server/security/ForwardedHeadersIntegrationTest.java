package com.example.app.web.server.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.filter.ForwardedHeaderFilter;

import com.example.commons.logging.client.ClientIpResolver;
import com.example.commons.logging.client.XForwardedForClientIpResolver;

/**
 * Tests the application behind a gateway that strips a {@code /api} prefix and sends it
 * in {@code X-Forwarded-Prefix}, with {@code server.forward-headers-strategy=framework}:
 * generated URLs carry the external scheme, host and prefix, the cookies stay at
 * {@code Path=/} (required by the {@code __Host-} prefix), and the client IP resolvers
 * still see {@code X-Forwarded-For}. It runs against a real server, because Spring Boot
 * applies {@code server.servlet.session.cookie.*} to Spring Session only then.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "server.forward-headers-strategy=framework" })
@ActiveProfiles("test")
@Import(ForwardedHeadersIntegrationTest.ResolverConfiguration.class)
class ForwardedHeadersIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private AtomicReference<Optional<String>> resolvedClientIp;

	@Test
	void generatedUrlsCarryTheExternalSchemeHostAndPrefix() throws Exception {
		HttpResponse<Void> response = login();

		assertThat(response.headers().firstValue(HttpHeaders.LOCATION).orElseThrow())
			.contains("redirect_uri=https://example.com/api/login/oauth2/code/keycloak");
	}

	@Test
	void theCookiesStayAtTheRootPath() throws Exception {
		List<String> setCookies = login().headers().allValues(HttpHeaders.SET_COOKIE);

		assertThat(setCookies).anyMatch(header -> header.startsWith("id="));
		assertThat(setCookies).anyMatch(header -> header.startsWith("XSRF-TOKEN="));
		assertThat(setCookies).allSatisfy(header -> assertThat(header).containsIgnoringCase("Path=/;"));
	}

	@Test
	void theClientIpResolverStillSeesXForwardedFor() throws Exception {
		login();

		assertThat(this.resolvedClientIp.get()).contains("203.0.113.9");
	}

	@Test
	void onlyOneForwardedHeaderFilterIsRegistered() {
		assertThat(this.applicationContext.getBeansOfType(FilterRegistrationBean.class).values())
			.filteredOn(registration -> registration.getFilter() instanceof ForwardedHeaderFilter)
			.hasSize(1);
	}

	private HttpResponse<Void> login() throws Exception {
		return HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER)
			.build()
			.send(HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + "/oauth2/authorization/keycloak"))
				.header("X-Forwarded-Proto", "https")
				.header("X-Forwarded-Host", "example.com")
				.header("X-Forwarded-Prefix", "/api")
				.header("X-Forwarded-For", "203.0.113.9")
				.GET()
				.build(), HttpResponse.BodyHandlers.discarding());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ResolverConfiguration {

		@Bean
		AtomicReference<Optional<String>> resolvedClientIp() {
			return new AtomicReference<>(Optional.empty());
		}

		@Bean
		ClientIpResolver clientIpResolver(AtomicReference<Optional<String>> resolvedClientIp) {
			ClientIpResolver delegate = XForwardedForClientIpResolver.leftmost(List.of("127.0.0.0/8", "::1/128"));
			return (HttpServletRequest request) -> {
				Optional<String> resolved = delegate.resolve(request);
				resolvedClientIp.set(resolved);
				return resolved;
			};
		}

	}

}
