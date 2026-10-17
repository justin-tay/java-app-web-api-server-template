package com.example.commons.crypto;

/**
 * Where a signature is carried relative to the signed data.
 */
public enum SignatureMode {

	/** The signature is in the same message as the data. */
	EMBEDDED,

	/** The signature is in its own stream, separate from the data. */
	DETACHED

}
