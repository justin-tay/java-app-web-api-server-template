package com.example.app.web.server.security;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Records ECS-compatible request lifecycle events.
 * <p>
 * This filter intentionally records only request metadata. It never logs request or
 * response bodies, cookies, credentials, or authorization headers. Query parameter values
 * are redacted when their names are in the configured redaction list.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);

	private static final String HTTP_REQUEST_ID = "http.request.id";

	private static final String REDACTED_VALUE = "[REDACTED]";

	private final Set<String> queryParameterRedactList;

	public RequestLoggingFilter(Collection<String> queryParameterRedactList) {
		this.queryParameterRedactList = queryParameterRedactList.stream()
			.map(parameter -> parameter.toLowerCase(Locale.ROOT))
			.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		Instant startedAt = Instant.now();
		logRequestReceived(request, startedAt);
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			if (!request.isAsyncStarted()) {
				logRequestCompleted(request, response, startedAt, Instant.now());
			}
			else {
				request.getAsyncContext()
					.addListener(new RequestLoggingAsyncListener(request, response, startedAt, MDC.get(HTTP_REQUEST_ID),
							username()));
			}
		}
	}

	private void logRequestReceived(HttpServletRequest request, Instant startedAt) {
		LoggingEventBuilder event = LOGGER.atInfo()
			.addKeyValue("event.category", "web")
			.addKeyValue("event.type", List.of("access", "start"))
			.addKeyValue("event.action", "receive_request")
			.addKeyValue("event.start", startedAt)
			.addKeyValue("http.request.method", request.getMethod())
			.addKeyValue("url.scheme", request.getScheme())
			.addKeyValue("server.address", request.getServerName())
			.addKeyValue("server.port", request.getServerPort())
			.addKeyValue("url.path", request.getRequestURI());
		addQueryParameters(event, request);
		addUser(event, username());
		event.log("HTTP request received");
	}

	private void logRequestCompleted(HttpServletRequest request, HttpServletResponse response, Instant startedAt,
			Instant completedAt) {
		logRequestCompleted(request, response, startedAt, completedAt, MDC.get(HTTP_REQUEST_ID), username());
	}

	private void logRequestCompleted(HttpServletRequest request, HttpServletResponse response, Instant startedAt,
			Instant completedAt, String requestId, String username) {
		LoggingEventBuilder event = LOGGER.atInfo()
			.addKeyValue("event.category", "web")
			.addKeyValue("event.type", List.of("access", "end"))
			.addKeyValue("event.action", "complete_request")
			.addKeyValue("event.start", startedAt)
			.addKeyValue("event.end", completedAt)
			.addKeyValue("event.duration", Duration.between(startedAt, completedAt).toNanos())
			.addKeyValue("http.request.method", request.getMethod())
			.addKeyValue("http.response.status_code", response.getStatus())
			.addKeyValue("event.outcome", outcome(response.getStatus()))
			.addKeyValue("url.scheme", request.getScheme())
			.addKeyValue("server.address", request.getServerName())
			.addKeyValue("server.port", request.getServerPort())
			.addKeyValue("url.path", request.getRequestURI())
			.addKeyValue("http.route", route(request));
		addQueryParameters(event, request);
		addRequestId(event, requestId);
		addUser(event, username);
		event.log("HTTP request completed");
	}

	private void addQueryParameters(LoggingEventBuilder event, HttpServletRequest request) {
		MultiValueMap<String, String> parameters = queryParameters(request);
		if (parameters.isEmpty()) {
			return;
		}
		event.addKeyValue("url.query_keys", List.copyOf(parameters.keySet()));
		String query = parameters.entrySet()
			.stream()
			.flatMap(entry -> entry.getValue()
				.stream()
				.map(value -> entry.getKey() + "=" + redactedValue(entry.getKey(), value)))
			.collect(Collectors.joining("&"));
		if (!query.isEmpty()) {
			event.addKeyValue("url.query", query);
		}
	}

	private String redactedValue(String parameterName, String value) {
		return this.queryParameterRedactList.contains(parameterName.toLowerCase(Locale.ROOT)) ? REDACTED_VALUE : value;
	}

	private MultiValueMap<String, String> queryParameters(HttpServletRequest request) {
		String query = request.getQueryString();
		if (query == null || query.isBlank()) {
			return new LinkedMultiValueMap<>();
		}
		return UriComponentsBuilder.newInstance().query(query).build().getQueryParams();
	}

	private void addRequestId(LoggingEventBuilder event, String requestId) {
		if (requestId != null && MDC.get(HTTP_REQUEST_ID) == null) {
			event.addKeyValue(HTTP_REQUEST_ID, requestId);
		}
	}

	private void addUser(LoggingEventBuilder event, String username) {
		if (username != null && MDC.get("user.name") == null) {
			event.addKeyValue("user.name", username);
		}
	}

	private String route(HttpServletRequest request) {
		Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		return (pattern != null) ? pattern.toString() : "UNKNOWN";
	}

	private String outcome(int status) {
		return (status < 400) ? "success" : "failure";
	}

	private String username() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return null;
		}
		return authentication.getName();
	}

	private final class RequestLoggingAsyncListener implements AsyncListener {

		private final HttpServletRequest request;

		private final HttpServletResponse response;

		private final Instant startedAt;

		private final String requestId;

		private final String username;

		private RequestLoggingAsyncListener(HttpServletRequest request, HttpServletResponse response, Instant startedAt,
				String requestId, String username) {
			this.request = request;
			this.response = response;
			this.startedAt = startedAt;
			this.requestId = requestId;
			this.username = username;
		}

		@Override
		public void onComplete(AsyncEvent event) {
			logRequestCompleted(this.request, this.response, this.startedAt, Instant.now(), this.requestId,
					this.username);
		}

		@Override
		public void onError(AsyncEvent event) {
		}

		@Override
		public void onStartAsync(AsyncEvent event) {
			event.getAsyncContext().addListener(this);
		}

		@Override
		public void onTimeout(AsyncEvent event) {
		}

	}

}
