package com.example.app.web.server.security.authentication;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import com.example.app.web.server.api.ProblemTypes;
import com.example.app.web.server.security.ContentNegotiation;

/**
 * Returns an appropriate response when a request carrying no authentication at all
 * reaches a protected endpoint. A browser is redirected to the OAuth2 authorization
 * endpoint, exactly as Spring Security's own default entry point would do; a non-browser
 * client instead receives an RFC 9457 Problem Details response, matching how
 * {@link com.example.app.web.server.security.session.ContentNegotiatingSessionExpiredStrategy}
 * already treats an expired session.
 */
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private static final String UNAUTHENTICATED_PROBLEM_DETAIL = "{\"type\":\"%s\",\"title\":\"%s\",\"status\":%d}"
		.formatted(ProblemTypes.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED.getReasonPhrase(),
				HttpStatus.UNAUTHORIZED.value());

	private final AuthenticationEntryPoint browserEntryPoint;

	public ProblemDetailAuthenticationEntryPoint(String authorizationRequestUri) {
		this.browserEntryPoint = new LoginUrlAuthenticationEntryPoint(authorizationRequestUri);
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException, ServletException {
		if (ContentNegotiation.acceptsHtml(request)) {
			this.browserEntryPoint.commence(request, response, authException);
			return;
		}
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(UNAUTHENTICATED_PROBLEM_DETAIL);
	}

}
