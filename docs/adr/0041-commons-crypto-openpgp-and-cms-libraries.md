# ADR 0041: OpenPGP and CMS libraries on Bouncy Castle, with keys in software or on a token

## Status

Accepted

## Context

The template needs a way to sign, verify, encrypt and decrypt OpenPGP and CMS messages, with a simple
streaming API and with private keys that can stay on a hardware token (PKCS#11). Bouncy Castle has
both formats but a verbose API, and no part of it knows about tokens. The JDK's SunPKCS11 provider
reaches tokens, but it has no RSA OAEP, no Ed25519 or X25519, and no key agreement with a key
derivation function, which Bouncy Castle's CMS and OpenPGP code ask for by name. Spikes against
SoftHSM2 settled what works and what does not; the results are in
[the specification](../specifications/commons-crypto/design.md).

The choices were: one library or one per format; wrapping Bouncy Castle or writing against a
PKCS#11 binding directly; and what to do where SunPKCS11 falls short.

## Decision

`commons-crypto` is three modules under `commons-crypto-parent`, each named after the package it owns:
`commons-crypto` (key handles, PKCS#11 tokens, results, errors), `commons-crypto-cms` and
`commons-crypto-openpgp`. The two format modules depend on the core and never on each other, so an
application takes only the format it uses. It is not part of `commons` and nothing else here
depends on it.

It wraps Bouncy Castle, kept on one version, and does the private key operations through a JCA
provider that the key handle carries, so a token key and a software key are used the same way.
Where SunPKCS11 falls short:

- RSA OAEP on a token is not available. CMS decryption with an RSA token key fails with an error that
  says so, unless the caller allows PKCS#1 v1.5 on that key handle.
- Key agreement with a key derivation function is provided by a small adapter provider, one per open
  token and handed to Bouncy Castle by the key handle. It does the plain ECDH on the token and the
  key derivation in software.
- Ed25519 and X25519 on a token are not supported.

Verification checks signatures and leaves trust to the caller. Old formats and weak algorithms are
refused unless the caller asks for legacy compatibility when reading. Everything streams, which means
decrypted data is written before the final integrity check; the caller discards it if the call throws.

## Consequences

An application chooses a format and a key location by choosing a dependency and a key handle. Token
keys work for signing and for RSA and P-256 decryption, and were tested on SoftHSM2 and NSS, not on
hardware. Nothing is added to the JVM's provider list.

Callers carry two duties: deciding whether a valid signer is trusted, and discarding output when a
call fails. The library will need attention when Bouncy Castle or the JDK change: the adapter and the
OAEP limit exist only because of what they lack today.
