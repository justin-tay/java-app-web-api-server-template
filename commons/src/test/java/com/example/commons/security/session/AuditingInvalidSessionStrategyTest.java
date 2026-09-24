package com.example.commons.security.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.security.web.savedrequest.RequestCache;

class AuditingInvalidSessionStrategyTest {

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger = mock(SessionLifecycleAuditLogger.class);

	private final RequestCache requestCache = mock(RequestCache.class);

	private final AuthenticationEntryPoint authenticationEntryPoint = mock(AuthenticationEntryPoint.class);

	private final AccessDeniedHandler accessDeniedHandler = mock(AccessDeniedHandler.class);

	private final AuditingInvalidSessionStrategy strategy = new AuditingInvalidSessionStrategy(
			this.sessionLifecycleAuditLogger, () -> this.requestCache, this.authenticationEntryPoint,
			this.accessDeniedHandler);

	@Test
	void logsAnUnknownRequestedSessionAndAnswersAsUnauthenticated() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/account");
		request.setRequestedSessionId("unknown-session-id");
		request.setRequestedSessionIdValid(false);
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.strategy.onInvalidSessionDetected(request, response);

		verify(this.sessionLifecycleAuditLogger).logRequestedSessionNotFound();
		verify(this.requestCache).saveRequest(request, response);
		verify(this.authenticationEntryPoint).commence(same(request), same(response),
				any(AuthenticationException.class));
		verifyNoInteractions(this.accessDeniedHandler);
	}

	@Test
	void answersAMissingCsrfTokenWithoutAPresentedSessionIdAsACsrfFailure() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/account");
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.strategy.onInvalidSessionDetected(request, response);

		verify(this.accessDeniedHandler).handle(same(request), same(response), any(MissingCsrfTokenException.class));
		verifyNoInteractions(this.sessionLifecycleAuditLogger, this.requestCache, this.authenticationEntryPoint);
	}

}
