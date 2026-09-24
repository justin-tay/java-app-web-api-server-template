package com.example.commons.web.problem;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

class ProblemDetailsAutoConfigurationTest {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ProblemDetailsAutoConfiguration.class));

	@Test
	void registersProblemDetailsHandlersByDefault() {
		this.contextRunner.run(context -> assertThat(context).hasSingleBean(ProblemDetailErrorController.class)
			.hasSingleBean(ApiResponseEntityExceptionHandler.class));
	}

	@Test
	void backsOffWhenDisabled() {
		this.contextRunner.withPropertyValues("commons.web.problem-details.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(ProblemDetailsAutoConfiguration.class)
				.doesNotHaveBean(ErrorController.class)
				.doesNotHaveBean(ResponseEntityExceptionHandler.class));
	}

	@Test
	void keepsApplicationHandlers() {
		this.contextRunner.withUserConfiguration(CustomHandlersConfiguration.class)
			.run(context -> assertThat(context).hasSingleBean(ErrorController.class)
				.hasSingleBean(ResponseEntityExceptionHandler.class)
				.doesNotHaveBean(ProblemDetailErrorController.class)
				.doesNotHaveBean(ApiResponseEntityExceptionHandler.class));
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomHandlersConfiguration {

		@Bean
		ErrorController customErrorController() {
			return new ErrorController() {
			};
		}

		@Bean
		ResponseEntityExceptionHandler customExceptionHandler() {
			return new ResponseEntityExceptionHandler() {
			};
		}

	}

}
