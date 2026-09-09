package com.example.app.web.server.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

import com.example.app.web.server.api.ProblemTypes;

/**
 * Writes Problem Details responses for access-denied requests.
 */
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProblemDetailAccessDeniedHandler.class);

	private static final String ACCESS_DENIED_PROBLEM_DETAIL = "{\"type\":\"%s\",\"title\":\"%s\",\"status\":%d}"
		.formatted(ProblemTypes.ACCESS_DENIED, HttpStatus.FORBIDDEN.getReasonPhrase(), HttpStatus.FORBIDDEN.value());

	private static final String CSRF_PROBLEM_DETAIL = "{\"type\":\"%s\",\"title\":\"%s\",\"status\":%d,\"detail\":\"The request could not be verified. Refresh the page and try again.\"}"
		.formatted(ProblemTypes.CSRF_VALIDATION_FAILED, HttpStatus.FORBIDDEN.getReasonPhrase(),
				HttpStatus.FORBIDDEN.value());

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
			throws IOException, ServletException {
		if (exception instanceof CsrfException) {
			LOGGER.atWarn()
				.addKeyValue("event.category", List.of("web", "api"))
				.addKeyValue("event.type", List.of("access", "denied"))
				.addKeyValue("event.action", "validate_csrf_token")
				.addKeyValue("event.outcome", "failure")
				.addKeyValue("http.response.status_code", HttpStatus.FORBIDDEN.value())
				.addKeyValue("url.path", request.getRequestURI())
				.addKeyValue("source.ip", request.getRemoteAddr())
				.addKeyValue("error.type", exception.getClass().getSimpleName())
				.log("CSRF validation failed");
		}
		String responseBody = (exception instanceof CsrfException) ? CSRF_PROBLEM_DETAIL : ACCESS_DENIED_PROBLEM_DETAIL;
		response.setStatus(HttpStatus.FORBIDDEN.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(responseBody);
	}

}
