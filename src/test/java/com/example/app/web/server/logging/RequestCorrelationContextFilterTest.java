package com.example.app.web.server.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.app.web.server.logging.client.ClientIpResolver;
import com.example.app.web.server.logging.client.CloudFrontViewerAddressClientIpResolver;
import com.example.app.web.server.logging.client.TrustedHeaderClientIpResolver;
import com.example.app.web.server.logging.client.XForwardedForClientIpResolver;
import com.example.app.web.server.logging.request.CloudFrontRequestIdResolver;
import com.example.app.web.server.logging.request.RequestIdResolver;

class RequestCorrelationContextFilterTest {

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void addsDirectPeerAddressToMdcForTheRequest() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts");
		request.setRemoteAddr("192.0.2.10");

		new RequestCorrelationContextFilter(ClientIpResolver.none(), RequestIdResolver.none()).doFilter(request,
				new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
					assertThat(MDC.get("source.ip")).isEqualTo("192.0.2.10");
					assertThat(MDC.get("http.request.id")).isNotBlank();
				});

		assertThat(MDC.get("source.ip")).isNull();
		assertThat(MDC.get("http.request.id")).isNull();
	}

	@Test
	void doesNotClearMdcEntriesEstablishedBeforeItRuns() throws Exception {
		MDC.put("trace.id", "already-established-before-this-filter-runs");
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts");

		try {
			new RequestCorrelationContextFilter(ClientIpResolver.none(), RequestIdResolver.none()).doFilter(request,
					new MockHttpServletResponse(), (servletRequest, servletResponse) -> assertThat(MDC.get("trace.id"))
						.isEqualTo("already-established-before-this-filter-runs"));
		}
		finally {
			MDC.remove("trace.id");
		}
	}

	@Test
	void doesNotClearMdcEntriesAddedDuringTheRequestOnExit() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts");

		try {
			new RequestCorrelationContextFilter(ClientIpResolver.none(), RequestIdResolver.none()).doFilter(request,
					new MockHttpServletResponse(),
					(servletRequest, servletResponse) -> MDC.put("user.name", "someone-set-during-the-request"));

			assertThat(MDC.get("user.name")).isEqualTo("someone-set-during-the-request");
		}
		finally {
			MDC.remove("user.name");
		}
	}

	@Test
	void addsAValidatedTrustedClientIpToMdc() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts");
		request.setRemoteAddr("192.0.2.10");
		request.addHeader("True-Client-IP", "198.51.100.20");

		ClientIpResolver resolver = new TrustedHeaderClientIpResolver("True-Client-IP",
				java.util.List.of("192.0.2.0/24"));
		new RequestCorrelationContextFilter(resolver, RequestIdResolver.none()).doFilter(request,
				new MockHttpServletResponse(),
				(servletRequest, servletResponse) -> assertThat(MDC.get("client.ip")).isEqualTo("198.51.100.20"));
	}

	@Test
	void ignoresTheClientIpHeaderFromAnUntrustedPeer() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts");
		request.setRemoteAddr("203.0.113.10");
		request.addHeader("True-Client-IP", "198.51.100.20");

		ClientIpResolver resolver = new TrustedHeaderClientIpResolver("True-Client-IP",
				java.util.List.of("192.0.2.0/24"));
		new RequestCorrelationContextFilter(resolver, RequestIdResolver.none()).doFilter(request,
				new MockHttpServletResponse(),
				(servletRequest, servletResponse) -> assertThat(MDC.get("client.ip")).isNull());
	}

	@Test
	void resolvesClientIpFromTheFirstUntrustedAddressInAnXForwardedForChain() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts");
		request.setRemoteAddr("192.0.2.10");
		request.addHeader("X-Forwarded-For", "198.51.100.20, 192.0.2.20");

		assertThat(new XForwardedForClientIpResolver(java.util.List.of("192.0.2.0/24")).resolve(request))
			.contains("198.51.100.20");
	}

	@Test
	void resolvesClientIpFromCloudFrontViewerAddress() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts");
		request.setRemoteAddr("192.0.2.10");
		request.addHeader("CloudFront-Viewer-Address", "198.51.100.20:12345");

		assertThat(new CloudFrontViewerAddressClientIpResolver(java.util.List.of("192.0.2.0/24")).resolve(request))
			.contains("198.51.100.20");
	}

	@Test
	void usesCloudFrontRequestIdWhenTheResolverIsSelected() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts");
		request.addHeader("X-Amz-Cf-Id", "cloudfront-request-id");

		new RequestCorrelationContextFilter(ClientIpResolver.none(), new CloudFrontRequestIdResolver())
			.doFilter(request, new MockHttpServletResponse(), (servletRequest,
					servletResponse) -> assertThat(MDC.get("http.request.id")).contains("cloudfront-request-id"));
	}

}
