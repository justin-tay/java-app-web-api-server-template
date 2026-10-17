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

- [ ] 2. Modules and build
  - Add `commons-crypto-core`, `commons-crypto-cms` and `commons-crypto-openpgp` to the root
    `pom.xml` and `commons-parent` dependency management, with Java 17, the
    `Automatic-Module-Name` values, Bouncy Castle 1.86 on one version, and the
    integration-test profile.
  - Completion: the modules build under the repository's format, coverage and
    dependency-check checks.
  - _Requirements: R1, R10_

- [ ] 3. Core API (`commons-crypto-core`)
  - `PrivateKeyHandle`, `KeyStores` for PKCS#12 and Java key stores, `SignatureMode`,
    `CryptoException` and its subtypes, and the result types.
  - _Requirements: R5, R6, R9_

- [ ] 4. PKCS#11 key source
  - Token loading, the `PinSupplier` callback, thread safety, close, and
    `TokenUnavailableException` and `PinRequiredException` mapping.
  - Completion: tests against SoftHSM2 including a wrong PIN with no retry.
  - _Requirements: R5_

- [ ] 5. CMS encrypt and decrypt
  - Requests, recipient selection, authenticated content encryption by default, streaming,
    armor, legacy refusal.
  - _Requirements: R2, R4, R7, R8_

- [ ] 6. CMS sign and verify
  - Embedded and detached modes, multiple signers, verification result without chain
    validation.
  - _Requirements: R2, R3, R6_

- [ ] 7. OpenPGP encrypt and decrypt
  - Subkey selection, key ID matching on decrypt, token key binding, MDC and AEAD
    handling, armor, legacy refusal.
  - _Requirements: R2, R4, R7, R8_

- [ ] 8. OpenPGP sign and verify
  - Embedded, detached and cleartext modes, multiple signers, unknown signers in the
    result.
  - _Requirements: R2, R3, R6_

- [ ] 9. Combined operations and shortcuts
  - Sign-and-encrypt, decrypt-and-verify and the `Pgp` and `Cms` static shortcuts for
    both formats.
  - _Requirements: R2_

- [ ] 10. Interoperability tests
  - GnuPG and OpenSSL in both directions for every signature mode, behind the
    integration-test profile.
  - _Requirements: R10_

- [ ] 11. Dependency isolation checks
  - A test or enforcer rule that `commons-crypto-cms` has no `bcpg` and
    `commons-crypto-openpgp` has no `bcpkix` on its classpath, and that neither depends on
    the other.
  - _Requirements: R1_

- [ ] 12. Documentation
  - Javadoc for the failure contract and key handle lifecycle, and a README with the
    one-line shortcuts.
  - _Requirements: R7_

- [x] 13. Spike: ECC on tokens
  - Result: ECDSA works, raw ECDH works with provider attributes, EdDSA and XDH are
    unavailable in SunPKCS11, and an adapter provider makes CMS and PGP ECDH decrypt work
    on a token; see the design's ECDH adapter spike.
  - _Requirements: R5.7_

- [ ] 14. ECDH adapter provider
  - Turn the spike's `TokenEcdh` into the `pkcs11` package adapter: names, key acceptance,
    raw agreement on the token, concatenation KDF and X9.63 KDF in software.
  - Completion: tests compare its KDF output with Bouncy Castle's for each digest and key
    wrap size, and CMS and OpenPGP ECDH decrypt pass on SoftHSM2.
  - _Requirements: R5.8_

- [ ] 15. Token provider configuration and placeholder certificate
  - Build the SunPKCS11 configuration with the derived secret key attributes, and add a
    helper that writes a placeholder certificate for a key without one.
  - _Requirements: R5.9, R5.10_

- [ ] 16. Token RSA decrypt policy
  - `allowPkcs1v15KeyTransport()` on the key handle, and the clear error for CMS OAEP
    through a provider without OAEP.
  - _Requirements: R5.11, R8_

