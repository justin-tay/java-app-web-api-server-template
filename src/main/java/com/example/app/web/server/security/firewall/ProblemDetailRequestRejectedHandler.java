package com.example.app.web.server.security.firewall;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;

import com.example.app.web.server.api.ProblemTypes;

/**
 * Logs and writes a Problem Details response for a {@link RequestRejectedException}
 * raised by Spring Security's {@code HttpFirewall} (the default
 * {@code StrictHttpFirewall} unless replaced).
 * <p>
 * The firewall rejects a request, for example a path-traversal attempt, an encoded slash,
 * or a control character in a header field, before it reaches any filter this application
 * registers or {@code DispatcherServlet}. {@code SecurityLoggingContextFilter} has not
 * yet run, so no correlation ID or MDC context exists; this handler adds
 * {@code source.ip} directly from the request instead. Because
 * {@code com.example.app.web.server.api.ApiResponseEntityExceptionHandler} only sees
 * exceptions thrown from within {@code DispatcherServlet}'s handler invocation, this is
 * the only place a firewall rejection is recorded. The exception's message is never
 * logged or returned, consistent with every other {@code validate_input}-style event,
 * since it can echo the rejected request content.
 */
public class ProblemDetailRequestRejectedHandler implements RequestRejectedHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProblemDetailRequestRejectedHandler.class);

	private static final String REQUEST_REJECTED_PROBLEM_DETAIL = "{\"type\":\"%s\",\"title\":\"%s\",\"status\":%d}"
		.formatted(ProblemTypes.REQUEST_REJECTED, HttpStatus.BAD_REQUEST.getReasonPhrase(),
				HttpStatus.BAD_REQUEST.value());

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			RequestRejectedException requestRejectedException) throws IOException, ServletException {
		LOGGER.atWarn()
			.addKeyValue("event.category", List.of("web"))
			.addKeyValue("event.type", List.of("error"))
			.addKeyValue("event.action", "reject_request")
			.addKeyValue("event.outcome", "failure")
			.addKeyValue("http.response.status_code", HttpStatus.BAD_REQUEST.value())
			.addKeyValue("http.request.method", request.getMethod())
			.addKeyValue("url.path", request.getRequestURI())
			.addKeyValue("source.ip", request.getRemoteAddr())
			.addKeyValue("error.type", requestRejectedException.getClass().getSimpleName())
			.log("Request rejected by the HTTP firewall");
		if (response.isCommitted()) {
			return;
		}
		response.setStatus(HttpStatus.BAD_REQUEST.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(REQUEST_REJECTED_PROBLEM_DETAIL);
	}

}
