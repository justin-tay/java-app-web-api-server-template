package com.example.app.web.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.app.web.server.test.RestTestClientITSupport;

/**
 * Integration tests for session management configured by
 * {@link WebSecurityConfiguration}.
 */
@Import(WebSecurityConfigurationSessionManagementIntegrationTest.SessionManagementTestConfiguration.class)
class WebSecurityConfigurationSessionManagementIntegrationTest extends RestTestClientITSupport {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private SessionRegistry sessionRegistry;

	@LocalServerPort
	private int port;

	@Test
	void successfulAuthenticationRotatesTheSessionId() throws Exception {
		HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
		String originalCookie = createSession(client);
		String originalSessionId = sessionId(originalCookie);

		HttpResponse<Void> authenticationResponse = authenticate(client, originalCookie);
		String rotatedSessionId = sessionId(sessionCookie(authenticationResponse));

		assertThat(authenticationResponse.statusCode()).isEqualTo(HttpStatus.FOUND.value());
		assertThat(rotatedSessionId).isNotEqualTo(originalSessionId);
		assertThat(sessionCount(originalSessionId)).isZero();
		assertThat(sessionCount(rotatedSessionId)).isEqualTo(1);
	}

	@Test
	void secondSuccessfulLoginInvalidatesTheExistingSession() throws Exception {
		HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
		String firstSessionCookie = sessionCookie(authenticate(client, createSession(client)));
		String firstSessionId = sessionId(firstSessionCookie);
		String secondSessionCookie = sessionCookie(authenticate(client, createSession(client)));
		String secondSessionId = sessionId(secondSessionCookie);
		assertThat(this.sessionRegistry.getSessionInformation(firstSessionId)).isNotNull();
		assertThat(this.sessionRegistry.getSessionInformation(firstSessionId).isExpired()).isTrue();

		HttpResponse<String> expiredSessionResponse = client
			.send(HttpRequest.newBuilder(uri("/test/session-security/protected"))
				.header(HttpHeaders.COOKIE, firstSessionCookie)
				.GET()
				.build(), HttpResponse.BodyHandlers.ofString());

		assertThat(expiredSessionResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
		assertThat(expiredSessionResponse.body()).contains("This session has been expired");
		assertThat(sessionCount(firstSessionId)).isZero();
		assertThat(sessionCount(secondSessionId)).isEqualTo(1);
	}

	private String createSession(HttpClient client) throws Exception {
		HttpResponse<Void> response = client.send(
				HttpRequest.newBuilder(uri("/test/session-security/session")).GET().build(),
				HttpResponse.BodyHandlers.discarding());
		return sessionCookie(response);
	}

	private HttpResponse<Void> authenticate(HttpClient client, String sessionCookie) throws Exception {
		return client.send(HttpRequest.newBuilder(uri("/test/session-security/login"))
			.header(HttpHeaders.COOKIE, sessionCookie)
			.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
			.POST(HttpRequest.BodyPublishers.ofString("username=test-user&password=not-used"))
			.build(), HttpResponse.BodyHandlers.discarding());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + this.port + path);
	}

	private int sessionCount(String sessionId) {
		return this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION WHERE SESSION_ID = ?",
				Integer.class, sessionId);
	}

	private String sessionCookie(HttpResponse<?> response) {
		return response.headers()
			.allValues(HttpHeaders.SET_COOKIE)
			.stream()
			.filter(cookie -> cookie.startsWith("id="))
			.findFirst()
			.map(cookie -> cookie.substring(0, cookie.indexOf(';')))
			.orElseThrow();
	}

	private String sessionId(String cookie) {
		return new String(Base64.getDecoder().decode(cookie.substring("id=".length())), StandardCharsets.UTF_8);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SessionManagementTestConfiguration {

		@Bean
		@Order(0)
		SecurityFilterChain sessionSecurityTestSecurityFilterChain(HttpSecurity http, SessionRegistry sessionRegistry)
				throws Exception {
			return http.securityMatcher("/test/session-security/**")
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(authorize -> authorize
					.requestMatchers("/test/session-security/session", "/test/session-security/login")
					.permitAll()
					.anyRequest()
					.authenticated())
				.authenticationProvider(testAuthenticationProvider())
				.sessionManagement(sessionManagement -> sessionManagement.maximumSessions(1)
					.maxSessionsPreventsLogin(false)
					.sessionRegistry(sessionRegistry))
				.formLogin(formLogin -> formLogin.loginProcessingUrl("/test/session-security/login"))
				.build();
		}

		@Bean
		SessionCreatingController sessionCreatingController() {
			return new SessionCreatingController();
		}

		private AuthenticationProvider testAuthenticationProvider() {
			return new AuthenticationProvider() {
				@Override
				public UsernamePasswordAuthenticationToken authenticate(
						org.springframework.security.core.Authentication authentication) {
					return UsernamePasswordAuthenticationToken.authenticated(authentication.getPrincipal(), null,
							List.of(new SimpleGrantedAuthority("ROLE_TEST")));
				}

				@Override
				public boolean supports(Class<?> authentication) {
					return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
				}
			};
		}

	}

	@Controller
	static class SessionCreatingController {

		@GetMapping("/test/session-security/session")
		@ResponseBody
		void createSession(HttpServletRequest request) {
			request.getSession();
		}

	}

}
