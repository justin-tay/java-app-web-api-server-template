package com.example.app.web.server.logging;

import java.util.Map;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.json.JsonWriter.Members;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Renames Micrometer Tracing's {@code traceId}/{@code spanId} MDC entries to ECS's
 * {@code trace.id}/{@code span.id} field names in the structured log output.
 * <p>
 * Spring Boot's ECS structured log formatter passes MDC entries through verbatim, so
 * without this customizer the fields would appear under Micrometer's own key names
 * instead of the ECS Tracing field names the rest of this application's log schema uses.
 * A member name containing a dot is not automatically nested, so each field is added as a
 * single-entry {@link Map} value instead, which the JSON writer renders as a nested
 * object. Registered via {@code logging.structured.json.customizer}.
 */
public class TraceCorrelationJsonMembersCustomizer implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

	@Override
	public void customize(Members<ILoggingEvent> members) {
		members.add("trace", TraceCorrelationJsonMembersCustomizer::traceId).whenNotNull();
		members.add("span", TraceCorrelationJsonMembersCustomizer::spanId).whenNotNull();
	}

	private static Map<String, String> traceId(ILoggingEvent event) {
		String traceId = event.getMDCPropertyMap().get("traceId");
		return (traceId != null) ? Map.of("id", traceId) : null;
	}

	private static Map<String, String> spanId(ILoggingEvent event) {
		String spanId = event.getMDCPropertyMap().get("spanId");
		return (spanId != null) ? Map.of("id", spanId) : null;
	}

}
