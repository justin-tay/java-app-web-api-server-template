package com.example.commons.security.session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SessionBindingFilter}.
 */
@ExtendWith(OutputCaptureExtension.class)
class SessionBindingFilterTest {

	private final MockHttpServletResponse response = new MockHttpServletResponse();

	private final MockHttpSession session = new MockHttpSession();

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger = new SessionLifecycleAuditLogger();

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void capturesTheUserAgentAndClientIpOnTheFirstRequest() throws Exception {
		MockHttpServletRequest request = requestFrom("browser-a");
		MockFilterChain filterChain = new MockFilterChain();

		filter(true, true, "203.0.113.1").doFilter(request, this.response, filterChain);

		assertThat(this.session.isInvalid()).isFalse();
		assertThat(filterChain.getRequest()).isSameAs(request);
	}

	@Test
	void invalidatesTheSessionWhenTheUserAgentChanges() throws Exception {
		this.sessionLifecycleAuditLogger.logSessionCreatedIfNeeded(this.session);
		filter(true, true, "203.0.113.1").doFilter(requestFrom("browser-a"), this.response, new MockFilterChain());

		filter(true, true, "203.0.113.1").doFilter(requestFrom("browser-b"), this.response, new MockFilterChain());

		assertThat(this.session.isInvalid()).isTrue();
	}

	@Test
	void retainsTheSessionWhenHijackingProtectionIsDisabled(CapturedOutput output) throws Exception {
		filter(false, true, "203.0.113.1").doFilter(requestFrom("browser-a"), this.response, new MockFilterChain());

		filter(false, true, "203.0.113.1").doFilter(requestFrom("browser-b"), this.response, new MockFilterChain());

		assertThat(this.session.isInvalid()).isFalse();
		assertThat(output).doesNotContain("user_agent_mismatch");
	}

	@Test
	void logsAnAnomalyWithoutInvalidatingWhenTheClientIpChanges(CapturedOutput output) throws Exception {
		this.sessionLifecycleAuditLogger.logSessionCreatedIfNeeded(this.session);
		filter(true, true, "203.0.113.1").doFilter(requestFrom("browser-a"), this.response, new MockFilterChain());

		filter(true, true, "203.0.113.2").doFilter(requestFrom("browser-a"), this.response, new MockFilterChain());

		assertThat(this.session.isInvalid()).isFalse();
		assertThat(output).contains("event.reason=\"client_ip_changed\"")
			.contains("session.bound_client_ip=\"203.0.113.1\"");
	}

	@Test
	void doesNotDetectAnAnomalyWhenAnomalyDetectionIsDisabled(CapturedOutput output) throws Exception {
		filter(true, false, "203.0.113.1").doFilter(requestFrom("browser-a"), this.response, new MockFilterChain());

		filter(true, false, "203.0.113.2").doFilter(requestFrom("browser-a"), this.response, new MockFilterChain());

		assertThat(this.session.isInvalid()).isFalse();
		assertThat(output).doesNotContain("client_ip_changed");
	}

	@Test
	void passesARequestWithoutASessionThrough() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/account");
		request.addHeader("User-Agent", "browser-a");
		MockFilterChain filterChain = new MockFilterChain();

		filter(true, true, "203.0.113.1").doFilter(request, this.response, filterChain);

		assertThat(request.getSession(false)).isNull();
		assertThat(filterChain.getRequest()).isSameAs(request);
	}

	private MockHttpServletRequest requestFrom(String userAgent) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/account");
		request.addHeader("User-Agent", userAgent);
		request.setSession(this.session);
		return request;
	}

	private SessionBindingFilter filter(boolean hijackingProtection, boolean anomalyDetection, String clientIp) {
		MDC.put("client.ip", clientIp);
		return new SessionBindingFilter(hijackingProtection, anomalyDetection, this.sessionLifecycleAuditLogger);
	}

}
