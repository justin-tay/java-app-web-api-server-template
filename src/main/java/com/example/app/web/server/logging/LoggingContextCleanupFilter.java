package com.example.app.web.server.logging;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guarantees no MDC entry survives past the end of a request, regardless of which filter
 * or library added it.
 * <p>
 * Registered as the outermost filter (see {@code WebSecurityConfiguration}, ahead of even
 * Micrometer Tracing's observation filter), so its cleanup is the last thing that runs
 * before control returns to the servlet container, and a reused servlet thread cannot
 * associate a later, unrelated event with this request. Every MDC-adding component in
 * this application already scopes and removes its own entries; this filter is a safety
 * net for anything that does not, not the primary cleanup mechanism, and it does not
 * establish any MDC entries itself. See docs/adr/0012.
 */
@Component
public class LoggingContextCleanupFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			MDC.clear();
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

}
