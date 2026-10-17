# Requirements: commons-crypto

## Overview

The `commons-crypto` modules are Java modules that sign, verify, encrypt and decrypt data in
two formats, OpenPGP and CMS (RFC 5652), with a small API built on `InputStream` and
`OutputStream`. Private keys may be software keys or keys held on a hardware token
through PKCS#11. They sit beside the other `commons-*` modules under `commons-parent`, grouped under
`commons-crypto-parent`.

It is a library, not an application. Applications decide which keys to trust and where
data goes; the library performs the cryptographic operations and reports what it found.

## Terminology

- **Key handle:** An object that stands for a private key and the JCA provider that
  must perform operations with it. For a token key the key material never leaves the
  token.
- **Token:** A PKCS#11 device such as a smart card, USB token or HSM.
- **Embedded signature:** A signature carried in the same message as the data (a PGP
  signed message, CMS `SignedData` with encapsulated content).
- **Detached signature:** A signature in its own stream, separate from the data.
- **Cleartext signature:** A PGP signature over human-readable text that stays
  readable. PGP only.
- **Armor:** The text encoding of a binary message: ASCII armor for PGP, PEM or base64
  for CMS.
- **Legacy compatibility:** An explicit option that allows weak algorithms and
  unauthenticated formats when reading old data.

## Requirements

### R1: Formats and packaging

**User story:** As a developer, I want to use OpenPGP or CMS without pulling in the
other format's dependencies, so that my application stays small.

1. The library SHALL be three Maven modules under `com.example`, each named after the
   package it owns: `commons-crypto` (shared key, token and result API, package
   `com.example.commons.crypto` plus the `pkcs11` subpackage), `commons-crypto-cms` (package
   `com.example.commons.crypto.cms`) and `commons-crypto-openpgp` (package
   `com.example.commons.crypto.openpgp`), grouped under the aggregator
   `commons-crypto-parent`.
2. `commons-crypto-cms` and `commons-crypto-openpgp` SHALL depend on `commons-crypto`
   and SHALL NOT depend on each other.
3. `commons-crypto` SHALL depend on `bcprov` only. `commons-crypto-cms` SHALL add
   `bcpkix` and `commons-crypto-openpgp` SHALL add `bcpg`, so a consumer's dependencies
   follow the format it uses.
4. Bouncy Castle SHALL be used at its latest release at the time of implementation (1.86
   when this specification was written), with all Bouncy Castle artifacts on one version.
5. The modules SHALL target Java 17, SHALL NOT depend on Spring, and SHALL declare the
   `Automatic-Module-Name` values `com.example.commons.crypto`,
   `com.example.commons.crypto.cms` and `com.example.commons.crypto.openpgp`.

### R2: Operations

**User story:** As a developer, I want the common cryptographic operations through one
simple request-and-stream API, so that I can use them without learning Bouncy Castle.

1. Each format SHALL provide sign, verify, encrypt, decrypt, sign-and-encrypt and
   decrypt-and-verify.
2. Each operation SHALL read from an `InputStream` and write to an `OutputStream` and
   SHALL NOT read the whole message into memory.
3. Each operation SHALL take an immutable request object built with a builder.
4. Each format SHALL also provide static one-line shortcuts for the common cases, which
   delegate to the request-based operations.
5. Sign-and-encrypt SHALL sign first and then encrypt.
6. WHEN more than one signer is given, THEN the operation SHALL produce one signature
   per signer.
7. Key generation, keyring management, timestamping and certificate chain or
   revocation validation SHALL NOT be part of version 1.

### R3: Signature modes

**User story:** As a developer, I want to choose between embedded and detached
signatures, so that I can fit the format my consumers expect.

1. A sign request SHALL carry a `SignatureMode` of `EMBEDDED` or `DETACHED`.
2. The OpenPGP request SHALL also be able to produce a cleartext signature, selected with
   `cleartext()` on its builder because `SignatureMode` has only `EMBEDDED` and `DETACHED`.
   The CMS request SHALL NOT offer it.
3. WHEN a detached signature is verified, THEN the caller SHALL pass the signature as a
   second stream and the data as the first.
4. WHEN armor is requested, THEN the output SHALL be ASCII armor for OpenPGP and PEM or
   base64 for CMS.

### R4: Recipients and keys

**User story:** As a developer, I want to name recipients in each format's own terms,
so that the library does not hide how the format works.

1. An OpenPGP encrypt request SHALL take public keys as recipients and SHALL encrypt to
   every encryption-capable subkey of each that is neither revoked nor expired, in a ring whose
   primary key is neither revoked nor expired.
