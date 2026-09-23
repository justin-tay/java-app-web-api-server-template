package com.example.app.web.server.security.session;

import java.io.IOException;
import java.util.function.Supplier;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.session.InvalidSessionStrategy;

/**
 * Logs a request that presents a session ID the session repository no longer (or never)
 * held, then responds exactly as an unauthenticated request is answered: the request is
 * saved in the request cache, as {@code ExceptionTranslationFilter} does, and the
 * application's authentication entry point redirects a browser to login or returns a 401
 * Problem Details response.
 * <p>
 * Spring Session returns no session for an ID past its idle timeout, so this is where
 * idle expiry is detected, on the session's next use. It is indistinguishable here from
 * an ended or forged ID, so the event says only that the requested session was not found.
 * <p>
 * Spring Security also calls the invalid-session strategy from {@code CsrfFilter} for any
 * {@link MissingCsrfTokenException}, including a request that presented no session ID at
 * all. Such a request is not an invalid session: it is answered by the access-denied
 * handler as the CSRF failure it is, and nothing is logged here.
 */
public class AuditingInvalidSessionStrategy implements InvalidSessionStrategy {

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger;

	private final Supplier<RequestCache> requestCache;

	private final AuthenticationEntryPoint authenticationEntryPoint;

	private final AccessDeniedHandler accessDeniedHandler;

	/**
	 * Creates the strategy.
	 * @param sessionLifecycleAuditLogger records the event
	 * @param requestCache supplies the filter chain's request cache, resolved per request
	 * because it is only known once the chain is built
	 * @param authenticationEntryPoint the entry point that answers unauthenticated
	 * requests
	 * @param accessDeniedHandler the handler that answers a missing CSRF token when no
	 * invalid session ID was presented
	 */
	public AuditingInvalidSessionStrategy(SessionLifecycleAuditLogger sessionLifecycleAuditLogger,
			Supplier<RequestCache> requestCache, AuthenticationEntryPoint authenticationEntryPoint,
			AccessDeniedHandler accessDeniedHandler) {
		this.sessionLifecycleAuditLogger = sessionLifecycleAuditLogger;
		this.requestCache = requestCache;
		this.authenticationEntryPoint = authenticationEntryPoint;
		this.accessDeniedHandler = accessDeniedHandler;
	}

	@Override
	public void onInvalidSessionDetected(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		if (request.getRequestedSessionId() == null || request.isRequestedSessionIdValid()) {
			this.accessDeniedHandler.handle(request, response, new MissingCsrfTokenException(null));
			return;
		}
		this.sessionLifecycleAuditLogger.logRequestedSessionNotFound();
		RequestCache cache = this.requestCache.get();
		if (cache != null) {
			cache.saveRequest(request, response);
		}
		this.authenticationEntryPoint.commence(request, response,
				new InsufficientAuthenticationException("Requested session not found"));
	}

}
