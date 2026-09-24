package com.example.commons.web.problem;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Configures RFC 9457 Problem Details responses for every Spring MVC error path, in place
 * of Spring Boot's default error JSON. See docs/adr/0013.
 *
 * <p>
 * Applied whenever commons is on the classpath of a servlet application. Set
 * {@code commons.web.problem-details.enabled=false} to turn it off, or define an
 * {@link ErrorController} or {@link ResponseEntityExceptionHandler} bean to replace one
 * part of it.
 */
@AutoConfiguration(before = { WebMvcAutoConfiguration.class, ErrorMvcAutoConfiguration.class })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = "commons.web.problem-details.enabled", matchIfMissing = true)
public class ProblemDetailsAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(ErrorController.class)
	ProblemDetailErrorController problemDetailErrorController() {
		return new ProblemDetailErrorController();
	}

	@Bean
	@ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)
	ApiResponseEntityExceptionHandler apiResponseEntityExceptionHandler() {
		return new ApiResponseEntityExceptionHandler();
	}

}
