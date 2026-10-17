package com.example.commons.crypto.cms;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Writes PEM: the bytes written are base64 encoded between BEGIN CMS and END CMS lines.
 * Closing it writes the end line and does not close the stream it wraps.
 */
final class ArmorOutputStream extends OutputStream {

	private static final byte[] LINE_SEPARATOR = { '\n' };

	private final OutputStream target;

	private final OutputStream encoder;

	ArmorOutputStream(OutputStream target) throws IOException {
		this.target = target;
		target.write("-----BEGIN CMS-----\n".getBytes(StandardCharsets.US_ASCII));
		this.encoder = Base64.getMimeEncoder(64, LINE_SEPARATOR).wrap(new FilterOutputStream(target) {
			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				this.out.write(b, off, len);
			}

			@Override
			public void close() throws IOException {
				flush();
			}
		});
	}

	@Override
	public void write(int b) throws IOException {
		this.encoder.write(b);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		this.encoder.write(b, off, len);
	}

	@Override
	public void flush() throws IOException {
		this.encoder.flush();
	}

	@Override
	public void close() throws IOException {
		this.encoder.close();
		this.target.write("\n-----END CMS-----\n".getBytes(StandardCharsets.US_ASCII));
		this.target.flush();
	}

}
