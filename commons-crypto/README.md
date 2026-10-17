# commons-crypto

Signs, verifies, encrypts and decrypts data as OpenPGP or CMS (RFC 5652), through a small
`InputStream` and `OutputStream` API. Private keys can be in software or on a hardware token
through PKCS#11. It is built on Bouncy Castle and does not depend on Spring.

| Artifact | Package | Use it for |
| --- | --- | --- |
| `commons-crypto` | `com.example.commons.crypto` | Shared: key handles, `KeyStores`, PKCS#11 tokens, results, exceptions. Pulled in by the others. |
| `commons-crypto-openpgp` | `com.example.commons.crypto.openpgp` | OpenPGP (needs `bcpg`). |
| `commons-crypto-cms` | `com.example.commons.crypto.cms` | CMS (needs `bcpkix`). |

Add the one you need. The two format modules do not depend on each other.

## Add it to your build

It needs Java 17 or later. The format modules bring in `commons-crypto` and the Bouncy Castle
artifact they need (1.86), so one dependency is enough:

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>commons-crypto-openpgp</artifactId> <!-- or commons-crypto-cms -->
    <version>${commons-crypto.version}</version>   <!-- the version of this repository's build -->
</dependency>
```

Keep every Bouncy Castle artifact on the same version if you add your own.

The design, requirements and the results of the hardware and interoperability checks are in
[the specification](../docs/specifications/commons-crypto/design.md).

## Quick start

Every operation has a one-line form for the defaults, in `Pgp` and in `Cms`:

```java
PgpPublicKeys recipient = PgpPublicKeys.fromArmored(recipientAsc);
Pgp.encrypt(in, out, recipient);

try (PgpSecretKeys mine = PgpSecretKeys.fromArmored(secretAsc, passphrase)) {
    Pgp.decrypt(in, out, mine);
}

VerifyResult result = Pgp.verifyDetached(data, signatureIn, signerPublicKeys);
```

`PgpSecretKeys` is closeable: closing it overwrites the passphrase copy it holds.

```java
try (PrivateKeyHandle key = KeyStores.pkcs12(path, password, "alias")) {
    Cms.sign(in, out, key, certificate);
}
VerifyResult result = Cms.verify(signedIn, contentOut, certificate);
```

For anything else, build a request and use the operation classes (`PgpEncryptor`,
`PgpDecryptor`, `PgpSigner`, `PgpVerifier`, `CmsEncryptor`, `CmsDecryptor`, `CmsSigner`,
`CmsVerifier`): armored output, several recipients or signers, embedded or detached signatures,
cleartext signatures (OpenPGP only), and sign-and-encrypt or decrypt-and-verify in one call.

```java
PgpSigner.create().sign(
    SignRequest.builder().signer(mine).mode(SignatureMode.DETACHED).armored(true).build(),
    data, signatureOut);
