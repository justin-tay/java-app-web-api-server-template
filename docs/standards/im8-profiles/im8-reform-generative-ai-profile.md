---
title: IM8 Reform Generative Ai Profile
profile:
  id: im8-reform-generative-ai-profile
  imports:
    - ../im8-reform-cybersecurity-control-catalog.md
  source: https://info.standards.tech.gov.sg/ssp/gen-ai/
  type: baseline-template
---

# Generative AI Profile

> **Profile interpretation:** IM8 publishes this as a System Security Plan template. This file is a profile-style control selection: it imports the authoritative catalog rather than copying its control prose. It is not a system-specific SSP and does not claim implementation status or evidence.

> Source: [https://info.standards.tech.gov.sg/ssp/gen-ai/](https://info.standards.tech.gov.sg/ssp/gen-ai/). Extracted 7 September 2026.

## Template applicability

- **Name:** Generative AI System
- **Description:** A generic system that utilises generative AI models.
- **Security Sensitivity Level:** Up to Confidential, Sensitive High

## Selected controls

The IM8 profile level is retained as source metadata: Level 0 is cardinal and mandatory, Level 1 is basic hygiene subject to risk assessment, and Level 2 is a best practice to consider.

## DP: Data Protection

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| DP-8 | Data Classification Disclosure | 1 |

## GA: Generative AI

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| GA-1 | Overseas-hosted GenAI API services | 0 |
| GA-2 | Singapore-hosted GenAI API services | 0 |
| GA-3 | Non-logging and non-training Agreement | 0 |
| GA-4 | Data classification for self-hosted GenAI models | 0 |
| GA-5 | GenAI model formats and loaders | 1 |
| GA-6 | File upload safeguards | 1 |
| GA-7 | Evaluation of GenAI accuracy, safety, and output quality | 1 |
| GA-8 | Inform users about GenAI risks and limitations | 1 |
