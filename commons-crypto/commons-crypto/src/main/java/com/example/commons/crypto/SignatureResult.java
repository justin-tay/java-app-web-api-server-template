package com.example.commons.crypto;

import java.time.Instant;

/**
 * One signature found in a message.
 *
 * @param signerId identifies the signer in the format's own terms: a key ID for OpenPGP,
 * an issuer and serial number or subject key identifier for CMS
 * @param status the outcome of checking the signature
 * @param signingTime when the signer says it signed, or {@code null} if the signature
 * carries no time
 */
public record SignatureResult(String signerId, SignatureStatus status, Instant signingTime) {
}
