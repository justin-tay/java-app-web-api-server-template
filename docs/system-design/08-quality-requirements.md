# 8. Quality Requirements

## Basis

`docs/standards/iso-25010-2023-quality-characteristics.md` retains the
ISO/IEC 25010:2023 product-quality model as reference material: nine quality
characteristics (functional suitability, performance efficiency,
compatibility, interaction capability, reliability, security,
maintainability, flexibility, safety) and their subcharacteristics. This
chapter records how the template currently addresses them, so gaps are
visible rather than implicit.

## Where a control implementation already exists

**Security** is the only characteristic with a complete, verification-item
by verification-item control implementation: the
[OWASP ASVS control implementation](06-security/asvs.md) maps ASVS requirements to what is
implemented, what is a deployment decision, and what is unimplemented. That
document, not this chapter, is the source of truth for the template's
security posture.

## Other characteristics

The remaining ISO/IEC 25010 characteristics do not yet have an equivalent
control implementation in this template. Adding one, following the same
pattern as
[the ASVS control implementation](06-security/asvs.md) (one row per requirement, a status,
and evidence pointing at the responsible code or document), is future work,
not something this chapter should assert without the underlying assessment
having actually been done.

## Other retained standards

`docs/standards/` also retains Singapore's IM8 cybersecurity and digital
service standard control catalogs, and a set of IM8 risk/impact profiles.
Like ISO/IEC 25010, these are kept as external reference material, not
reproduced or paraphrased here; consult them directly in
[docs/standards/](../standards/).
