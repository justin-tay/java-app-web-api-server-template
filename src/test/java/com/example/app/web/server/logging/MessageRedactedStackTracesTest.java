package com.example.app.web.server.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MessageRedactedStackTracesTest {

	@Test
	void rejectsANullThrowable() {
		assertThatThrownBy(() -> MessageRedactedStackTraces.stackTraceWithoutMessages(null))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	void omitsTheMessageButKeepsTheClassNameAndStackFrames() {
		IllegalStateException exception = new IllegalStateException("sensitive-value-should-not-appear");

		String rendered = MessageRedactedStackTraces.stackTraceWithoutMessages(exception);

		assertThat(rendered).doesNotContain("sensitive-value-should-not-appear");
		assertThat(rendered).startsWith(IllegalStateException.class.getName() + System.lineSeparator());
		assertThat(rendered).contains("at " + getClass().getName());
	}

	@Test
	void redactsMessagesThroughoutTheCauseChain() {
		RuntimeException rootCause = new RuntimeException("root-cause-secret");
		IllegalStateException wrapper = new IllegalStateException("wrapper-secret", rootCause);

		String rendered = MessageRedactedStackTraces.stackTraceWithoutMessages(wrapper);

		assertThat(rendered).doesNotContain("wrapper-secret");
		assertThat(rendered).doesNotContain("root-cause-secret");
		assertThat(rendered).contains("Caused by: " + RuntimeException.class.getName());
	}

	@Test
	void redactsMessagesOnSuppressedExceptions() {
		IllegalStateException exception = new IllegalStateException("primary-secret");
		exception.addSuppressed(new RuntimeException("suppressed-secret"));

		String rendered = MessageRedactedStackTraces.stackTraceWithoutMessages(exception);

		assertThat(rendered).doesNotContain("primary-secret");
		assertThat(rendered).doesNotContain("suppressed-secret");
		assertThat(rendered).contains("Suppressed: " + RuntimeException.class.getName());
	}

	@Test
	void handlesAnExceptionWithNoMessage() {
		IllegalStateException exception = new IllegalStateException();

		String rendered = MessageRedactedStackTraces.stackTraceWithoutMessages(exception);

		assertThat(rendered).startsWith(IllegalStateException.class.getName() + System.lineSeparator());
	}

	@Test
	void foldsRepeatedCommonFramesTheSameWayTheJdkDoes() {
		RuntimeException rootCause = new RuntimeException("root-cause-secret");
		StackTraceElement[] sharedFrames = new StackTraceElement[] {
				new StackTraceElement("com.example.Shared", "run", "Shared.java", 42),
				new StackTraceElement("com.example.Shared", "invoke", "Shared.java", 10) };
		rootCause.setStackTrace(sharedFrames);
		IllegalStateException wrapper = new IllegalStateException("wrapper-secret", rootCause);
		wrapper.setStackTrace(
				new StackTraceElement[] { new StackTraceElement("com.example.Wrapper", "process", "Wrapper.java", 5),
						sharedFrames[0], sharedFrames[1] });

		String rendered = MessageRedactedStackTraces.stackTraceWithoutMessages(wrapper);

		assertThat(rendered).contains("... 2 more");
	}

}
