package com.example.commons.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthenticatedUserLoggingContextFilterTest {

	@AfterEach
	void clearContext() {
		MDC.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void addsTheAuthenticatedUserNameToMdcForTheRequest() throws Exception {
		SecurityContextHolder.getContext()
			.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("alice", "N/A", null));

		new AuthenticatedUserLoggingContextFilter().doFilter(new MockHttpServletRequest("GET", "/accounts"),
				new MockHttpServletResponse(),
				(servletRequest, servletResponse) -> assertThat(MDC.get("user.name")).isEqualTo("alice"));

		assertThat(MDC.get("user.name")).isNull();
	}

	@Test
	void doesNotAddAnAnonymousUserNameToMdc() throws Exception {
		SecurityContextHolder.getContext()
			.setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
					java.util.List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

		new AuthenticatedUserLoggingContextFilter().doFilter(new MockHttpServletRequest("GET", "/accounts"),
				new MockHttpServletResponse(),
				(servletRequest, servletResponse) -> assertThat(MDC.get("user.name")).isNull());
	}

}