```

Streams are never closed by the library.

## Caveats

Read these before relying on it.

- **Trust is yours.** Verification checks that a signature matches the data and a key you
  supplied. It does not build a certificate chain, check revocation, or look at the expiry or
  revocation of the signing key. A `VALID` result is not "trusted".
- **Streaming means unchecked output.** Decrypted or signed content is written before the final
  integrity check. If a call throws, discard what was written. Write to a temporary file or buffer if
  you need all-or-nothing.
- **Old formats are refused.** Messages without integrity protection, weak ciphers, SHA-1
  signatures and RSA PKCS#1 v1.5 key transport are rejected unless you set `legacyCompatibility(true)`
  on the decrypt or verify request. Anything the library writes is modern.
- **Hardware keys are narrower than software keys.** RSA and EC P-256 only. SunPKCS11 has no RSA
  OAEP, so CMS messages for an RSA token key need a provider that has it, or
  `allowPkcs1v15KeyTransport()` on the handle. A key is visible only if the token also holds a
  certificate for it. It was tested on SoftHSM2, not on a real HSM.
- **Not included.** Key generation, key ring management, timestamps, certificate chain validation,
  passphrase-only (symmetric) OpenPGP encryption, and messages addressed to no particular key
  (wildcard recipients).
- **Memory.** Cleartext OpenPGP signatures read the whole text into memory. Everything else streams.

## Keys on a token

```java
Pkcs11Config config = Pkcs11Config.builder().library(Path.of("/usr/lib/softhsm/libsofthsm2.so")).build();
try (Pkcs11Token token = Pkcs11Token.open(config, (info) -> pin())) {
    PrivateKeyHandle key = token.privateKey("alias");
    X509Certificate certificate = token.certificate("alias");
    Cms.sign(in, out, key, certificate);
}
```

- The PIN is asked for once and never retried, because a wrong PIN counts against the token's
  limit. A `char[]` is returned and cleared after the login.
- A key is listed only if the token also holds a certificate for it. See the specification for
  how to add a placeholder certificate to an OpenPGP key.
- An OpenPGP token key is paired with its public keys: `PgpSecretKeys.token(key, publicKeys)`. The
  token key must be allowed to sign, so that the pairing can be checked.
- RSA and EC P-256 keys are supported. SunPKCS11 has no RSA OAEP, so CMS messages for an RSA token
  key fail with an error that says so. If the sender can use PKCS#1 v1.5, call
  `allowPkcs1v15KeyTransport()` on the key handle.
- If the token is removed, operations fail with `TokenUnavailableException`; open a new token.
- Module-specific SunPKCS11 settings that the library does not set itself go in
  `Pkcs11Config.Builder.extraSettings(...)`, for example `nssArgs` for an NSS database.

## Defaults and policy

- Encryption uses AES-256 in an authenticated mode: CMS `AuthEnvelopedData` with GCM, and OpenPGP
  with a modification detection code. CMS RSA recipients get OAEP with SHA-256; EC recipients get
  ECDH.
- Messages that are not integrity protected, use weak ciphers, or use PKCS#1 v1.5 key transport, and
  signatures that use SHA-1, are refused. `legacyCompatibility(true)` on the decrypt or verify
  request accepts them for reading only.
- Verification reports every signature with a status: `VALID`, `INVALID` or `UNKNOWN_SIGNER`. It
  checks signatures only. It does not build certificate chains or check revocation, and it does not
  decide whether a signer is trusted: that is for the caller. A signature by a key that has since been
  revoked or has expired is still reported `VALID`; check that yourself if it matters.

## Providers

- The library uses its own Bouncy Castle provider instance, created the first time it is needed and
  shared (`BouncyCastle.provider()`). It is never added to the JDK's provider list, so your
  application does not need to register Bouncy Castle and its providers are left alone.
- A key handle says which provider performs the private key operations: `PrivateKeyHandle.provider()`.
  For a key in software it is empty; for a token it is the token's provider. You can implement
  `PrivateKeyHandle` yourself to put a key behind any JCA provider, such as a vendor's HSM provider,
  and that provider is used for signing, for RSA decryption and for EC key agreement. There is no
  option to choose the provider for ciphers, hashes or verification.
- The one JVM-wide change: the first time a token is opened through `Pkcs11Token`, the library
  registers a provider (`CommonsCryptoTokenEcdh`) at the front of the provider list. It offers the
  key agreement names Bouncy Castle asks for with a key derivation function, which SunPKCS11 lacks,
  and the EC key factory names Bouncy Castle looks up, handing them to the JDK's. It acts only for EC
  private keys that cannot be extracted, and only keys on a token you opened.

## Limits

- Compressed OpenPGP data may be nested at most 4 layers deep.
- `DecryptRequest` and `VerifyRequest` take `maxOutputBytes(long)`, which stops a message that
  expands a great deal (a decompression bomb) by failing once that much has been written. There is
  no limit unless you set one.
- A line of an armored CMS message may be at most 1024 bytes.
- Cleartext OpenPGP signatures read the whole text into memory.

## Streaming and failures

Everything streams. A signature or an integrity tag is at the end of the data, so decrypted or
signed content is written before it is known to be intact. If a call throws
`IntegrityException`, discard what was written to the output stream. To release data only after the
check, write to a temporary file or buffer and move it on success.

All exceptions are unchecked and extend `CryptoException`: `NoMatchingKeyException`,
`IntegrityException`, `PinRequiredException` and `TokenUnavailableException`. Messages never contain
a PIN, a key or message content.

## Tests

`mvn test` runs the unit tests, which need nothing else. The tests that use a PKCS#11 token (run
against SoftHSM2 and against an NSS software token) and the interoperability tests (GnuPG and OpenSSL)
need those tools, so they run in a container and skip themselves otherwise. The token tests take the
token from `PKCS11_LIBRARY` and related variables, so another token can be pointed at them:

```bash
docker build -t commons-crypto-pkcs11-test commons-crypto/commons-crypto/src/test/docker/pkcs11
docker run --rm -v "$PWD:/repo:ro" -v "$HOME/.m2:/root/.m2" commons-crypto-pkcs11-test \
  sh /repo/commons-crypto/commons-crypto/src/test/docker/pkcs11/run-tests.sh
```
