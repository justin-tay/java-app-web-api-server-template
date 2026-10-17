package com.example.commons.crypto.openpgp;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.example.commons.crypto.CryptoException;

/**
 * Limits that keep a crafted message from using unbounded memory or time: how deeply
 * compressed data may be nested, and, if the caller sets one, how much output may be
 * written.
 */
final class PgpLimits {

	/** Real messages are compressed once; a few layers are allowed for unusual tools. */
	static final int MAX_COMPRESSION_NESTING = 4;

	private PgpLimits() {
	}

	static CryptoException nestedTooDeeply() {
		return new CryptoException("The message is nested in too many compression layers");
	}

	/**
	 * Wraps the output so that writing more than {@code maxBytes} fails. Nothing past the
	 * limit is written.
	 */
	static OutputStream limit(OutputStream out, long maxBytes) {
		return (maxBytes == Long.MAX_VALUE) ? out : new LimitedOutputStream(out, maxBytes);
	}

	private static final class LimitedOutputStream extends FilterOutputStream {

		private final long maxBytes;

		private long written;

		LimitedOutputStream(OutputStream out, long maxBytes) {
			super(out);
			this.maxBytes = maxBytes;
		}

		@Override
		public void write(int b) throws IOException {
			check(1);
			this.out.write(b);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			check(len);
			this.out.write(b, off, len);
		}

		private void check(int length) {
			if (length > this.maxBytes - this.written) {
				throw new CryptoException("The output is larger than the limit of " + this.maxBytes + " bytes");
			}
			this.written += length;
		}

	}

}
