package com.example.commons.crypto.cms;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Passes writes through and leaves the wrapped stream open when closed, so a Bouncy
 * Castle generator can be closed to finish its message without closing the caller's
 * stream.
 */
final class UnclosableOutputStream extends FilterOutputStream {

	UnclosableOutputStream(OutputStream out) {
		super(out);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		this.out.write(b, off, len);
	}

	@Override
	public void close() throws IOException {
		flush();
	}

}
