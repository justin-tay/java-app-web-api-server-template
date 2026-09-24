package com.example.commons.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonWriter;

class TraceCorrelationJsonMembersCustomizerTest {

	private final JsonWriter<ILoggingEvent> writer = JsonWriter
		.of(new TraceCorrelationJsonMembersCustomizer()::customize);

	@Test
	void rendersTraceAndSpanIdsAsNestedEcsFields() {
		LoggingEvent event = new LoggingEvent();
		event.setMDCPropertyMap(Map.of("traceId", "01679bd2a399cef9c825a842a594c7bc", "spanId", "0e8571a615cc224a"));

		assertThat(this.writer.writeToString(event)).isEqualTo("""
				{"trace":{"id":"01679bd2a399cef9c825a842a594c7bc"},"span":{"id":"0e8571a615cc224a"}}""");
	}

	@Test
	void omitsTraceAndSpanWhenNeitherIsInMdc() {
		LoggingEvent event = new LoggingEvent();
		event.setMDCPropertyMap(Map.of());

		assertThat(this.writer.writeToString(event)).isEqualTo("{}");
	}

}
