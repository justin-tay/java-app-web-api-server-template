package com.example.commons.crypto.cms;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Reads PEM written by {@link ArmorOutputStream}, or any PEM body with a BEGIN line and
 * an END line, and returns the decoded bytes.
 */
final class ArmorInputStream {

	private static final byte[] BEGIN = "-----BEGIN".getBytes(StandardCharsets.US_ASCII);

	private ArmorInputStream() {
	}

	/**
	 * Returns a stream of the DER bytes: the input decoded if it is PEM, else the input.
	 * @param in the message
	 * @return a stream positioned at the first byte of the message
	 * @throws IOException if the input cannot be read
	 */
	static InputStream dearmorIfNeeded(InputStream in) throws IOException {
		BufferedInputStream buffered = new BufferedInputStream(in, 8192);
		buffered.mark(BEGIN.length);
		byte[] start = buffered.readNBytes(BEGIN.length);
		buffered.reset();
		if (!java.util.Arrays.equals(start, BEGIN)) {
			return buffered;
		}
		skipLine(buffered);
		return Base64.getMimeDecoder().wrap(new Body(buffered));
	}

	private static void skipLine(InputStream in) throws IOException {
		int next;
		do {
			next = in.read();
		}
		while (next != -1 && next != '\n');
	}

	/** The lines up to the END line. */
	private static final class Body extends InputStream {

		private final InputStream in;

		private byte[] line = new byte[0];

		private int position;

		private boolean finished;

		Body(InputStream in) {
			this.in = in;
		}

		@Override
		public int read() throws IOException {
			while (this.position >= this.line.length) {
				if (this.finished || !nextLine()) {
					return -1;
				}
			}
			return this.line[this.position++] & 0xff;
		}

		private boolean nextLine() throws IOException {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			int next = this.in.read();
			if (next == -1) {
				this.finished = true;
				return false;
			}
			while (next != -1 && next != '\n') {
				bytes.write(next);
				next = this.in.read();
			}
			String text = bytes.toString(StandardCharsets.US_ASCII);
			if (text.startsWith("-----END")) {
				this.finished = true;
				return false;
			}
			this.line = (text + "\n").getBytes(StandardCharsets.US_ASCII);
			this.position = 0;
			return true;
		}

	}

}
