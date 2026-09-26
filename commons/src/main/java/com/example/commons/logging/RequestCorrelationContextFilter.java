package com.example.commons.logging;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.commons.logging.client.ClientIpResolver;
import com.example.commons.logging.request.RequestIdResolver;

/**
 * Establishes request correlation MDC fields before Spring Security's filter chain runs.
 * <p>
 * Registered as a plain servlet filter ahead of {@code springSecurityFilterChain} (see
 * {@link LoggingAutoConfiguration}), rather than through {@code HttpSecurity},
 * specifically so {@code http.request.id}, {@code source.ip}, and {@code client.ip} are
 * present even when Spring Security's {@code HttpFirewall} rejects a request before its
 * own internal filter chain is ever invoked; see docs/adr/0012. It scopes and removes
 * only its own MDC entries; {@link LoggingContextCleanupFilter}, the outermost filter, is
 * the one guaranteed final cleanup for the whole request.
 */
public class RequestCorrelationContextFilter extends OncePerRequestFilter {

	private static final String REQUEST_ID_ATTRIBUTE = RequestCorrelationContextFilter.class.getName() + ".REQUEST_ID";

	private final ClientIpResolver clientIpResolver;

	private final RequestIdResolver requestIdResolver;

	public RequestCorrelationContextFilter(ClientIpResolver clientIpResolver, RequestIdResolver requestIdResolver) {
		this.clientIpResolver = clientIpResolver;
		this.requestIdResolver = requestIdResolver;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		try (MDC.MDCCloseable requestId = MDC.putCloseable(EcsFields.HTTP_REQUEST_ID, requestId(request));
				MDC.MDCCloseable sourceIp = MDC.putCloseable(EcsFields.SOURCE_IP, request.getRemoteAddr())) {
			String clientIp = this.clientIpResolver.resolve(request).orElse(null);
			if (clientIp != null) {
				MDC.put(EcsFields.CLIENT_IP, clientIp);
			}
			try {
				filterChain.doFilter(request, response);
			}
			finally {
				MDC.remove(EcsFields.CLIENT_IP);
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

	private String requestId(HttpServletRequest request) {
		Object existingRequestId = request.getAttribute(REQUEST_ID_ATTRIBUTE);
		if (existingRequestId instanceof String requestId) {
			return requestId;
		}
		String requestId = this.requestIdResolver.resolve(request).orElseGet(() -> UUID.randomUUID().toString());
		request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
		return requestId;
	}

}
