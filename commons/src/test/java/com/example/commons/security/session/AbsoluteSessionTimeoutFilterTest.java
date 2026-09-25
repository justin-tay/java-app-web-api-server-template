package com.example.commons.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/**
 * Tests for {@link AbsoluteSessionTimeoutFilter}.
 */
class AbsoluteSessionTimeoutFilterTest {

	private static final Duration TIMEOUT = Duration.ofHours(12);

	private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/account");

	private final MockHttpServletResponse response = new MockHttpServletResponse();

	private final MockFilterChain filterChain = new MockFilterChain();

	private final MockHttpSession session = new MockHttpSession();

	@Test
	void invalidatesSessionAtAbsoluteTimeout() throws Exception {
		this.request.setSession(this.session);

		filterAt(created().plus(TIMEOUT)).doFilter(this.request, this.response, this.filterChain);

		assertThat(this.session.isInvalid()).isTrue();
		assertThat(this.filterChain.getRequest()).isSameAs(this.request);
	}

	@Test
	void retainsSessionBeforeAbsoluteTimeout() throws Exception {
		this.request.setSession(this.session);

		filterAt(created().plus(TIMEOUT).minusMillis(1)).doFilter(this.request, this.response, this.filterChain);

		assertThat(this.session.isInvalid()).isFalse();
		assertThat(this.filterChain.getRequest()).isSameAs(this.request);
	}

	@Test
	void passesARequestWithoutASessionThrough() throws Exception {
		filterAt(created().plus(TIMEOUT)).doFilter(this.request, this.response, this.filterChain);

		assertThat(this.request.getSession(false)).isNull();
		assertThat(this.filterChain.getRequest()).isSameAs(this.request);
	}

	private Instant created() {
		return Instant.ofEpochMilli(this.session.getCreationTime());
	}

	private static AbsoluteSessionTimeoutFilter filterAt(Instant now) {
		return new AbsoluteSessionTimeoutFilter(TIMEOUT, Clock.fixed(now, ZoneOffset.UTC));
	}

}
