package com.example.commons.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import com.example.commons.logging.client.ClientIpResolver;
import com.example.commons.logging.request.RequestIdResolver;

class LoggingAutoConfigurationTest {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(LoggingAutoConfiguration.class));

	@Test
	void registersTheLoggingFiltersByDefault() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(LoggingContextCleanupFilter.class)
				.hasSingleBean(RequestCorrelationContextFilter.class)
				.hasSingleBean(AuthenticatedUserLoggingContextFilter.class)
				.hasBean("loggingContextCleanupFilterRegistration")
				.hasBean("requestCorrelationContextFilterRegistration")
				.hasBean("loggingFilterChainCustomizer");
			assertThat(
					context.getBean("loggingContextCleanupFilterRegistration", FilterRegistrationBean.class).getOrder())
				.isLessThan(context.getBean("requestCorrelationContextFilterRegistration", FilterRegistrationBean.class)
					.getOrder());
		});
	}

	@Test
	void resolvesNoClientIpOrRequestIdByDefault() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Forwarded-For", "203.0.113.10");
		request.addHeader("X-Amz-Cf-Id", "cloudfront-request-id");
		this.contextRunner.run(context -> {
			assertThat(context.getBean(ClientIpResolver.class).resolve(request)).isEmpty();
			assertThat(context.getBean(RequestIdResolver.class).resolve(request)).isEmpty();
		});
	}

	@Test
	void usesApplicationResolvers() {
		this.contextRunner.withUserConfiguration(CustomResolverConfiguration.class).run(context -> {
			assertThat(context).hasSingleBean(ClientIpResolver.class).hasSingleBean(RequestIdResolver.class);
			assertThat(context.getBean(ClientIpResolver.class)).isSameAs(CustomResolverConfiguration.CLIENT_IP);
			assertThat(context.getBean(RequestIdResolver.class)).isSameAs(CustomResolverConfiguration.REQUEST_ID);
		});
	}

	@Test
	void propagatesTheMdcToAsyncTasks() {
		this.contextRunner.run(context -> assertThat(context).hasSingleBean(MdcTaskDecorator.class));
	}

	@Test
	void keepsAnApplicationTaskDecorator() {
		this.contextRunner.withBean("customTaskDecorator", TaskDecorator.class, () -> runnable -> runnable)
			.run(context -> assertThat(context).hasSingleBean(TaskDecorator.class)
				.doesNotHaveBean(MdcTaskDecorator.class));
	}

	@Test
	void backsOffWhenDisabled() {
		this.contextRunner.withPropertyValues("commons.logging.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(LoggingAutoConfiguration.class)
				.doesNotHaveBean(LoggingContextCleanupFilter.class)
				.doesNotHaveBean(RequestCorrelationContextFilter.class)
				.doesNotHaveBean(AuthenticatedUserLoggingContextFilter.class)
				.doesNotHaveBean(Customizer.class));
	}

	@Test
	void skipsTheSecurityFilterChainCustomizerWithoutSpringSecurity() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(HttpSecurity.class))
			.run(context -> assertThat(context).hasSingleBean(RequestCorrelationContextFilter.class)
				.doesNotHaveBean(AuthenticatedUserLoggingContextFilter.class)
				.doesNotHaveBean("loggingFilterChainCustomizer"));
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomResolverConfiguration {

		static final ClientIpResolver CLIENT_IP = request -> Optional.empty();

		static final RequestIdResolver REQUEST_ID = request -> Optional.empty();

		@Bean
		ClientIpResolver customClientIpResolver() {
			return CLIENT_IP;
		}

		@Bean
		RequestIdResolver customRequestIdResolver() {
			return REQUEST_ID;
		}

	}

}
