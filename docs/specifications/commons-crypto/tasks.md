# Implementation Plan: commons-crypto

## Sequence

The riskiest piece, a private-key operation on a token, is proven first. Then the shared
root package, then CMS, then OpenPGP, then the build checks and documentation. Each task
is independently testable and ordered by its dependencies.

- [x] 1. Spike: RSA decrypt and sign with a non-extractable SoftHSM2 key through SunPKCS11
  - Decrypt a CMS key-transport recipient with OAEP, and an OpenPGP session key with
    `CKM_RSA_PKCS`, using Bouncy Castle JCE operators bound to the provider.
  - Completion: both decrypt through the token, and the findings are recorded in the
    design's hardware section.
  - Result: PGP decrypt and sign, and CMS sign and PKCS#1 v1.5 decrypt, work. CMS OAEP does
    not work through SunPKCS11; see the design's spike findings and the open decision.
  - _Requirements: R5_

- [x] 2. Modules and build
  - Add `commons-crypto-parent` with `commons-crypto`, `commons-crypto-cms` and
    `commons-crypto-openpgp` to the root `pom.xml` and `commons-parent` dependency management, with Java 17, the
    `Automatic-Module-Name` values, Bouncy Castle 1.86 on one version, and the
    integration-test profile.
  - Completion: the modules build under the repository's format and coverage checks. The
    dependency-check profile has not been run on them.
  - _Requirements: R1, R10_

- [x] 3. Core API (`commons-crypto`)
  - `PrivateKeyHandle`, `KeyStores` for PKCS#12 and Java key stores, `SignatureMode`,
    `CryptoException` and its subtypes, and the result types.
  - _Requirements: R5, R6, R9_
  - `PinSupplier` and `TokenInfo` move to task 4 with the PKCS#11 key source.

- [x] 4. PKCS#11 key source
  - Token loading, the `PinSupplier` callback, thread safety, close, and
    `TokenUnavailableException` and `PinRequiredException` mapping.
  - Completion: tests against SoftHSM2 including a wrong PIN with no retry.
  - _Requirements: R5_

- [x] 5. CMS encrypt and decrypt
  - Requests, recipient selection, authenticated content encryption by default, streaming,
    armor, legacy refusal.
  - _Requirements: R2, R4, R7, R8_

- [x] 6. CMS sign and verify
  - Embedded and detached modes, multiple signers, verification result without chain
    validation.
  - _Requirements: R2, R3, R6_

- [x] 7. OpenPGP encrypt and decrypt
  - Subkey selection, key ID matching on decrypt, token key binding, MDC and AEAD
    handling, armor, legacy refusal.
  - _Requirements: R2, R4, R7, R8_

- [x] 8. OpenPGP sign and verify
  - Embedded, detached and cleartext modes, multiple signers, unknown signers in the
    result.
  - _Requirements: R2, R3, R6_

- [x] 9. Combined operations and shortcuts
  - Sign-and-encrypt, decrypt-and-verify and the `Pgp` and `Cms` static shortcuts for
    both formats.
  - _Requirements: R2_

- [x] 10. Interoperability tests
  - GnuPG and OpenSSL in both directions for every signature mode, behind the
    integration-test profile.
  - _Requirements: R10_

- [x] 11. Dependency isolation checks
  - A test or enforcer rule that `commons-crypto-cms` has no `bcpg` and
    `commons-crypto-openpgp` has no `bcpkix` on its classpath, and that neither depends on
    the other.
  - _Requirements: R1_

- [x] 12. Documentation
  - Javadoc for the failure contract and key handle lifecycle, and a README with the
    one-line shortcuts.
  - Done: the README is `commons-crypto/README.md`, the Javadoc states the failure contract and
    the handle lifecycle, and it builds without errors. About 60 "no comment" warnings remain on
    trivial constructors, builder setters and factory methods.
  - _Requirements: R7_

- [x] 13. Spike: ECC on tokens
  - Result: ECDSA works, raw ECDH works with provider attributes, EdDSA and XDH are
    unavailable in SunPKCS11, and an adapter provider makes CMS and PGP ECDH decrypt work
    on a token; see the design's ECDH adapter spike.
  - _Requirements: R5.7_

- [x] 14. ECDH adapter provider
  - Turn the spike's `TokenEcdh` into the `pkcs11` package adapter: names, key acceptance,
    raw agreement on the token, concatenation KDF and X9.63 KDF in software.
  - Completion: tests compare its KDF output with Bouncy Castle's for each digest and key
    wrap size, and CMS and OpenPGP ECDH decrypt pass on SoftHSM2.
  - _Requirements: R5.8_

- [x] 15. Placeholder certificate for token keys
  - The SunPKCS11 configuration with the derived secret key attributes is built by
    `Pkcs11Config`. SunPKCS11 lists a private key only if the token also holds a certificate
    for it, and cannot address a key that has none, so a Java helper cannot add one. The
    procedure is documented in the design (token keys without a certificate) instead.

- [x] 16. Token RSA decrypt policy
  - `allowPkcs1v15KeyTransport()` on the key handle, and the clear error for CMS OAEP
    through a provider without OAEP.
  - _Requirements: R5.11, R8_

- [x] 17. Hardening from the review
  - A shared Bouncy Castle provider in the core module, the handle's own provider for EC key
    agreement, compression nesting and output limits, an armor line cap, and closeable OpenPGP
    secret keys.
  - _Requirements: R5b, R7b_

- [x] 18. A second PKCS#11 implementation
  - Run the token tests against NSS softoken as well as SoftHSM2; make the tests choose the token from
    the environment; add `Pkcs11Config.Builder.extraSettings` for module-specific settings.
  - Result: all token tests pass on both; the OpenPGP signer no longer asks the token's provider
    for digests.
  - _Requirements: R5_

- [x] 19. Review follow-up
  - CMS output limit, token failures during operations, the shared stream helpers and test support, the
    integrity classification, primary key validity, removal of `clear()`, the streaming memory test,
    the ADR, and the README additions.
  - _Requirements: R5.6, R7b.2_
