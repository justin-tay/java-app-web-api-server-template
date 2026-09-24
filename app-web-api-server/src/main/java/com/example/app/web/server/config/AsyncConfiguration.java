package com.example.app.web.server.config;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configures asynchronous execution with request logging context propagation.
 */
@Configuration(proxyBeanMethods = false)
public class AsyncConfiguration {

	/**
	 * Configures Spring MVC's default async executor to propagate MDC values, including
	 * the request ID and authenticated user.
	 * @param builder configures the executor using Spring Boot task properties
	 * @return the application task executor
	 */
	@Bean(name = TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
	ThreadPoolTaskExecutor applicationTaskExecutor(ThreadPoolTaskExecutorBuilder builder) {
		return builder.taskDecorator(new MdcTaskDecorator()).build();
	}

	private static final class MdcTaskDecorator implements TaskDecorator {

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

		private void setContext(Map<String, String> context) {
			if (context == null) {
				MDC.clear();
			}
			else {
				MDC.setContextMap(context);
			}
		}

	}

}
