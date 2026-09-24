package com.example.commons.security;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;

/**
 * Distinguishes a browser navigation from a non-browser client at points in the security
 * filter chain that run outside Spring MVC's dispatch, where a
 * {@code ContentNegotiationStrategy} bean is not available.
 */
public final class ContentNegotiation {

	private ContentNegotiation() {
	}

	/**
	 * Reports whether the request's {@code Accept} header indicates a browser navigation,
	 * so the caller can choose an HTML redirect over a Problem Details response.
	 * @param request the request
	 * @return {@code true} if the request accepts {@code text/html}
	 */
	public static boolean acceptsHtml(HttpServletRequest request) {
		String accept = request.getHeader("Accept");
		return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
	}

}
