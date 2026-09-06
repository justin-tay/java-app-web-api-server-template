package com.example.app.web.server.config;

import java.io.IOException;
import java.io.PrintWriter;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Configures Tomcat security behavior for malformed HTTP requests.
 *
 * <p>
 * Tomcat rejects some invalid requests before they reach Spring MVC. Its default HTML
 * error report reveals that Tomcat generated the response, so this configuration replaces
 * it with a generic RFC 9457 Problem Details response.
 */
@Configuration
public class TomcatConfiguration {

	@Bean
	WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatProblemDetailErrorReportValve() {
		return factory -> factory.addContextCustomizers(context -> ((StandardHost) context.getParent())
			.setErrorReportValveClass(TomcatProblemDetailErrorReportValve.class.getName()));
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
