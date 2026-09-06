package com.example.app.web.server.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationContextInitializedEvent;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

/**
 * Emits structured lifecycle events once Spring Boot has initialized logging.
 *
 * <p>
 * This listener is registered through {@code META-INF/spring.factories}, rather than as a
 * bean, so it can observe startup failures that prevent the application context from
 * being refreshed.
 */
public class ApplicationLifecycleEventLogger implements ApplicationListener<ApplicationEvent> {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationLifecycleEventLogger.class);

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		// Logging is not available during ApplicationStartingEvent.
		if (event instanceof ApplicationContextInitializedEvent) {
			logApplicationStarting();
		}
		else if (event instanceof ApplicationStartedEvent) {
			logApplicationStarted();
		}
		else if (event instanceof ApplicationFailedEvent applicationFailedEvent) {
			logApplicationFailed(applicationFailedEvent);
		}
		else if (event instanceof ContextClosedEvent contextClosedEvent
				&& contextClosedEvent.getApplicationContext().getParent() == null) {
			logApplicationStopped();
		}
	}

	private void logApplicationStarting() {
		LOGGER.atInfo()
			.addKeyValue("event.category", "process")
			.addKeyValue("event.type", List.of("start"))
			.addKeyValue("event.action", "start_application")
			.addKeyValue("event.outcome", "unknown")
			.log("Application starting");
	}

	private void logApplicationStarted() {
		LOGGER.atInfo()
			.addKeyValue("event.category", "process")
			.addKeyValue("event.type", List.of("start"))
			.addKeyValue("event.action", "start_application")
			.addKeyValue("event.outcome", "success")
			.log("Application started");
	}

	private void logApplicationFailed(ApplicationFailedEvent event) {
		LOGGER.atError()
			.addKeyValue("event.category", "process")
			.addKeyValue("event.type", List.of("start"))
			.addKeyValue("event.action", "start_application")
			.addKeyValue("event.outcome", "failure")
			.setCause(event.getException())
			.log("Application failed to start");
	}

	private void logApplicationStopped() {
		LOGGER.atInfo()
			.addKeyValue("event.category", "process")
			.addKeyValue("event.type", List.of("end"))
			.addKeyValue("event.action", "stop_application")
			.addKeyValue("event.outcome", "success")
			.log("Application stopped");
	}

}
