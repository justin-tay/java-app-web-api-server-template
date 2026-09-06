package com.example.app.web.server.api;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * A {@link ResponseEntityExceptionHandler}.
 */
@ControllerAdvice
public class ApiResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApiResponseEntityExceptionHandler.class);

	private static final String GENERIC_ERROR_DETAIL = "The request could not be completed.";

	@ExceptionHandler(RestClientResponseException.class)
	public ResponseEntity<ProblemDetail> handleRestClientResponseException(RestClientResponseException ex,
			HttpServletRequest request) {
		HttpStatusCode statusCode = ex.getStatusCode();
		if (statusCode.is5xxServerError()) {
			logRequestProcessingFailure(ex, request, statusCode.value());
		}
		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(statusCode, GENERIC_ERROR_DETAIL);
		return ResponseEntity.status(statusCode).body(problemDetail);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ProblemDetail> handleUnexpectedException(Exception ex, HttpServletRequest request) {
		logRequestProcessingFailure(ex, request, HttpStatus.INTERNAL_SERVER_ERROR.value());
		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				GENERIC_ERROR_DETAIL);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
	}

	private void logRequestProcessingFailure(Exception ex, HttpServletRequest request, int responseStatusCode) {
		LOGGER.atError()
			.addKeyValue("event.category", List.of("web"))
			.addKeyValue("event.type", List.of("error"))
			.addKeyValue("event.action", "process_request")
			.addKeyValue("event.outcome", "failure")
			.addKeyValue("http.response.status_code", responseStatusCode)
			.addKeyValue("url.path", request.getRequestURI())
			.addKeyValue("error.type", ex.getClass().getName())
			.setCause(ex)
			.log("Request processing failed");
	}

}
