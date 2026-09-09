package com.example.app.web.server.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AbsoluteSessionTimeoutFilter}.
 */
class AbsoluteSessionTimeoutFilterTest {

	private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");

	@Test
	void invalidatesSessionAtAbsoluteTimeout() throws Exception {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		HttpSession session = mock(HttpSession.class);
		FilterChain filterChain = mock(FilterChain.class);
		when(request.getSession(false)).thenReturn(session);
		when(session.getCreationTime()).thenReturn(NOW.minus(Duration.ofHours(12)).toEpochMilli());

		filter().doFilter(request, response, filterChain);

		verify(session).invalidate();
		verify(filterChain).doFilter(request, response);
	}

	@Test
	void retainsSessionBeforeAbsoluteTimeout() throws Exception {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		HttpSession session = mock(HttpSession.class);
		FilterChain filterChain = mock(FilterChain.class);
		when(request.getSession(false)).thenReturn(session);
		when(session.getCreationTime()).thenReturn(NOW.minus(Duration.ofHours(12)).plusMillis(1).toEpochMilli());

		filter().doFilter(request, response, filterChain);

		verify(session, never()).invalidate();
		verify(filterChain).doFilter(request, response);
	}

	private AbsoluteSessionTimeoutFilter filter() {
		return new AbsoluteSessionTimeoutFilter(Duration.ofHours(12), Clock.fixed(NOW, ZoneOffset.UTC));
	}

}
