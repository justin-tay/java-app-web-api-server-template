package com.example.app.web.server.api;

import java.net.URI;
import java.util.List;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's default {@code BasicErrorController} so that a dispatch which
 * never reaches {@link ApiResponseEntityExceptionHandler} (for example one Spring MVC
 * forwards to {@code /error} outside a controller invocation) still returns RFC 9457
 * Problem Details instead of Boot's default error JSON shape. This is a last-resort net:
 * every error path exercised in practice is already handled by
 * {@code ApiResponseEntityExceptionHandler}, the security filter chain's Problem Details
 * handlers, or {@code TomcatProblemDetailErrorReportValve}, so reaching here at all is
 * unexpected and logged as such.
 */
@RestController
public class ProblemDetailErrorController implements ErrorController {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProblemDetailErrorController.class);

	@RequestMapping("/error")
	public ResponseEntity<ProblemDetail> handleError(HttpServletRequest request) {
		HttpStatus status = resolveStatus(request);
		LOGGER.atWarn()
			.addKeyValue("event.category", List.of("web"))
			.addKeyValue("event.type", List.of("error"))
			.addKeyValue("event.action", "handle_uncaught_dispatch")
			.addKeyValue("event.outcome", "failure")
			.addKeyValue("http.response.status_code", status.value())
			.addKeyValue("url.path", String.valueOf(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)))
			// setCause() is deliberately not used here: RequestDispatcher.ERROR_EXCEPTION
			// (the actual Throwable, if any) is not read, since this last-resort path is
			// reached from a forward outside any handler's control, and the underlying
			// cause has not been reviewed for message content safe to log; only the
			// class name from ERROR_EXCEPTION_TYPE is used.
			.addKeyValue("error.type", String.valueOf(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE)))
			.log("Request dispatch reached the last-resort error handler");
		ProblemDetail problemDetail = ProblemDetail.forStatus(status);
		problemDetail.setType(resolveType(status));
		return ResponseEntity.status(status).body(problemDetail);
	}

	private HttpStatus resolveStatus(HttpServletRequest request) {
		Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		if (statusAttribute instanceof Integer statusCode) {
			HttpStatus status = HttpStatus.resolve(statusCode);
			if (status != null) {
				return status;
			}
		}
		return HttpStatus.INTERNAL_SERVER_ERROR;
	}

	private URI resolveType(HttpStatus status) {
		return switch (status) {
			case NOT_FOUND -> ProblemTypes.ROUTE_NOT_FOUND;
			case METHOD_NOT_ALLOWED -> ProblemTypes.METHOD_NOT_ALLOWED;
			default -> ProblemTypes.INTERNAL_ERROR;
		};
	}

}
