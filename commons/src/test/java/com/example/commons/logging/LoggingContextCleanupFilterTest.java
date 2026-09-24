package com.example.commons.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoggingContextCleanupFilterTest {

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void clearsMdcEntriesAddedDuringTheRequestOnExitSoAReusedThreadStartsClean() throws Exception {
		new LoggingContextCleanupFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
				(servletRequest, servletResponse) -> MDC.put("user.name", "someone-set-during-the-request"));

		assertThat(MDC.get("user.name")).isNull();
	}

	@Test
	void clearsMdcEntriesThatExistedBeforeTheRequestToo() throws Exception {
		MDC.put("stale.key", "leftover-from-a-previous-request");

		new LoggingContextCleanupFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
				(servletRequest, servletResponse) -> {
				});

		assertThat(MDC.get("stale.key")).isNull();
	}

	@Test
	void clearsMdcWhenTheDownstreamChainThrows() {
		org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
				() -> new LoggingContextCleanupFilter().doFilter(new MockHttpServletRequest(),
						new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
							MDC.put("user.name", "someone-set-during-the-request");
							throw new java.io.IOException("downstream failure");
						}));

		assertThat(MDC.get("user.name")).isNull();
	}

}
