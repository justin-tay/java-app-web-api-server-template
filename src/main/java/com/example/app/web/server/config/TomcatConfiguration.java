package com.example.app.web.server.config;

import java.io.IOException;
import java.io.PrintWriter;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.JreMemoryLeakPreventionListener;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Configures security hardening for the embedded Tomcat server.
 *
 * <p>
 * This applies the applicable connector, context, and server controls from the CIS Apache
 * Tomcat 11 Benchmark. It also replaces Tomcat's default HTML error report with a generic
 * RFC 9457 Problem Details response because malformed requests can be rejected before
 * they reach Spring MVC.
 */
@Configuration
public class TomcatConfiguration {

	@Bean
	TomcatServletWebServerFactory tomcatServletWebServerFactory() {
		// CIS 9.16: enable memory leak listener.
		return new JreMemoryLeakPreventionTomcatServletWebServerFactory();
	}

	@Bean
	WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatSecurityHardening() {
		return factory -> {
			factory.addConnectorCustomizers(connector -> {
				// CIS 2.6: reject TRACE requests before they reach the application.
				connector.setAllowTrace(false);
				// CIS 2.4: do not disclose container or JVM details in X-Powered-By.
				connector.setXpoweredBy(false);
				// CIS 2.4: leave Tomcat's Server value unset and remove any application
				// value.
				connector.setProperty("serverRemoveAppProvidedValues", "true");
				// CIS 9.7: do not recycle servlet request and response facade objects.
				connector.setProperty("discardFacades", "true");
				// CIS 9.8: do not accept backslashes as request-path delimiters.
				connector.setAllowBackslash(false);
				// CIS 9.8: reject encoded forward and reverse slash path delimiters.
				connector.setEncodedSolidusHandling("reject");
				connector.setEncodedReverseSolidusHandling("reject");
			});
			factory.addContextCustomizers(context -> {
				// CIS 9.12: prevent resources outside the web application via symbolic
				// links.
				context.getResources().setAllowLinking(false);
				// CIS 9.13: do not grant privileged container access to the application.
				context.setPrivileged(false);
				// CIS 9.14: prevent access to other web application contexts in this
				// JVM.
				context.setCrossContext(false);
				// CIS 2.5: avoid Tomcat's default client-facing HTML error report and
				// stack traces.
				((StandardHost) context.getParent())
					.setErrorReportValveClass(TomcatProblemDetailErrorReportValve.class.getName());
			});
		};
	}

	/**
	 * Adds Tomcat's JRE memory-leak-prevention listener to the Server before it is
	 * initialized. The listener must be attached to a Server, rather than a Context.
	 */
	private static final class JreMemoryLeakPreventionTomcatServletWebServerFactory
			extends TomcatServletWebServerFactory {

		@Override
		protected Tomcat createTomcat(TempDirs tempDirs) {
			Tomcat tomcat = super.createTomcat(tempDirs);
			tomcat.getServer().addLifecycleListener(new JreMemoryLeakPreventionListener());
			return tomcat;
		}

	}

	/**
	 * Writes generic RFC 9457 Problem Details responses for Tomcat-level errors without
	 * exposing the underlying server implementation.
	 */
	public static class TomcatProblemDetailErrorReportValve extends ErrorReportValve {

		@Override
		protected void report(Request request, Response response, Throwable throwable) {
			int statusCode = response.getStatus();
			if (statusCode < HttpStatus.BAD_REQUEST.value() || response.getContentWritten() > 0 || !response.isError()
					|| !response.setErrorReported()) {
				return;
			}

			HttpStatus status = HttpStatus.resolve(statusCode);
			String title = (status != null) ? status.getReasonPhrase() : "HTTP Error";
			String responseBody = "{\"title\":\"" + title + "\",\"status\":" + statusCode + "}";

			response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
			response.setCharacterEncoding("UTF-8");
			try {
				PrintWriter writer = response.getReporter();
				if (writer != null) {
					writer.write(responseBody);
					response.finishResponse();
				}
			}
			catch (IOException ex) {
				// The client disconnected before Tomcat could write the error response.
			}
		}

	}

}
