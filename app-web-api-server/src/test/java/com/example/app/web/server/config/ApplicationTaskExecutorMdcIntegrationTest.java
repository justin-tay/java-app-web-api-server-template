package com.example.app.web.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.core.task.AsyncTaskExecutor;

import com.example.app.web.server.test.RestTestClientITSupport;

/**
 * Tests that Spring Boot's auto-configured application task executor runs tasks with the
 * submitting request's MDC, through the commons {@code MdcTaskDecorator}.
 */
class ApplicationTaskExecutorMdcIntegrationTest extends RestTestClientITSupport {

	@Autowired
	@Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
	private AsyncTaskExecutor applicationTaskExecutor;

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void runsTasksWithTheSubmittingThreadsMdc() throws Exception {
		MDC.put("http.request.id", "request-1");

		Map<String, String> taskMdc = this.applicationTaskExecutor.submit(MDC::getCopyOfContextMap)
			.get(10, TimeUnit.SECONDS);

		assertThat(taskMdc).containsEntry("http.request.id", "request-1");
	}

}
