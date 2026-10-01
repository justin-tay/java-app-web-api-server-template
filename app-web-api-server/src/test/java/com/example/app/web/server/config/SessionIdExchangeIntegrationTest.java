package com.example.app.web.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.example.app.web.server.test.OidcLoginITSupport;

/**
 * Integration test proving that the {@code id} cookie is the only mechanism that resumes
 * a session: the same authenticated session ID presented any other way is ignored.
 */
@ExtendWith(OutputCaptureExtension.class)
class SessionIdExchangeIntegrationTest extends OidcLoginITSupport {

	@Test
	void onlyTheIdCookieResumesTheSession(CapturedOutput output) throws Exception {
		String sessionCookie = login();
		String sessionId = sessionId(sessionCookie);
		String encodedSessionId = Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));
		// The HTTP firewall rejects a path parameter outright (400); every other
		// mechanism reaches authorization unauthenticated (401).
		Map<String, HttpRequest> rejectedByFirewall = new LinkedHashMap<>();
		rejectedByFirewall.put(";jsessionid= path parameter", request("/login-user;jsessionid=" + sessionId).build());
		rejectedByFirewall.put(";id= path parameter", request("/login-user;id=" + sessionId).build());
		Map<String, HttpRequest> otherMechanisms = new LinkedHashMap<>();
		otherMechanisms.put("?jsessionid= query parameter", request("/login-user?jsessionid=" + sessionId).build());
		otherMechanisms.put("?id= query parameter", request("/login-user?id=" + sessionId).build());
		otherMechanisms.put("?id= query parameter, cookie encoding",
				request("/login-user?id=" + encodedSessionId).build());
		otherMechanisms.put("?SESSION= query parameter", request("/login-user?SESSION=" + encodedSessionId).build());
		otherMechanisms.put("?sessionid= query parameter", request("/login-user?sessionid=" + sessionId).build());
		otherMechanisms.put("X-Auth-Token header", request("/login-user").header("X-Auth-Token", sessionId).build());
		otherMechanisms.put("SESSION header", request("/login-user").header("SESSION", sessionId).build());
		otherMechanisms.put("SESSION cookie",
				request("/login-user").header(HttpHeaders.COOKIE, "SESSION=" + encodedSessionId).build());
		otherMechanisms.put("JSESSIONID cookie",
				request("/login-user").header(HttpHeaders.COOKIE, "JSESSIONID=" + sessionId).build());
		long oldLastAccessTime = markSessionLastAccessedAMinuteAgo(sessionId);

		assertIgnored(rejectedByFirewall, HttpStatus.BAD_REQUEST);
		assertIgnored(otherMechanisms, HttpStatus.UNAUTHORIZED);
		// The firewall's reject_request event records the path without the rejected
		// path parameter, so the session ID it carried does not reach the logs.
		List<String> rejections = output.getOut()
			.lines()
			.filter(line -> line.contains("\"action\":\"reject_request\""))
			.toList();
		assertThat(rejections).hasSize(rejectedByFirewall.size())
			.allSatisfy(
					line -> assertThat(line).contains("\"path\":\"/login-user;[REDACTED]\"").doesNotContain(sessionId));
		// The request events record each query parameter carrying the session ID with its
		// value redacted.
		List<String> queryRequests = output.getOut()
			.lines()
			.filter(line -> line.contains("\"action\":\"receive_request\"")
					|| line.contains("\"action\":\"complete_request\""))
			.filter(line -> line.contains("\"path\":\"/login-user\"") && line.contains("\"query\":"))
			.toList();
		assertThat(queryRequests).hasSize(2 * 5)
			.allSatisfy(line -> assertThat(line)
				.containsPattern("\"query\":\"(jsessionid|id|SESSION|sessionid)=\\[REDACTED]\"")
				.doesNotContain(sessionId)
				.doesNotContain(encodedSessionId));
		assertThat(output.getOut()).doesNotContain(sessionId).doesNotContain(encodedSessionId);
		assertThat(lastAccessTime(sessionId)).as("session resumed by another mechanism").isEqualTo(oldLastAccessTime);

		HttpResponse<String> cookieResponse = this.client.send(
				request("/login-user").header(HttpHeaders.COOKIE, sessionCookie).build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(cookieResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
		assertThat(cookieResponse.body()).contains("\"username\":\"user\"");
		assertThat(lastAccessTime(sessionId)).isGreaterThan(oldLastAccessTime);
	}

	private void assertIgnored(Map<String, HttpRequest> mechanisms, HttpStatus expectedStatus) throws Exception {
		for (Map.Entry<String, HttpRequest> mechanism : mechanisms.entrySet()) {
			HttpResponse<String> response = this.client.send(mechanism.getValue(),
					HttpResponse.BodyHandlers.ofString());

			assertThat(response.statusCode()).as(mechanism.getKey()).isEqualTo(expectedStatus.value());
			assertThat(response.body()).as(mechanism.getKey()).doesNotContain("user");
		}
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(uri(path)).header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).GET();
	}

	/**
	 * Moves the session's last access time a minute into the past, well within its idle
	 * timeout, so any request that resumes the session visibly updates it.
	 */
	private long markSessionLastAccessedAMinuteAgo(String sessionId) {
		long lastAccessTime = System.currentTimeMillis() - 60_000;
		this.jdbcTemplate.update("UPDATE SPRING_SESSION SET LAST_ACCESS_TIME = ? WHERE SESSION_ID = ?", lastAccessTime,
				sessionId);
		return lastAccessTime;
	}

	private long lastAccessTime(String sessionId) {
		return this.jdbcTemplate.queryForObject("SELECT LAST_ACCESS_TIME FROM SPRING_SESSION WHERE SESSION_ID = ?",
				Long.class, sessionId);
	}

}
