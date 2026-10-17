package com.example.commons.crypto.openpgp;

import com.example.commons.crypto.CryptoException;

/**
 * Limits that keep a crafted message from using unbounded time: how deeply compressed
 * data may be nested. The output size limit is {@code Streams.output}.
 */
final class PgpLimits {

	/** Real messages are compressed once; a few layers are allowed for unusual tools. */
	static final int MAX_COMPRESSION_NESTING = 4;

	private PgpLimits() {
	}

	static CryptoException nestedTooDeeply() {
		return new CryptoException("The message is nested in too many compression layers");
	}

}