2. A CMS encrypt request SHALL take X.509 certificates as recipients.
3. An OpenPGP decrypt request SHALL choose the secret key by the key ID in the message.
   A CMS decrypt request SHALL choose it by the recipient identifier in the message.
   WHEN the message hides its recipient (a wildcard key ID), THEN the OpenPGP decrypt request
   SHALL try every supplied key that can decrypt.
4. WHEN no supplied key matches, THEN decrypt SHALL throw `NoMatchingKeyException`.
   An OpenPGP encrypt request SHALL also accept a passphrase, alone or with recipients, and an
   OpenPGP decrypt request SHALL accept a passphrase for such messages. A wrong passphrase SHALL
   throw `PinRequiredException`.
   A CMS encrypt request SHALL likewise accept a password (a password recipient, PBKDF2-HMAC-SHA256
   and AES-256 CBC key wrap), a CMS decrypt request SHALL accept one, and a CMS decrypt request
   SHALL refuse a message whose password key derivation asks for more than 5 million iterations.
   WHEN a message has several recipient entries for the supplied key, THEN the CMS decrypt request
   SHALL try each until one unwraps.
5. WHEN a private key is on a token for OpenPGP, THEN the caller SHALL supply the
   public keys and the library SHALL find the one that matches the token key by having the
   token sign a test value and verifying it with each candidate, at construction, and fail
   if none matches. The token key must therefore be allowed to sign.

### R5: Hardware keys through PKCS#11

**User story:** As an operator, I want private keys to stay on a hardware token, so that
they cannot be copied.

1. The library SHALL load keys from a PKCS#11 token, a PKCS#12 file or a Java key store
   into a key handle, and the operations SHALL accept any key handle.
2. A token key handle SHALL perform private-key operations through the JCA provider it
   carries, and the key material SHALL NOT be extracted.
3. The token PIN SHALL be obtained through a `PinSupplier` callback that returns
   `char[]`. The library SHALL NOT keep the PIN longer than the login needs.
4. WHEN a PIN is wrong, THEN the library SHALL throw `PinRequiredException` and SHALL
   NOT retry automatically.
5. A key handle SHALL be `AutoCloseable` and safe to use from several threads.
6. WHEN the token becomes unavailable, THEN operations SHALL throw
   `TokenUnavailableException` and the caller SHALL create a new handle. The library
   SHALL NOT reconnect by itself.
7. Version 1 SHALL support, on tokens, RSA keys and EC keys on P-256, P-384 and P-521 for
   signing and decryption. Ed25519 and X25519 are not available through SunPKCS11 and are out of scope
   for token keys. Software keys SHALL support ECC from the first release.
8. WHEN an EC token key decrypts (CMS key agreement or OpenPGP ECDH), THEN the library
   SHALL run the raw key agreement on the token and the key derivation in software, through
   an adapter provider that the library hands to Bouncy Castle, and the private key SHALL NOT leave the
   token.
9. The library SHALL build the token provider configuration itself, including the derived
   secret key attributes the agreement needs, and SHALL NOT require the caller to supply a
   provider configuration file.
10. WHEN a token holds a private key without a certificate, THEN the documentation SHALL
    describe how to write a placeholder certificate so the key is listed, because SunPKCS11
    lists only keys that have a certificate.
11. WHEN an RSA token key is used through a provider that has no OAEP cipher (SunPKCS11),
    THEN decrypt of a CMS OAEP recipient SHALL fail with a `CryptoException` that names the
    cause and the options. A CMS PKCS#1 v1.5 recipient SHALL be accepted only when the
    caller calls `allowPkcs1v15KeyTransport()` on that key handle, which is an explicit
    exception to R8.2 for that key and not a global setting.

### R5b: Providers

**User story:** As an integrator, I want to know which JCA providers the library uses, so that it
does not disturb mine and I can put a key behind my own.

1. The library SHALL use its own Bouncy Castle provider instance, created lazily and shared, and
   SHALL NOT add it to the JDK's provider list.
2. A key handle SHALL be able to name the provider that performs the operations with its key. WHEN
   a handle is not one of the library's own token handles, THEN its provider SHALL be used for
   signing, RSA decryption and EC key agreement.
3. The library SHALL NOT register any provider with the JVM. The ECDH adapter SHALL be one instance
   per open token, handed to Bouncy Castle by the token's key handle, and it SHALL do the plain
   ECDH only through that token's provider. Its two key factory names (`ECDH` and
   `1.2.840.10045.2.1`), which Bouncy Castle looks up and the JDK lacks, SHALL hand every key to
   the JDK's EC key factory. It SHALL also offer the AES key wrap and the EC algorithm parameters
   that Bouncy Castle asks the same provider for.
4. WHEN a token goes away while a key on it is used, THEN the operation SHALL throw a
   `TokenUnavailableException`, whichever operation it was.

