package com.example.commons.security.oauth2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.commons.security.ContentNegotiation;
import com.example.commons.web.problem.ProblemTypes;

/**
 * Answers a login request whose client registration cannot be resolved yet because its
 * OpenID Provider is unreachable (see docs/adr/0029). A browser navigation, which is what
 * a single-page application's {@code window.location} to the authorization URL is, is
 * redirected to {@code unavailableRedirectUri}, since it would otherwise display raw
 * JSON; any other client receives 503 with an RFC 9457 Problem Details body. Both carry
 * {@code Retry-After}.
 *
 * <p>
 * Spring Security's authorization redirect filter turns any exception from resolving the
 * registration into an authentication failure, so this filter resolves the registration
 * of a login request itself first (the authorization request
 * {@code /oauth2/authorization/{registrationId}} and its callback
 * {@code /login/oauth2/code/{registrationId}}). Once resolved, a registration is kept, so
 * this costs a map lookup.
 */
public class IdentityProviderUnavailableFilter extends OncePerRequestFilter {

	private static final String PROBLEM_DETAIL = "{\"type\":\"%s\",\"title\":\"%s\",\"status\":%d}".formatted(
			ProblemTypes.IDENTITY_PROVIDER_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
			HttpStatus.SERVICE_UNAVAILABLE.value());

	private static final String[] LOGIN_PATH_PREFIXES = { "/oauth2/authorization/", "/login/oauth2/code/" };

	private final ClientRegistrationRepository clientRegistrationRepository;

	private final String unavailableRedirectUri;

	public IdentityProviderUnavailableFilter(ClientRegistrationRepository clientRegistrationRepository,
			String unavailableRedirectUri) {
		this.clientRegistrationRepository = clientRegistrationRepository;
		this.unavailableRedirectUri = unavailableRedirectUri;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		try {
			String registrationId = registrationId(request);
			if (registrationId != null) {
				this.clientRegistrationRepository.findByRegistrationId(registrationId);
			}
			filterChain.doFilter(request, response);
		}
		catch (IdentityProviderUnavailableException ex) {
			if (response.isCommitted()) {
				throw ex;
			}
			response.resetBuffer();
			response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, ex.getRetryAfter().toSeconds())));
			if (ContentNegotiation.acceptsHtml(request)) {
				// Set the header rather than sendRedirect, so a relative URI resolves
				// against the address the browser used, such as a development proxy's.
				response.setStatus(HttpStatus.FOUND.value());
				response.setHeader(HttpHeaders.LOCATION, this.unavailableRedirectUri);
				return;
			}
			response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
			response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
			response.setCharacterEncoding(StandardCharsets.UTF_8);
			response.getWriter().write(PROBLEM_DETAIL);
		}
	}

	private static String registrationId(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		for (String prefix : LOGIN_PATH_PREFIXES) {
			if (path.startsWith(prefix) && path.length() > prefix.length()) {
				return path.substring(prefix.length());
			}
		}
		return null;
	}

}
