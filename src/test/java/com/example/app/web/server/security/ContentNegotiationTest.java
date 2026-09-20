package com.example.app.web.server.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

class ContentNegotiationTest {

	@Test
	void acceptsHtmlIsTrueWhenAcceptHeaderContainsTextHtml() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Accept", "text/html,application/xhtml+xml");

		assertThat(ContentNegotiation.acceptsHtml(request)).isTrue();
	}

	@Test
	void acceptsHtmlIsFalseWhenAcceptHeaderIsAbsentOrNonHtml() {
		MockHttpServletRequest requestWithoutHeader = new MockHttpServletRequest();
		MockHttpServletRequest requestWithJson = new MockHttpServletRequest();
		requestWithJson.addHeader("Accept", MediaType.APPLICATION_JSON_VALUE);

		assertThat(ContentNegotiation.acceptsHtml(requestWithoutHeader)).isFalse();
		assertThat(ContentNegotiation.acceptsHtml(requestWithJson)).isFalse();
	}

}