### R6: Verification results

**User story:** As a developer, I want clear verification results, so that I cannot
accidentally ignore a bad signature.

1. Verify and decrypt-and-verify SHALL return a result listing each signature with the
   signer, whether it is valid, and the signing time.
2. A signature from a key the caller did not supply SHALL appear as "unknown signer",
   not be dropped.
3. The library SHALL check the signature only and SHALL NOT decide whether the signer is
   trusted. The caller decides trust from the result.
4. WHEN the caller turns on the key validity check on an OpenPGP verify request, THEN a matching
   signature by a key that is revoked in the supplied key blocks (the key or its primary key), or
   that had expired at the time of the signature, SHALL be reported as `KEY_REVOKED` or
   `KEY_EXPIRED`.
5. WHEN the caller turns on the certificate validity check on a CMS verify request, THEN a matching
   signature whose certificate was not valid at the signing time SHALL be reported as
   `KEY_EXPIRED`. Without it the certificate dates SHALL NOT affect the result.
6. WHEN the caller supplies a trust check on a CMS verify request, THEN a matching signature whose
   certificate the check rejects SHALL be reported as `NOT_TRUSTED`. The library SHALL NOT fetch
   revocation data.

### R7: Streaming and integrity

**User story:** As a developer, I want a predictable rule for what I have received when
a message turns out to be corrupt, so that I handle failures correctly.

1. WHEN an integrity or signature check fails, THEN the operation SHALL throw
   `IntegrityException` or a signature exception after the stream ends.
2. The documentation SHALL state that output written before the exception SHALL be
   discarded by the caller.
   The `Cms` and `Pgp` shortcuts SHALL also offer `decryptToBytes` with a size limit, which
   returns the plain data only after the whole message passed its integrity check.
3. WHEN a message is not integrity-protected (an OpenPGP packet without modification
   detection, CMS `EnvelopedData` with an unauthenticated cipher), THEN decrypt SHALL
   refuse it unless legacy compatibility is set.

### R7b: Resource limits

**User story:** As an operator, I want a crafted message not to exhaust memory or time, so that
the library can read input I do not control.

1. WHEN OpenPGP compressed data is nested more than 4 layers deep, THEN the library SHALL refuse
   it with a `CryptoException`.
2. WHEN the caller sets `maxOutputBytes` on a decrypt or verify request, in either format, and
   more output would be written, THEN the library SHALL stop before writing past the limit and
   throw a `CryptoException`.
3. WHEN a line of an armored CMS message is longer than 1024 bytes, THEN the library SHALL refuse
   it with a `CryptoException`.
4. A `PgpSecretKeys` SHALL be closeable, closing it SHALL overwrite the passphrase copy it holds,
   and closed keys SHALL NOT be usable.

### R8: Algorithm policy

**User story:** As a security owner, I want safe algorithms by default, so that
adopters do not weaken their security by accident.

1. The library SHALL encrypt content with AES-256 in an authenticated mode by default
   (GCM for CMS, OCB or MDC-protected AES for OpenPGP).
2. The library SHALL refuse 3DES, CAST5, SHA-1 signatures and PKCS#1 v1.5 key transport
   for CMS by default, on both read and write.
3. WHEN legacy compatibility is set, THEN decrypt and verify SHALL accept those
   algorithms, and encrypt and sign SHALL still refuse them.

### R9: Errors

1. Every library exception SHALL extend the unchecked `CryptoException`.
2. The library SHALL provide the subtypes `NoMatchingKeyException`,
   `IntegrityException`, `TokenUnavailableException` and `PinRequiredException`.
3. An exception message SHALL NOT contain a PIN, key material or message content.

### R10: Testing

1. Unit tests SHALL run with `mvn test` using software keys only and no external tools.
2. An integration-test profile SHALL run PKCS#11 tests against SoftHSM2 in a container,
   and interoperability tests against GnuPG and OpenSSL.
3. Every format SHALL be tested for round trips in both directions with the external
   tools, for every `SignatureMode`.

## Constraints and edge cases

- SunPKCS11 has no RSA OAEP cipher on JDK 17, 21 or 25, so CMS OAEP decrypt needs a vendor
  provider that supports it.
- OpenPGP RSA uses PKCS#1 v1.5 key transport, so a PGP token key needs the mechanism
  `CKM_RSA_PKCS` enabled. A token that allows only OAEP cannot decrypt OpenPGP messages.
- CMS verification returns the signer's certificate and does not build or validate the
  chain; the caller does that.
- Detached signatures need the data twice (once to sign or verify, once to deliver), so
  the caller supplies a repeatable source or the data stream once per operation.
