package com.example.commons.logging;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Copies the submitting thread's MDC, including the request ID and authenticated user, to
 * the thread that runs a task, and restores the executing thread's own MDC afterwards, so
 * log events from {@code @Async} methods and Spring MVC asynchronous requests keep the
 * request's correlation fields.
 */
public class MdcTaskDecorator implements TaskDecorator {

	@Override
	public Runnable decorate(Runnable runnable) {
		Map<String, String> submittingThreadContext = MDC.getCopyOfContextMap();
		return () -> {
			Map<String, String> executingThreadContext = MDC.getCopyOfContextMap();
			try {
				setContext(submittingThreadContext);
				runnable.run();
			}
			finally {
				setContext(executingThreadContext);
			}
		};
	}

	private static void setContext(Map<String, String> context) {
		if (context == null) {
			MDC.clear();
		}
		else {
			MDC.setContextMap(context);
		}
	}

}
