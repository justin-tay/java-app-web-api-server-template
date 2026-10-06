package com.example.commons.web.problem;

import java.util.List;
import java.util.Map;
import java.net.URI;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.TypeMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.NativeWebRequest;

import com.example.commons.web.problem.ConflictException;
import com.example.commons.web.problem.ResourceNotFoundException;
import com.example.commons.logging.LoggedUrlPath;
import com.example.commons.security.authentication.oidc.ReauthenticationChallenge;

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
		HttpServletRequest servletRequest = ((NativeWebRequest) request).getNativeRequest(HttpServletRequest.class);
		ex.getBindingResult()
			.getFieldErrors()
			.forEach(error -> logInputValidationFailure(servletRequest, ex, error.getCode(), error.getField()));
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
		logInputValidationFailure(((NativeWebRequest) request).getNativeRequest(HttpServletRequest.class), ex,
				"MalformedRequest", null);
		ProblemDetail problemDetail = problemDetail(HttpStatus.BAD_REQUEST, "Request content is invalid.",
				ProblemTypes.MALFORMED_REQUEST);
		problemDetail.setTitle("Validation failed");
		problemDetail.setProperty("errors",
				List.of(Map.of("code", "MalformedRequest", "message", "Request content is invalid.")));
		return ResponseEntity.badRequest().body(problemDetail);
	}

	/**
	 * Answers a path variable, request parameter or similar that cannot be converted to
	 * its type, such as a malformed UUID, like any other invalid input. The rejected
	 * value is never echoed.
	 */
	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		logInputValidationFailure(((NativeWebRequest) request).getNativeRequest(HttpServletRequest.class), ex,
				"TypeMismatch", ex.getPropertyName());
		ProblemDetail problemDetail = problemDetail(HttpStatus.BAD_REQUEST, "One or more fields are invalid.",
				ProblemTypes.VALIDATION_FAILED);
		problemDetail.setTitle("Validation failed");
		problemDetail.setProperty("errors",
				List.of(Map.of("code", "TypeMismatch", "message", "The value is not valid for its type.")));
		return ResponseEntity.badRequest().body(problemDetail);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
			HttpServletRequest request) {
		ex.getConstraintViolations()
			.forEach(error -> logInputValidationFailure(request, ex,
					error.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
					error.getPropertyPath().toString()));
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

	/**
	 * Answers a request whose user must authenticate again with a 401 carrying the
	 * allowed authentication age, and logs it as an access denial.
	 */
	@ExceptionHandler(ReauthenticationRequiredException.class)
	public ResponseEntity<ProblemDetail> handleReauthenticationRequired(ReauthenticationRequiredException ex,
			HttpServletRequest request) {
		LOGGER.atWarn()
			.addKeyValue("event.category", List.of("web", "api"))
			.addKeyValue("event.type", List.of("access", "denied"))
			.addKeyValue("event.action", "authorize_access")
			.addKeyValue("event.outcome", "failure")
			.addKeyValue("event.reason", "reauthentication_required")
			.addKeyValue("http.response.status_code", HttpStatus.UNAUTHORIZED.value())
			.addKeyValue("url.path", LoggedUrlPath.of(request.getRequestURI()))
			.log("Recent authentication required");
		ProblemDetail problemDetail = problemDetail(HttpStatus.UNAUTHORIZED, ex.getMessage(),
				ProblemTypes.REAUTHENTICATION_REQUIRED);
		problemDetail.setProperty("max_age", ex.getMaxAge().toSeconds());
		ReauthenticationChallenge.members(SecurityContextHolder.getContext().getAuthentication())
			.forEach(problemDetail::setProperty);
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problemDetail);
	}

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<ProblemDetail> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
		logInputValidationFailure(request, ex, "InvalidRequest", null);
		ProblemDetail problemDetail = problemDetail(HttpStatus.BAD_REQUEST, ex.getMessage(),
				ProblemTypes.VALIDATION_FAILED);
		problemDetail.setTitle("Validation failed");
		problemDetail.setProperty("errors",
				List.of(ex.getField() == null ? Map.of("code", "InvalidRequest", "message", ex.getMessage())
						: Map.of("code", "InvalidRequest", "message", ex.getMessage(), "source",
								Map.of("pointer", "/" + ex.getField()))));
		return ResponseEntity.badRequest().body(problemDetail);
	}

	/**
	 * Handles the JDK's general-purpose "invalid argument" exception. Unlike
	 * {@link BadRequestException}, an {@link IllegalArgumentException} is not necessarily
	 * thrown with an external audience in mind. Consistent with every other
	 * {@code validate_input} event, its message is never logged or included in the
	 * response; only its exception type is recorded.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex,
			HttpServletRequest request) {
		logInputValidationFailure(request, ex, ex.getClass().getSimpleName(), null);
		ProblemDetail problemDetail = problemDetail(HttpStatus.BAD_REQUEST, GENERIC_ERROR_DETAIL,
				ProblemTypes.VALIDATION_FAILED);
		problemDetail.setTitle("Validation failed");
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

	/**
	 * Rethrows an authorization failure raised inside a handler, such as a
	 * {@code @PreAuthorize} denial, so it is not caught by
	 * {@link #handleUnexpectedException(Exception, HttpServletRequest)} and answered as a
	 * 500. The exception leaves the {@code DispatcherServlet} and reaches Spring
	 * Security's {@code ExceptionTranslationFilter}, which answers it the same way as a
	 * URL authorization failure: a 403 from {@code ProblemDetailAccessDeniedHandler} for
	 * an authenticated user, or the authentication entry point for an anonymous one.
	 * @param ex the authorization failure
	 * @throws AccessDeniedException always
	 */
	@ExceptionHandler(AccessDeniedException.class)
	public void rethrowAccessDenied(AccessDeniedException ex) {
		throw ex;
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
			.addKeyValue("url.path", LoggedUrlPath.of(request.getRequestURI()))
			.setCause(ex)
			.log("Request processing failed");
	}

	private void logInputValidationFailure(HttpServletRequest request, Exception exception, String errorCode,
			String validationField) {
		LoggingEventBuilder event = LOGGER.atWarn()
			.addKeyValue("event.category", List.of("web"))
			.addKeyValue("event.type", List.of("error"))
			.addKeyValue("event.action", "validate_input")
			.addKeyValue("event.outcome", "failure")
			.addKeyValue("http.response.status_code", HttpStatus.BAD_REQUEST.value())
			.addKeyValue("url.path", LoggedUrlPath.of(request.getRequestURI()))
			// setCause(exception) is deliberately not used here: this is an expected,
			// client-driven failure, not a bug, so a stack trace is noise, and the
			// exception message can echo rejected request content (see
			// docs/system-design/08-crosscutting-concepts/06-logging-and-monitoring/schema.md,
			// "Sensitive-data policy").
			.addKeyValue("error.type", exception.getClass().getName())
			.addKeyValue("error.code", errorCode);
		if (validationField != null) {
			event.addKeyValue("validation.field", validationField);
		}
		event.log("Input validation failed");
	}

	private ProblemDetail problemDetail(HttpStatusCode status, String detail, URI type) {
		ProblemDetail problemDetail = (detail == null) ? ProblemDetail.forStatus(status)
				: ProblemDetail.forStatusAndDetail(status, detail);
		problemDetail.setType(type);
		return problemDetail;
	}

}
