package com.example.commons.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTest {

	private final MdcTaskDecorator decorator = new MdcTaskDecorator();

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void runsTheTaskWithTheSubmittingThreadsMdc() {
		MDC.put("http.request.id", "request-1");
		AtomicReference<Map<String, String>> captured = new AtomicReference<>();
		Runnable decorated = this.decorator.decorate(captureMdc(captured));
		MDC.clear();
		MDC.put("http.request.id", "executing-thread");

		decorated.run();

		assertThat(captured.get()).containsExactly(Map.entry("http.request.id", "request-1"));
		assertThat(MDC.getCopyOfContextMap()).containsExactly(Map.entry("http.request.id", "executing-thread"));
	}

	@Test
	void clearsTheMdcWhenNeitherThreadHasOne() {
		AtomicReference<Map<String, String>> captured = new AtomicReference<>(Map.of("stale", "value"));
		Runnable decorated = this.decorator.decorate(captureMdc(captured));

		decorated.run();

		assertThat(captured.get()).isNullOrEmpty();
		assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
	}

	private static Runnable captureMdc(AtomicReference<Map<String, String>> captured) {
		return () -> captured.set(MDC.getCopyOfContextMap());
	}

}
