package com.example.app.web.server.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityLoggingContextFilterTest {

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void addsDirectPeerAddressToMdcForTheRequest() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts");
		request.setRemoteAddr("192.0.2.10");
		MDC.put("user.name", "stale-user");

		new SecurityLoggingContextFilter(ClientIpResolver.none(), RequestIdResolver.none(),
				new SessionLifecycleAuditLogger())
			.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
				assertThat(MDC.get("source.ip")).isEqualTo("192.0.2.10");
				assertThat(MDC.get("http.request.id")).isNotBlank();
				assertThat(MDC.get("user.name")).isNull();
			});

		assertThat(MDC.get("source.ip")).isNull();
		assertThat(MDC.get("http.request.id")).isNull();
		assertThat(MDC.get("user.name")).isNull();
	}

	@Test
	void addsAValidatedTrustedClientIpToMdc() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts");
		request.setRemoteAddr("192.0.2.10");
		request.addHeader("True-Client-IP", "198.51.100.20");

		ClientIpResolver resolver = new TrustedHeaderClientIpResolver("True-Client-IP",
				java.util.List.of("192.0.2.0/24"));
		new SecurityLoggingContextFilter(resolver, RequestIdResolver.none(), new SessionLifecycleAuditLogger())
			.doFilter(request, new MockHttpServletResponse(),
					(servletRequest, servletResponse) -> assertThat(MDC.get("client.ip")).isEqualTo("198.51.100.20"));
	}

	@Test
	void ignoresTheClientIpHeaderFromAnUntrustedPeer() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts");
		request.setRemoteAddr("203.0.113.10");
		request.addHeader("True-Client-IP", "198.51.100.20");

		ClientIpResolver resolver = new TrustedHeaderClientIpResolver("True-Client-IP",
				java.util.List.of("192.0.2.0/24"));
		new SecurityLoggingContextFilter(resolver, RequestIdResolver.none(), new SessionLifecycleAuditLogger())
			.doFilter(request, new MockHttpServletResponse(),
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

		new SecurityLoggingContextFilter(ClientIpResolver.none(), new CloudFrontRequestIdResolver(),
				new SessionLifecycleAuditLogger())
			.doFilter(request, new MockHttpServletResponse(), (servletRequest,
					servletResponse) -> assertThat(MDC.get("http.request.id")).contains("cloudfront-request-id"));
	}

}
