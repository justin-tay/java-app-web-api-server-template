package com.example.app.web.server.logging;

import java.io.IOException;

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
 * Adds the authenticated actor to the logging context.
 * <p>
 * {@code http.request.id}, {@code source.ip}, and {@code client.ip} are established
 * earlier, by {@code RequestCorrelationContextFilter}, since they must be present even
 * when Spring Security's {@code HttpFirewall} rejects a request before this filter runs;
 * see docs/adr/0010.
 */
@Component
public class AuthenticatedUserLoggingContextFilter extends OncePerRequestFilter {

	private static final String USER_NAME = "user.name";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
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

}
