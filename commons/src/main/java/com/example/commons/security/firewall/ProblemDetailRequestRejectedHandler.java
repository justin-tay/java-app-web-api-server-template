package com.example.commons.security.firewall;

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

import com.example.commons.logging.EcsFields;
import com.example.commons.web.problem.ProblemTypes;
import com.example.commons.logging.LoggedUrlPath;

/**
 * Logs and writes a Problem Details response for a {@link RequestRejectedException}
 * raised by Spring Security's {@code HttpFirewall} (the default
 * {@code StrictHttpFirewall} unless replaced).
 * <p>
 * The firewall rejects a request, for example a path-traversal attempt, an encoded slash,
 * or a control character in a header field, before it reaches any filter registered
 * through Spring Security or {@code DispatcherServlet}, so {@code user.name} is never
 * available here. {@code http.request.id}, {@code source.ip}, and {@code client.ip} are
 * available, though: {@code RequestCorrelationContextFilter} runs ahead of the firewall
 * check and puts them in MDC, so this handler must not add them itself, since that would
 * duplicate the MDC-sourced fields and fail structured logging's duplicate-key check; see
 * docs/adr/0012. Because
 * {@code com.example.commons.web.problem.ApiResponseEntityExceptionHandler} only sees
 * exceptions thrown from within {@code DispatcherServlet}'s handler invocation, this is
 * the only place a firewall rejection is recorded. The exception's message is never
 * logged or returned, consistent with every other {@code validate_input}-style event,
 * since it can echo the rejected request content. For that reason {@code setCause()} is
 * deliberately not used below: it would add the withheld message (and an unnecessary
 * stack trace for this expected, client-driven outcome) as {@code error.message}/
 * {@code error.stack_trace}. The path is logged through {@link LoggedUrlPath}, because a
 * path parameter such as {@code ;jsessionid=} is one of the things the firewall rejects
 * and would otherwise put a session ID in the log.
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
			.addKeyValue(EcsFields.EVENT_CATEGORY, List.of("web"))
			.addKeyValue(EcsFields.EVENT_TYPE, List.of("error"))
			.addKeyValue(EcsFields.EVENT_ACTION, "reject_request")
			.addKeyValue(EcsFields.EVENT_OUTCOME, "failure")
			.addKeyValue("http.response.status_code", HttpStatus.BAD_REQUEST.value())
			.addKeyValue("http.request.method", request.getMethod())
			.addKeyValue("url.path", LoggedUrlPath.of(request.getRequestURI()))
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
