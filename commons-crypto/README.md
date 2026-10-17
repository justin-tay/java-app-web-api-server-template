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

The design, requirements and the results of the hardware and interoperability checks are in
[the specification](../docs/specifications/commons-crypto/design.md).

## Quick start

Every operation has a one-line form for the defaults, in `Pgp` and in `Cms`:

```java
PgpPublicKeys recipient = PgpPublicKeys.fromArmored(recipientAsc);
Pgp.encrypt(in, out, recipient);

PgpSecretKeys mine = PgpSecretKeys.fromArmored(secretAsc, passphrase);
Pgp.decrypt(in, out, mine);

Pgp.signDetached(data, signatureOut, mine);
VerifyResult result = Pgp.verifyDetached(data, signatureIn, signerPublicKeys);
```

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

## Defaults and policy

- Encryption uses AES-256 in an authenticated mode: CMS `AuthEnvelopedData` with GCM, and OpenPGP
  with a modification detection code. CMS RSA recipients get OAEP with SHA-256; EC recipients get
  ECDH.
- Messages that are not integrity protected, use weak ciphers, or use PKCS#1 v1.5 key transport, and
  signatures that use SHA-1, are refused. `legacyCompatibility(true)` on the decrypt or verify
  request accepts them for reading only.
- Verification reports every signature with a status: `VALID`, `INVALID` or `UNKNOWN_SIGNER`. It
  checks signatures only. It does not build certificate chains or check revocation, and it does not
  decide whether a signer is trusted: that is for the caller.

## Streaming and failures

Everything streams. A signature or an integrity tag is at the end of the data, so decrypted or
signed content is written before it is known to be intact. If a call throws
`IntegrityException`, discard what was written to the output stream. To release data only after the
check, write to a temporary file or buffer and move it on success.

All exceptions are unchecked and extend `CryptoException`: `NoMatchingKeyException`,
`IntegrityException`, `PinRequiredException` and `TokenUnavailableException`. Messages never contain
a PIN, a key or message content.

## Tests

`mvn test` runs the unit tests, which need nothing else. The tests that use a hardware token
(SoftHSM2) and the interoperability tests (GnuPG and OpenSSL) need those tools, so they run in a
container and skip themselves otherwise:

```bash
docker build -t commons-crypto-pkcs11-test commons-crypto/commons-crypto/src/test/docker/pkcs11
docker run --rm -v "$PWD:/repo:ro" -v "$HOME/.m2:/root/.m2" commons-crypto-pkcs11-test \
  sh /repo/commons-crypto/commons-crypto/src/test/docker/pkcs11/run-tests.sh
```
