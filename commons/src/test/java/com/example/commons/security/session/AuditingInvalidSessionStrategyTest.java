package com.example.commons.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

@ExtendWith(OutputCaptureExtension.class)
class AuditingInvalidSessionStrategyTest {

	private final HttpSessionRequestCache requestCache = new HttpSessionRequestCache();

	private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/account");

	private final MockHttpServletResponse response = new MockHttpServletResponse();

	private final AuditingInvalidSessionStrategy strategy = new AuditingInvalidSessionStrategy(
			new SessionLifecycleAuditLogger(), () -> this.requestCache,
			(request, response, ex) -> response.setStatus(401),
			(request, response, ex) -> response.sendError(403, ex.getClass().getSimpleName()));

	@Test
	void logsAnUnknownRequestedSessionAndAnswersAsUnauthenticated(CapturedOutput output) throws Exception {
		this.request.setRequestedSessionId("unknown-session-id");
		this.request.setRequestedSessionIdValid(false);

		this.strategy.onInvalidSessionDetected(this.request, this.response);

		assertThat(output).contains("event.action=\"resume_session\"")
			.contains("event.reason=\"session_not_found\"")
			.doesNotContain("unknown-session-id");
		assertThat(this.requestCache.getRequest(this.request, this.response)).isNotNull();
		assertThat(this.response.getStatus()).isEqualTo(401);
		assertThat(this.request.getSession(false)).isNotNull();
	}

	@Test
	void answersAMissingCsrfTokenWithoutAPresentedSessionIdAsACsrfFailure(CapturedOutput output) throws Exception {
		MockHttpServletRequest post = new MockHttpServletRequest("POST", "/account");

		this.strategy.onInvalidSessionDetected(post, this.response);

		assertThat(this.response.getStatus()).isEqualTo(403);
		assertThat(this.response.getErrorMessage()).isEqualTo(MissingCsrfTokenException.class.getSimpleName());
		assertThat(output).doesNotContain("resume_session");
		assertThat(this.requestCache.getRequest(post, this.response)).isNull();
		assertThat(post.getSession(false)).isNull();
	}

}
