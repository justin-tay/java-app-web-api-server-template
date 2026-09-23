package com.example.app.web.server.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.example.app.web.server.test.RestTestClientITSupport;

/**
 * Confirms trace correlation is present on real, rendered request lifecycle log output,
 * using the full application (real filter chain, real Micrometer Tracing MDC population,
 * real structured logging pipeline) rather than the customizer in isolation.
 */
@ExtendWith(OutputCaptureExtension.class)
class TraceCorrelationJsonMembersCustomizerIntegrationTest extends RestTestClientITSupport {

	@Test
	void requestLifecycleEventsCarryTraceAndSpanIds(CapturedOutput output) {
		this.restTestClient.get().uri("/account").exchange();

		assertThat(output.getOut()).containsPattern("\"trace\":\\{\"id\":\"[0-9a-f]{32}\"\\}");
		assertThat(output.getOut()).containsPattern("\"span\":\\{\"id\":\"[0-9a-f]{16}\"\\}");
	}

	/**
	 * Both {@code RequestCorrelationContextFilter} (which sets {@code http.request.id})
	 * and Micrometer Tracing's observation filter (which sets {@code traceId}/
	 * {@code spanId}) are registered ahead of Spring Security's filter chain, so both
	 * correlation mechanisms survive the HTTP firewall rejecting a request before its own
	 * internal filter chain, including {@code AuthenticatedUserLoggingContextFilter}, is
	 * ever invoked; see docs/adr/0012.
	 */
	@Test
	void firewallRejectedRequestsCarryTraceSpanAndHttpRequestIds(CapturedOutput output) {
		this.restTestClient.get().uri("/admin/users/%2e%2e/secrets").exchange();

		String rejectRequestLine = output.getAll()
			.lines()
			.filter((line) -> line.contains("\"action\":\"reject_request\""))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No reject_request event was logged"));
		assertThat(rejectRequestLine).containsPattern("\"trace\":\\{\"id\":\"[0-9a-f]{32}\"\\}");
		assertThat(rejectRequestLine).containsPattern("\"span\":\\{\"id\":\"[0-9a-f]{16}\"\\}");
		assertThat(rejectRequestLine).containsPattern("\"http\":\\{\"request\":\\{\"id\":\"[0-9a-f-]{36}\"");
	}

}
