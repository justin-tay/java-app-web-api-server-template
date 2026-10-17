package com.example.commons.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreamsTest {

	@Test
	void closingAnUnclosableStreamLeavesTheWrappedOneOpen() throws IOException {
		boolean[] closed = new boolean[1];
		ByteArrayOutputStream bytes = new ByteArrayOutputStream() {
			@Override
			public void close() {
				closed[0] = true;
			}
		};

		OutputStream out = Streams.unclosable(bytes);
		out.write(new byte[] { 1, 2, 3 }, 0, 3);
		out.close();

		assertThat(closed[0]).isFalse();
		assertThat(bytes.toByteArray()).containsExactly(1, 2, 3);
	}

	@Test
	void outputPassesWritesThroughUpToTheLimit() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		OutputStream out = Streams.output(bytes, 5);

		out.write(1);
		out.write(new byte[] { 2, 3, 4, 5 }, 0, 4);

		assertThat(bytes.toByteArray()).containsExactly(1, 2, 3, 4, 5);
	}

	@Test
	void outputWritesNothingPastTheLimit() {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		OutputStream out = Streams.output(bytes, 5);

		assertThatThrownBy(() -> out.write(new byte[6], 0, 6)).isInstanceOf(CryptoException.class)
			.hasMessageContaining("limit of 5 bytes");
		assertThat(bytes.size()).isZero();
	}

	@Test
	void outputWithoutALimitCountsNothing() throws IOException {
		OutputStream out = Streams.output(new ByteArrayOutputStream(), Long.MAX_VALUE);

		out.write(new byte[100_000], 0, 100_000);
		out.write(new byte[100_000], 0, 100_000);
	}

	@Test
	void aFailureOfTheStreamIsReportedAsThatNotAsACorruptMessage() {
		IOException cause = new IOException("disk full");
		OutputStream broken = new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				throw cause;
			}
		};
		OutputStream out = Streams.output(broken, Long.MAX_VALUE);

		assertThatThrownBy(() -> out.write(1)).isExactlyInstanceOf(CryptoException.class)
			.hasMessage("Cannot write to the output stream")
			.hasCause(cause);
		assertThatThrownBy(() -> out.write(new byte[2], 0, 2)).isExactlyInstanceOf(CryptoException.class);
	}

	@Test
	void closingTheOutputWrapperDoesNotCloseTheCallersStream() throws IOException {
		boolean[] closed = new boolean[1];
		OutputStream bytes = new ByteArrayOutputStream() {
			@Override
			public void close() {
				closed[0] = true;
			}
		};

		Streams.output(bytes, 10).close();

		assertThat(closed[0]).isFalse();
	}

}
