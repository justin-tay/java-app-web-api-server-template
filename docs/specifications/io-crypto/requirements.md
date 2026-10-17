# Requirements: io-crypto

## Overview

`io-crypto` is a Java library that signs, verifies, encrypts and decrypts data in two
formats, OpenPGP and CMS (RFC 5652), with a small API built on `InputStream` and
`OutputStream`. Private keys may be software keys or keys held on a hardware token
through PKCS#11. The library is the first of a family of IO-flavoured libraries
published under the `com.example.io` namespace.

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

1. The library SHALL be one artifact, `com.example:io-crypto`, with the root package
   `com.example.io.crypto`, the subpackages `openpgp`, `cms` and `pkcs11`, and a shared
   API in the root package.
2. The `openpgp` and `cms` packages SHALL depend on the root package and SHALL NOT
   depend on each other.
3. `bcprov` SHALL be a required dependency. `bcpg` and `bcpkix` SHALL be optional
   dependencies, so a consumer adds only the one it uses.
4. WHEN `bcpg` is absent from the classpath, THEN every CMS operation SHALL work. WHEN
   `bcpkix` is absent, THEN every OpenPGP operation SHALL work. The build SHALL test
   both cases.
5. The library SHALL target Java 17, SHALL NOT depend on Spring, and SHALL declare the
   `Automatic-Module-Name` `com.example.io.crypto`.

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
2. The OpenPGP request SHALL also accept `CLEARTEXT`. The CMS request SHALL NOT accept
   it.
3. WHEN a detached signature is verified, THEN the caller SHALL pass the signature as a
   second stream and the data as the first.
4. WHEN armor is requested, THEN the output SHALL be ASCII armor for OpenPGP and PEM or
   base64 for CMS.

### R4: Recipients and keys

**User story:** As a developer, I want to name recipients in each format's own terms,
so that the library does not hide how the format works.

1. An OpenPGP encrypt request SHALL take public keys as recipients and SHALL encrypt to
   every encryption-capable subkey of each.
2. A CMS encrypt request SHALL take X.509 certificates as recipients.
3. An OpenPGP decrypt request SHALL choose the secret key by the key ID in the message.
   A CMS decrypt request SHALL choose it by the recipient identifier in the message.
4. WHEN no supplied key matches, THEN decrypt SHALL throw `NoMatchingKeyException`.
5. WHEN a private key is on a token for OpenPGP, THEN the caller SHALL supply the
   public key and the library SHALL check at construction that it matches the token key,
   and fail if not.

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
7. Version 1 SHALL support RSA keys on tokens. ECC keys on tokens (ECDH and EdDSA) are
   deferred until a spike confirms they work. Software keys SHALL support ECC from the
   first release.

### R6: Verification results

**User story:** As a developer, I want clear verification results, so that I cannot
accidentally ignore a bad signature.

1. Verify and decrypt-and-verify SHALL return a result listing each signature with the
   signer, whether it is valid, and the signing time.
2. A signature from a key the caller did not supply SHALL appear as "unknown signer",
   not be dropped.
3. The library SHALL check the signature only and SHALL NOT decide whether the signer is
   trusted. The caller decides trust from the result.

### R7: Streaming and integrity

**User story:** As a developer, I want a predictable rule for what I have received when
a message turns out to be corrupt, so that I handle failures correctly.

1. WHEN an integrity or signature check fails, THEN the operation SHALL throw
   `IntegrityException` or a signature exception after the stream ends.
2. The documentation SHALL state that output written before the exception SHALL be
   discarded by the caller.
3. WHEN a message is not integrity-protected (an OpenPGP packet without modification
   detection, CMS `EnvelopedData` with an unauthenticated cipher), THEN decrypt SHALL
   refuse it unless legacy compatibility is set.

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

- OpenPGP RSA uses PKCS#1 v1.5 key transport, so a PGP token key needs the mechanism
  `CKM_RSA_PKCS` enabled. A token that allows only OAEP cannot decrypt OpenPGP messages.
- CMS verification returns the signer's certificate and does not build or validate the
  chain; the caller does that.
- Detached signatures need the data twice (once to sign or verify, once to deliver), so
  the caller supplies a repeatable source or the data stream once per operation.
