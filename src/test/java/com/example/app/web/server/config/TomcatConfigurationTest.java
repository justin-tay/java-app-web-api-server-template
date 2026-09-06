package com.example.app.web.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.core.JreMemoryLeakPreventionListener;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.assertj.RestTestClientResponse;

import com.example.app.web.server.test.RestTestClientITSupport;

/**
 * Tests the Tomcat configuration.
 */
class TomcatConfigurationTest extends RestTestClientITSupport {

	@Autowired
	private ServletWebServerApplicationContext applicationContext;

	@Test
	void discardsRequestAndResponseFacadesAfterEachRequest() {
		assertThat(this.applicationContext.getWebServer()).isInstanceOf(TomcatWebServer.class);
		TomcatWebServer tomcatWebServer = (TomcatWebServer) this.applicationContext.getWebServer();

		assertThat(tomcatWebServer.getTomcat().getConnector().getProperty("discardFacades")).isEqualTo("true");
	}

	@Test
	void enablesStrictServletComplianceBeforeEmbeddedTomcatStarts() {
		assertThat(System.getProperty("org.apache.catalina.STRICT_SERVLET_COMPLIANCE")).isEqualTo("true");
		assertThat(Globals.STRICT_SERVLET_COMPLIANCE).isTrue();
	}

	@Test
	void doesNotDiscloseTomcatOrApplicationServerInformation() {
		assertThat(this.applicationContext.getWebServer()).isInstanceOf(TomcatWebServer.class);
		TomcatWebServer tomcatWebServer = (TomcatWebServer) this.applicationContext.getWebServer();

		assertThat(tomcatWebServer.getTomcat().getConnector().getXpoweredBy()).isFalse();
		assertThat(tomcatWebServer.getTomcat().getConnector().getProperty("server")).isNull();
		assertThat(tomcatWebServer.getTomcat().getConnector().getProperty("serverRemoveAppProvidedValues"))
			.isEqualTo(true);
	}

	@Test
	void rejectsTraceRequests() {
		assertThat(this.applicationContext.getWebServer()).isInstanceOf(TomcatWebServer.class);
		TomcatWebServer tomcatWebServer = (TomcatWebServer) this.applicationContext.getWebServer();

		assertThat(tomcatWebServer.getTomcat().getConnector().getAllowTrace()).isFalse();
	}

	@Test
	void enablesJreMemoryLeakPrevention() {
		assertThat(this.applicationContext.getWebServer()).isInstanceOf(TomcatWebServer.class);
		TomcatWebServer tomcatWebServer = (TomcatWebServer) this.applicationContext.getWebServer();

		assertThat(tomcatWebServer.getTomcat().getServer().findLifecycleListeners())
			.anyMatch(JreMemoryLeakPreventionListener.class::isInstance);
	}

	@Test
	void rejectsAdditionalPathDelimiters() {
		assertThat(this.applicationContext.getWebServer()).isInstanceOf(TomcatWebServer.class);
		TomcatWebServer tomcatWebServer = (TomcatWebServer) this.applicationContext.getWebServer();

		assertThat(tomcatWebServer.getTomcat().getConnector().getAllowBackslash()).isFalse();
		assertThat(tomcatWebServer.getTomcat().getConnector().getEncodedSolidusHandling()).isEqualTo("reject");
		assertThat(tomcatWebServer.getTomcat().getConnector().getEncodedReverseSolidusHandling()).isEqualTo("reject");
	}

	@Test
	void disallowsSymbolicLinksInWebApplicationResources() {
		assertThat(this.applicationContext.getWebServer()).isInstanceOf(TomcatWebServer.class);
		TomcatWebServer tomcatWebServer = (TomcatWebServer) this.applicationContext.getWebServer();
		Context context = (Context) tomcatWebServer.getTomcat().getHost().findChild("");

		assertThat(context).isNotNull();
		assertThat(context.getResources().getAllowLinking()).isFalse();
	}

	@Test
	void doesNotRunTheWebApplicationAsPrivileged() {
		assertThat(this.applicationContext.getWebServer()).isInstanceOf(TomcatWebServer.class);
		TomcatWebServer tomcatWebServer = (TomcatWebServer) this.applicationContext.getWebServer();
		Context context = (Context) tomcatWebServer.getTomcat().getHost().findChild("");

		assertThat(context).isNotNull();
		assertThat(context.getPrivileged()).isFalse();
	}

	@Test
	void disallowsCrossContextRequests() {
		assertThat(this.applicationContext.getWebServer()).isInstanceOf(TomcatWebServer.class);
		TomcatWebServer tomcatWebServer = (TomcatWebServer) this.applicationContext.getWebServer();
		Context context = (Context) tomcatWebServer.getTomcat().getHost().findChild("");

		assertThat(context).isNotNull();
		assertThat(context.getCrossContext()).isFalse();
	}

	/**
	 * Verifies that malformed HTTP input rejected before Spring MVC receives a generic
	 * Problem Details response instead of Tomcat's default implementation-revealing HTML
	 * error page.
	 */
	@Test
	void oversizedRequestHeaderDoesNotRevealTomcatHtmlErrorPage() {
		RestTestClientResponse response = RestTestClientResponse.from(this.restTestClient.get()
			.uri("/oauth2/jwks")
			.header("X-Oversized-Header", "a".repeat(64 * 1024))
			.exchange());

		assertThat(response).hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentTypeCompatibleWith("application/problem+json")
			.bodyJson()
			.isLenientlyEqualTo("""
					{
					  "title": "Bad Request",
					  "status": 400
					}
					""");
	}

}
