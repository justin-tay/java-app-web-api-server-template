package com.example.commons.crypto.openpgp;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nothing buffers the message: 192 MiB is signed, encrypted, decrypted and verified in a
 * JVM whose heap is 96 MiB, which would fail with an OutOfMemoryError if the message were
 * held in memory.
 */
class PgpStreamingMemoryTest {

	@Test
	void aMessageLargerThanTheHeapIsStreamed() throws Exception {
		String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		Process process = new ProcessBuilder(java, "-Xmx96m", "-cp", System.getProperty("java.class.path"),
				PgpStreamingMemoryMain.class.getName())
			.redirectErrorStream(true)
			.start();
		String output = new String(process.getInputStream().readAllBytes());

		assertThat(process.waitFor(5, TimeUnit.MINUTES)).isTrue();
		assertThat(process.exitValue()).as(output).isZero();
	}

}
