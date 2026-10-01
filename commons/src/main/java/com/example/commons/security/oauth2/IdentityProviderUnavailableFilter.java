package com.example.commons.security.oauth2;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.commons.web.problem.ProblemTypes;

/**
 * Answers 503 with an RFC 9457 Problem Details body and a {@code Retry-After} header when
 * a login request needs a client registration whose OpenID Provider cannot be reached yet
 * (see docs/adr/0029).
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

	public IdentityProviderUnavailableFilter(ClientRegistrationRepository clientRegistrationRepository) {
		this.clientRegistrationRepository = clientRegistrationRepository;
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
			response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
			response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, ex.getRetryAfter().toSeconds())));
			response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
			response.setCharacterEncoding("UTF-8");
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
