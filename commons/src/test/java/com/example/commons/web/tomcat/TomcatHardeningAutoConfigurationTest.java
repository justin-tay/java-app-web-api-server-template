package com.example.commons.web.tomcat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import com.example.commons.web.tomcat.TomcatHardeningAutoConfiguration.JreMemoryLeakPreventionTomcatServletWebServerFactory;
import com.example.commons.web.tomcat.TomcatHardeningAutoConfiguration.TomcatHardeningRuntimeHints;
import com.example.commons.web.tomcat.TomcatHardeningAutoConfiguration.TomcatProblemDetailErrorReportValve;

class TomcatHardeningAutoConfigurationTest {

	private static final String STRICT_SERVLET_COMPLIANCE = "org.apache.catalina.STRICT_SERVLET_COMPLIANCE";

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(TomcatHardeningAutoConfiguration.class));

	@AfterEach
	void clearStrictServletCompliance() {
		System.clearProperty(STRICT_SERVLET_COMPLIANCE);
	}

	@Test
	void appliesHardeningByDefault() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(TomcatServletWebServerFactory.class);
			assertThat(context.getBean(TomcatServletWebServerFactory.class))
				.isInstanceOf(JreMemoryLeakPreventionTomcatServletWebServerFactory.class);
			assertThat(context).hasBean("tomcatSecurityHardening");
		});
	}

	@Test
	void backsOffWhenDisabled() {
		this.contextRunner.withPropertyValues("commons.web.tomcat.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(TomcatHardeningAutoConfiguration.class)
				.doesNotHaveBean(TomcatServletWebServerFactory.class)
				.doesNotHaveBean("tomcatSecurityHardening"));
	}

	@Test
	void keepsAnApplicationWebServerFactoryAndStillHardensIt() {
		this.contextRunner.withUserConfiguration(CustomWebServerFactoryConfiguration.class).run(context -> {
			assertThat(context).hasSingleBean(TomcatServletWebServerFactory.class);
			assertThat(context.getBean(TomcatServletWebServerFactory.class))
				.isNotInstanceOf(JreMemoryLeakPreventionTomcatServletWebServerFactory.class);
			assertThat(context).getBean("tomcatSecurityHardening").isInstanceOf(WebServerFactoryCustomizer.class);
		});
	}

	@Test
	void initializerEnablesStrictServletComplianceByDefault() {
		new TomcatApplicationContextInitializer().initialize(contextWith(new MockEnvironment()));

		assertThat(System.getProperty(STRICT_SERVLET_COMPLIANCE)).isEqualTo("true");
	}

	@Test
	void initializerDoesNothingWhenDisabled() {
		new TomcatApplicationContextInitializer().initialize(contextWith(
				new MockEnvironment().withProperty(TomcatHardeningAutoConfiguration.ENABLED_PROPERTY, "false")));

		assertThat(System.getProperty(STRICT_SERVLET_COMPLIANCE)).isNull();
	}

	@Test
	void registersTheErrorReportValveForReflection() {
		RuntimeHints hints = new RuntimeHints();
		new TomcatHardeningRuntimeHints().registerHints(hints, getClass().getClassLoader());

		assertThat(RuntimeHintsPredicates.reflection().onType(TomcatProblemDetailErrorReportValve.class))
			.accepts(hints);
	}

	private static GenericApplicationContext contextWith(MockEnvironment environment) {
		GenericApplicationContext context = new GenericApplicationContext();
		context.setEnvironment(environment);
		return context;
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomWebServerFactoryConfiguration {

		@Bean
		TomcatServletWebServerFactory customWebServerFactory() {
			return new TomcatServletWebServerFactory();
		}

	}

}
