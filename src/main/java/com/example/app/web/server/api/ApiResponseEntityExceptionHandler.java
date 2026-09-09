package com.example.app.web.server.api;

import java.util.List;
import java.util.Map;
import java.net.URI;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.context.request.WebRequest;

import com.example.app.web.server.api.admin.ConflictException;
import com.example.app.web.server.api.admin.ResourceNotFoundException;

/**
 * A {@link ResponseEntityExceptionHandler}.
 */
@ControllerAdvice
public class ApiResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApiResponseEntityExceptionHandler.class);

	private static final String GENERIC_ERROR_DETAIL = "The request could not be completed.";

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problemDetail = problemDetail(HttpStatus.BAD_REQUEST, "One or more fields are invalid.",
				ProblemTypes.VALIDATION_FAILED);
		problemDetail.setTitle("Validation failed");
		problemDetail.setProperty("errors",
				ex.getBindingResult()
					.getFieldErrors()
					.stream()
					.map(error -> Map.of("code", error.getCode(), "message", error.getDefaultMessage(), "source",
							Map.of("pointer", "/" + error.getField())))
					.toList());
		return ResponseEntity.badRequest().body(problemDetail);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problemDetail = problemDetail(HttpStatus.BAD_REQUEST, "Request content is invalid.",
				ProblemTypes.MALFORMED_REQUEST);
		problemDetail.setTitle("Validation failed");
		problemDetail.setProperty("errors",
				List.of(Map.of("code", "MalformedRequest", "message", "Request content is invalid.")));
		return ResponseEntity.badRequest().body(problemDetail);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
		ProblemDetail problemDetail = problemDetail(HttpStatus.BAD_REQUEST, "One or more fields are invalid.",
				ProblemTypes.VALIDATION_FAILED);
		problemDetail.setTitle("Validation failed");
		problemDetail.setProperty("errors",
				ex.getConstraintViolations()
					.stream()
					.map(error -> Map.of("code",
							error.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(), "message",
							error.getMessage()))
					.toList());
		return ResponseEntity.badRequest().body(problemDetail);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(problemDetail(HttpStatus.NOT_FOUND, ex.getMessage(), ProblemTypes.RESOURCE_NOT_FOUND));
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<ProblemDetail> handleConflict(ConflictException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(problemDetail(HttpStatus.CONFLICT, ex.getMessage(), ProblemTypes.RESOURCE_CONFLICT));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
		ProblemDetail problemDetail = problemDetail(HttpStatus.BAD_REQUEST, ex.getMessage(),
				ProblemTypes.VALIDATION_FAILED);
		problemDetail.setTitle("Validation failed");
		problemDetail.setProperty("errors", List.of(Map.of("code", "InvalidRequest", "message", ex.getMessage())));
		return ResponseEntity.badRequest().body(problemDetail);
	}

	@ExceptionHandler(RestClientResponseException.class)
	public ResponseEntity<ProblemDetail> handleRestClientResponseException(RestClientResponseException ex,
			HttpServletRequest request) {
		HttpStatusCode statusCode = ex.getStatusCode();
		if (statusCode.is5xxServerError()) {
			logRequestProcessingFailure(ex, request, statusCode.value());
		}
		ProblemDetail problemDetail = problemDetail(statusCode, GENERIC_ERROR_DETAIL,
				ProblemTypes.UPSTREAM_RESPONSE_FAILED);
		return ResponseEntity.status(statusCode).body(problemDetail);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ProblemDetail> handleUnexpectedException(Exception ex, HttpServletRequest request) {
		logRequestProcessingFailure(ex, request, HttpStatus.INTERNAL_SERVER_ERROR.value());
		ProblemDetail problemDetail = problemDetail(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_ERROR_DETAIL,
				ProblemTypes.INTERNAL_ERROR);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
	}

	@Override
	protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		return ResponseEntity.status(status).body(problemDetail(status, null, ProblemTypes.ROUTE_NOT_FOUND));
	}

	@Override
	protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return ResponseEntity.status(status).body(problemDetail(status, null, ProblemTypes.METHOD_NOT_ALLOWED));
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

	private ProblemDetail problemDetail(HttpStatusCode status, String detail, URI type) {
		ProblemDetail problemDetail = (detail == null) ? ProblemDetail.forStatus(status)
				: ProblemDetail.forStatusAndDetail(status, detail);
		problemDetail.setType(type);
		return problemDetail;
	}

}
