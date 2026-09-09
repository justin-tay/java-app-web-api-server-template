package com.example.app.web.server.config;

import java.time.Clock;
import java.util.TimeZone;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application time zone and clock.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

	/**
	 * Gets the host's configured time zone.
	 * @return the configured time zone
	 */
	@Bean
	TimeZone timeZone() {
		return TimeZone.getDefault();
	}

	/**
	 * Gets the application clock.
	 * @param timeZone the configured time zone
	 * @return a clock in the configured time zone
	 */
	@Bean
	Clock clock(TimeZone timeZone) {
		return Clock.system(timeZone.toZoneId());
	}

}
