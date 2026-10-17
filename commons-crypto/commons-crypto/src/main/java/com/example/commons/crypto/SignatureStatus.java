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
	UNKNOWN_SIGNER,

	/**
	 * The signature matches, but the signer's key was revoked. Only reported when the
	 * request asks for the key validity check.
	 */
	KEY_REVOKED,

	/**
	 * The signature matches, but the signer's key or certificate was expired (or not yet
	 * valid) when it signed. Only reported when the request asks for the key validity
	 * check.
	 */
	KEY_EXPIRED,

	/**
	 * The signature matches, but the caller's own trust check rejected the signer's
	 * certificate. Only reported when the request has such a check.
	 */
	NOT_TRUSTED

}
