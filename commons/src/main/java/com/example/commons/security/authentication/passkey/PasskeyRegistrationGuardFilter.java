package com.example.commons.security.authentication.passkey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.commons.logging.LoggedUrlPath;
import com.example.commons.security.authentication.oidc.ReauthenticationChallenge;
import com.example.commons.security.authentication.oidc.RecentAuthentication;
import com.example.commons.web.problem.ProblemTypes;

/**
 * Guards the two requests that register a passkey, Spring Security's
 * {@code POST /webauthn/register/options} and {@code POST /webauthn/register}. Both run
 * before the authorization filter, or without a request of their own to authorize, so the
 * checks are made here (see docs/adr/0024):
 * <ul>
 * <li>the user must be authenticated, or the request is answered by the authentication
 * entry point;
 * <li>the user must have logged in no longer than the configured age ago, or the request
 * is answered with a 401 {@code reauthentication-required} problem, the way the
 * administration API answers a stale login (see docs/adr/0023);
 * <li>the user must hold fewer than the allowed number of passkeys, or the request is
 * answered with a 409 problem.
 * </ul>
 */
class PasskeyRegistrationGuardFilter extends OncePerRequestFilter {

	private static final Logger LOGGER = LoggerFactory.getLogger(PasskeyRegistrationGuardFilter.class);

	private static final RequestMatcher REGISTRATION = new OrRequestMatcher(
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/webauthn/register/options"),
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/webauthn/register"));

	private final AuthenticationEntryPoint authenticationEntryPoint;

	private final PublicKeyCredentialUserEntityRepository userEntities;

	private final UserCredentialRepository userCredentials;

	private final Duration maxAge;

	private final int maxPerUser;

	private final Clock clock;

	PasskeyRegistrationGuardFilter(AuthenticationEntryPoint authenticationEntryPoint,
			PublicKeyCredentialUserEntityRepository userEntities, UserCredentialRepository userCredentials,
			Duration maxAge, int maxPerUser, Clock clock) {
		this.authenticationEntryPoint = authenticationEntryPoint;
		this.userEntities = userEntities;
		this.userCredentials = userCredentials;
		this.maxAge = maxAge;
		this.maxPerUser = maxPerUser;
		this.clock = clock;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!REGISTRATION.matches(request)) {
			filterChain.doFilter(request, response);
			return;
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || authentication instanceof AnonymousAuthenticationToken
				|| !authentication.isAuthenticated()) {
			this.authenticationEntryPoint.commence(request, response,
					new InsufficientAuthenticationException("Authentication is required to register a passkey"));
			return;
		}
		if (!RecentAuthentication.isWithin(authentication, request, this.maxAge, this.clock)) {
			StringBuilder members = new StringBuilder(",\"max_age\":" + this.maxAge.toSeconds());
			ReauthenticationChallenge.members(authentication)
				.forEach((name, value) -> members.append(",\"%s\":\"%s\"".formatted(name, value)));
			reject(request, response, HttpStatus.UNAUTHORIZED, "reauthentication_required",
					ProblemTypes.REAUTHENTICATION_REQUIRED.toString(), "Recent authentication is required.",
					members.toString());
			return;
		}
		if (holdsTheMostAllowed(authentication)) {
			reject(request, response, HttpStatus.CONFLICT, "passkey_limit_reached",
					ProblemTypes.RESOURCE_CONFLICT.toString(), "The most passkeys allowed are already registered.", "");
			return;
		}
		filterChain.doFilter(request, response);
	}

	private boolean holdsTheMostAllowed(Authentication authentication) {
		PublicKeyCredentialUserEntity entity = this.userEntities.findByUsername(authentication.getName());
		return entity != null && this.userCredentials.findByUserId(entity.getId()).size() >= this.maxPerUser;
	}

	private static void reject(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
			String reason, String type, String detail, String extraMembers) throws IOException {
		LOGGER.atWarn()
			.addKeyValue("event.category", List.of("web", "api"))
			.addKeyValue("event.type", List.of("access", "denied"))
			.addKeyValue("event.action", "authorize_access")
			.addKeyValue("event.outcome", "failure")
			.addKeyValue("event.reason", reason)
			.addKeyValue("http.response.status_code", status.value())
			.addKeyValue("url.path", LoggedUrlPath.of(request.getRequestURI()))
			.log("Passkey registration refused");
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8);
		response.getWriter()
			.write("{\"type\":\"%s\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"%s}".formatted(type,
					status.getReasonPhrase(), status.value(), detail, extraMembers));
	}

}
