package com.example.commons.crypto;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Stream helpers that the format modules share. They are public only because the modules
 * are in different packages; they are not meant for applications and may change.
 */
public final class Streams {

	private Streams() {
	}

	/**
	 * Passes writes through and leaves the wrapped stream open when closed, so a Bouncy
	 * Castle generator can be closed to finish its message without closing the caller's
	 * stream.
	 * @param out the stream to wrap
	 * @return the wrapper
	 */
	public static OutputStream unclosable(OutputStream out) {
		return new FilterOutputStream(out) {
			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				this.out.write(b, off, len);
			}

			@Override
			public void close() throws IOException {
				flush();
			}
		};
	}

	/**
	 * Wraps the caller's output stream for a decrypt or verify operation. Writing more
	 * than {@code maxBytes} fails before anything past the limit is written, and a
	 * failure of the stream itself is reported as such instead of looking like a corrupt
	 * message. Closing it only flushes.
	 * @param out the caller's stream
	 * @param maxBytes the most bytes to write, or {@code Long.MAX_VALUE} for no limit
	 * @return the wrapper
	 * @throws CryptoException from a write, if the limit is passed or the stream fails
	 */
	public static OutputStream output(OutputStream out, long maxBytes) {
		return new OutputStream() {

			private long written;

			@Override
			public void write(int b) {
				check(1);
				try {
					out.write(b);
				}
				catch (IOException ex) {
					throw sinkFailure(ex);
				}
			}

			@Override
			public void write(byte[] b, int off, int len) {
				check(len);
				try {
					out.write(b, off, len);
				}
				catch (IOException ex) {
					throw sinkFailure(ex);
				}
			}

			@Override
			public void flush() {
				try {
					out.flush();
				}
				catch (IOException ex) {
					throw sinkFailure(ex);
				}
			}

			private void check(int length) {
				if (length > maxBytes - this.written) {
					throw new CryptoException("The output is larger than the limit of " + maxBytes + " bytes");
				}
				this.written += length;
			}

		};
	}

	private static CryptoException sinkFailure(IOException cause) {
		return new CryptoException("Cannot write to the output stream", cause);
	}

}
