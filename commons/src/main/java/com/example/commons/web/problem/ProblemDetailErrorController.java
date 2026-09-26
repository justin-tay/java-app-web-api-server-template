package com.example.commons.web.problem;

import java.net.URI;
import java.util.List;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.commons.logging.EcsFields;
import com.example.commons.logging.LoggedUrlPath;
import com.example.commons.logging.MessageRedactedStackTraces;

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
		LoggingEventBuilder event = LOGGER.atError()
			.addKeyValue(EcsFields.EVENT_CATEGORY, List.of("web"))
			.addKeyValue(EcsFields.EVENT_TYPE, List.of("error"))
			.addKeyValue(EcsFields.EVENT_ACTION, "process_request")
			.addKeyValue(EcsFields.EVENT_OUTCOME, "failure")
			.addKeyValue("http.response.status_code", status.value());
		addUrlPath(event, request);
		addExceptionType(event, request);
		addStackTrace(event, request);
		event.log("Request dispatch reached the last-resort error handler");
		ProblemDetail problemDetail = ProblemDetail.forStatus(status);
		problemDetail.setType(resolveType(status));
		return ResponseEntity.status(status).body(problemDetail);
	}

	private void addUrlPath(LoggingEventBuilder event, HttpServletRequest request) {
		Object requestUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
		if (requestUri != null) {
			event.addKeyValue("url.path", LoggedUrlPath.of(String.valueOf(requestUri)));
		}
	}

	private void addExceptionType(LoggingEventBuilder event, HttpServletRequest request) {
		if (request.getAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE) instanceof Class<?> exceptionType) {
			event.addKeyValue("error.type", exceptionType.getName());
		}
	}

	/**
	 * {@code setCause()} is deliberately not used here: the ECS formatter would derive
	 * {@code error.message} from it too, and this last-resort path is reached from a
	 * forward outside any handler's control, so the underlying cause's message has not
	 * been reviewed for content safe to log; it could echo unsanitized request content.
	 * {@link MessageRedactedStackTraces} renders the same class names and stack frames
	 * {@code setCause()} would have, with every message in the cause chain, including
	 * suppressed exceptions, omitted.
	 */
	private void addStackTrace(LoggingEventBuilder event, HttpServletRequest request) {
		if (request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable exception) {
			event.addKeyValue("error.stack_trace", MessageRedactedStackTraces.stackTraceWithoutMessages(exception));
		}
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
