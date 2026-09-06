package com.example.app.web.server.security;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds request-scoped ECS fields to the logging context.
 */
@Component
public class SecurityLoggingContextFilter extends OncePerRequestFilter {

	private static final String CLOUDFRONT_REQUEST_ID_HEADER = "X-Amz-Cf-Id";

	private static final String HTTP_REQUEST_ID = "http.request.id";

	private static final String REQUEST_ID_ATTRIBUTE = SecurityLoggingContextFilter.class.getName() + ".REQUEST_ID";

	private static final String USER_NAME = "user.name";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		try (MDC.MDCCloseable requestId = MDC.putCloseable(HTTP_REQUEST_ID, requestId(request))) {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (isAuthenticated(authentication)) {
				try (MDC.MDCCloseable userName = MDC.putCloseable(USER_NAME, authentication.getName())) {
					filterChain.doFilter(request, response);
				}
			}
			else {
				filterChain.doFilter(request, response);
			}
		}
	}

	@Override
	protected boolean shouldNotFilterAsyncDispatch() {
		return false;
	}

	@Override
	protected boolean shouldNotFilterErrorDispatch() {
		return false;
	}

	private boolean isAuthenticated(Authentication authentication) {
		return authentication != null && authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken);
	}

	private String requestId(HttpServletRequest request) {
		Object existingRequestId = request.getAttribute(REQUEST_ID_ATTRIBUTE);
		if (existingRequestId instanceof String requestId) {
			return requestId;
		}
		String cloudFrontRequestId = request.getHeader(CLOUDFRONT_REQUEST_ID_HEADER);
		String requestId = (cloudFrontRequestId != null && !cloudFrontRequestId.isBlank()) ? cloudFrontRequestId
				: UUID.randomUUID().toString();
		request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
		return requestId;
	}

}
