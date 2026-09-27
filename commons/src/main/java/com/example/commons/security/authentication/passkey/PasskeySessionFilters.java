package com.example.commons.security.authentication.passkey;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpMessageConverterAuthenticationSuccessHandler;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.commons.security.authentication.oidc.RecentAuthentication;
import com.example.commons.security.session.SessionLifecycleAuditLogger;

/**
 * The parts of session handling that are specific to a passkey login: recording when the
 * login happened, and ending the session at its own maximum lifetime.
 * <p>
 * A passkey session has no OpenID Provider session behind it, so back-channel logout
 * cannot end it, and it gets a lifetime of its own, counted from the passkey login rather
 * than from when the browser first received a session cookie (see docs/adr/0024).
 */
final class PasskeySessionFilters {

	private PasskeySessionFilters() {
	}

	/**
	 * Records the time of a passkey login in the session, where
	 * {@link RecentAuthentication} reads it, then answers the login as Spring Security
	 * does.
	 */
	static class LoginRecorder implements AuthenticationSuccessHandler {

		private final AuthenticationSuccessHandler delegate = new HttpMessageConverterAuthenticationSuccessHandler();

		private final Clock clock;

		LoginRecorder(Clock clock) {
			this.clock = clock;
		}

		@Override
		public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
				Authentication authentication) throws IOException, ServletException {
			request.getSession()
				.setAttribute(RecentAuthentication.PASSKEY_AUTHENTICATED_AT_ATTRIBUTE, this.clock.instant());
			this.delegate.onAuthenticationSuccess(request, response, authentication);
		}

	}

	/**
	 * Ends a passkey session once it is older than its maximum lifetime.
	 */
	static class AbsoluteTimeoutFilter extends OncePerRequestFilter {

		private final Duration timeout;

		private final Clock clock;

		private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger;

		AbsoluteTimeoutFilter(Duration timeout, Clock clock, SessionLifecycleAuditLogger sessionLifecycleAuditLogger) {
			this.timeout = timeout;
			this.clock = clock;
			this.sessionLifecycleAuditLogger = sessionLifecycleAuditLogger;
		}

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
				FilterChain filterChain) throws ServletException, IOException {
			if (SecurityContextHolder.getContext().getAuthentication() instanceof WebAuthnAuthentication
					&& hasExpired(request.getSession(false))) {
				this.sessionLifecycleAuditLogger.logSessionDestroyed(request.getSession(false),
						"passkey_absolute_timeout");
				request.getSession(false).invalidate();
				SecurityContextHolder.clearContext();
			}
			filterChain.doFilter(request, response);
		}

		private boolean hasExpired(HttpSession session) {
			Object authenticatedAt = (session != null)
					? session.getAttribute(RecentAuthentication.PASSKEY_AUTHENTICATED_AT_ATTRIBUTE) : null;
			return session != null && (!(authenticatedAt instanceof Instant instant)
					|| !this.clock.instant().isBefore(instant.plus(this.timeout)));
		}

	}

}
