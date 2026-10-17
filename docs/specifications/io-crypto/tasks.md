# Implementation Plan: io-crypto

## Sequence

The riskiest piece, a private-key operation on a token, is proven first. Then the shared
root package, then CMS, then OpenPGP, then the build checks and documentation. Each task
is independently testable and ordered by its dependencies.

- [ ] 1. Spike: RSA decrypt and sign with a non-extractable SoftHSM2 key through SunPKCS11
  - Decrypt a CMS key-transport recipient with OAEP, and an OpenPGP session key with
    `CKM_RSA_PKCS`, using Bouncy Castle JCE operators bound to the provider.
  - Completion: both decrypt through the token, and the findings are recorded in the
    design's hardware section.
  - _Requirements: R5_

- [ ] 2. Repo and build
  - Multi-module Maven project with `io-crypto`, Java 17, `Automatic-Module-Name`,
    `bcprov` required and `bcpg` and `bcpkix` optional, and the integration-test profile.
  - _Requirements: R1, R10_

- [ ] 3. Shared root package
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
  - CI variants running CMS tests without `bcpg` and OpenPGP tests without `bcpkix`.
  - _Requirements: R1_

- [ ] 12. Documentation
  - Javadoc for the failure contract and key handle lifecycle, and a README with the
    one-line shortcuts.
  - _Requirements: R7_

- [ ] 13. Spike (later): ECC on tokens
  - Confirm raw ECDH and EdDSA through SunPKCS11 and decide whether Bouncy Castle's PGP
    ECDH can use it.
  - _Requirements: R5.7_
