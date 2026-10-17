package com.example.commons.crypto;

/**
 * The outcome of checking one signature. The library checks the signature only; whether
 * the signer is trusted is a decision for the caller.
 */
public enum SignatureStatus {

	/** The signature matches the data and a key the caller supplied. */
	VALID,

	/** The signature does not match the data. */
	INVALID,

	/**
	 * The signature is by a key the caller did not supply, so it could not be checked.
	 */
	UNKNOWN_SIGNER

}
