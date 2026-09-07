---
title: IM8 Reform Low Risk On Premises Profile
profile:
  id: im8-reform-low-risk-on-premises-profile
  imports:
    - ../im8-reform-cybersecurity-control-catalog.md
  source: https://info.standards.tech.gov.sg/ssp/low-risk-on-premises/
  type: baseline-template
---

# Low-Risk On Premises Profile

> **Profile interpretation:** IM8 publishes this as a System Security Plan template. This file is a profile-style control selection: it imports the authoritative catalog rather than copying its control prose. It is not a system-specific SSP and does not claim implementation status or evidence.

> Source: [https://info.standards.tech.gov.sg/ssp/low-risk-on-premises/](https://info.standards.tech.gov.sg/ssp/low-risk-on-premises/). Extracted 7 September 2026.

## Template applicability

- **Name:** Low-Risk On-Premises System
- **Description:** A generic system hosted on-premises.
- **Security Sensitivity Level:** Up to Restricted, Sensitive Normal

## Selected controls

The IM8 profile level is retained as source metadata: Level 0 is cardinal and mandatory, Level 1 is basic hygiene subject to risk assessment, and Level 2 is a best practice to consider.

## AS: Application Security

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| AS-1 | Input Validation | 1 |
| AS-2 | Parameterised Interfaces | 1 |
| AS-3 | Output Sanitisation | 1 |
| AS-4 | Authentication Mechanism Rate-Limiting | 1 |
| AS-5 | Password Requirements | 1 |
| AS-6 | Password Salting and Hashing | 1 |
| AS-7 | Access Control Check Enforcement | 1 |
| AS-8 | Secrets Management | 1 |
| AS-9 | Content Security Policy (CSP) | 1 |
| AS-10 | HTTP Strict Transport Security (HSTS) | 2 |
| AS-11 | Session Management | 1 |
| AS-12 | Malware Scanning of Uploaded Files | 2 |
| AS-13 | Exposure of Internal System Details | 2 |
| AS-14 | Secure Cryptographic Libraries | 2 |
| AS-15 | Password Change | 1 |

## SC: Software Supply Chain

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| SC-1 | Code Repository | 1 |
| SC-2 | Commit Signing | 2 |
| SC-3 | Peer Review | 1 |
| SC-4 | Dependency Manifest Version Pinning | 1 |
| SC-5 | Build and Release Process | 1 |
| SC-6 | Dependency Installation during Deployment | 1 |
| SC-7 | Software Artefact Signing | 2 |
| SC-8 | Software Artefact Signature Verification | 2 |
| SC-9 | Internal Code Collaboration and Sharing | 2 |

## ST: Security Testing

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| ST-1 | Vulnerability Assessment | 1 |
| ST-3 | Public Vulnerability Disclosure Programme | 1 |
| ST-4 | Security Testing Programme | 1 |
| ST-5 | Vulnerability Management | 1 |

## NS: Network Security

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| NS-1 | Network and System Component Segmentation | 1 |
| NS-3 | Deny by Default - Allow by Exception | 1 |
| NS-5 | Network and Application Layer Filtering | 1 |
| NS-6 | Valid and Trusted SSL/TLS Certificates | 1 |
| NS-8 | Secure Cloud and On-Premises Connectivity | 1 |
| NS-9 | Intrusion Prevention System (IPS)/Intrusion Detection System (IDS) | 1 |
| NS-10 | Private Network Connectivity | 1 |
| NS-11 | Alerts on Firewall Configuration Changes | 2 |

## BR: Backup and Recovery

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| BR-1 | Backup | 1 |
| BR-2 | Recovery Testing | 2 |
| BR-3 | Backup Retention | 1 |

## DP: Data Protection

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| DP-1 | Data Residency | 0 |
| DP-2 | Data at Rest Encryption | 1 |
| DP-3 | Data in Transit Encryption | 1 |
| DP-5 | Sanitisation | 1 |
| DP-6 | Witness Sanitisation and Destruction of Storage Devices | 2 |

## LM: Logging and Monitoring

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| LM-1 | Separate Log Storage | 1 |
| LM-2 | Tamper-Resistant Log Storage | 1 |
| LM-5 | Database Logging | 2 |
| LM-6 | Access Logging | 1 |
| LM-7 | Host Security Event Logging | 1 |
| LM-8 | Security Log Retention | 1 |
| LM-9 | Security Monitoring and Alerting | 1 |
| LM-11 | Service Level Monitoring and Alerting | 2 |
| LM-12 | Central Security Log Management and Monitoring | 0 |
| LM-13 | Anomalous Database Activity Monitoring | 2 |
| LM-14 | Web Defacement Monitoring | 2 |
| LM-15 | Structured Log Formatting | 2 |
| LM-16 | Key Signals Monitoring | 2 |
| LM-17 | Software delivery performance monitoring | 2 |
| LM-19 | Log Sanitisation | 2 |

## AC: Access Control

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| AC-1 | Principle of Least Privilege | 1 |
| AC-2 | Multi-Factor Authentication (MFA) | 1 |
| AC-3 | Inactive and Expired Accounts | 1 |
| AC-4 | Access Review | 1 |
| AC-5 | Endpoint Device Hardening | 1 |
| AC-6 | Default Credentials | 1 |
| AC-7 | Singpass/Corppass for Public Users | 1 |
| AC-8 | Automated Account Lifecycle Management | 1 |
| AC-9 | Endpoint Device Management | 1 |
| AC-10 | Identity and Device-Based Access Control | 2 |
| AC-11 | Single User Endpoints | 2 |
| AC-12 | Single Sign-On (SSO) for Internal Services and Accounts | 1 |
| AC-13 | Static Credential Expiry and Rotation | 2 |
| AC-14 | Inventory of Accounts | 1 |

## PM: Security Programme Management

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| PM-1 | Cybersecurity Incident Management Plan | 1 |
| PM-2 | Risk Assessment | 1 |
| PM-3 | System Security Plan (SSP) Development | 0 |
| PM-4 | Approval of Residual Risks | 0 |
| PM-5 | Central Submission of Approved System Security Plan (SSP) | 0 |
| PM-6 | System Documentation | 1 |

## IS: Infrastructure Security

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| IS-2 | Automated Patch Management Tools | 1 |
| IS-3 | Restricted Administrator Privileges | 1 |
| IS-4 | Least Functionality | 1 |
| IS-5 | Host System Hardening | 1 |
| IS-7 | Malware Protection | 1 |
| IS-8 | Endpoint Detection and Response (EDR) | 2 |
| IS-9 | End-of-Support (EOS) Assets | 1 |
| IS-10 | Synchronise time clocks | 1 |
| IS-11 | Central Domain Name Registration | 0 |
| IS-12 | DNS Security Extensions (DNSSEC) | 2 |
| IS-13 | Defensive Domain Name Registration | 2 |
| IS-14 | Singapore SMS Sender ID Registry Registration | 0 |

## SD: Secure Development

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| SD-1 | Push Protection for Secrets | 1 |
| SD-2 | Default Branch Push Permissions | 1 |
| SD-3 | Continuous Integration (CI) Tests | 2 |
| SD-4 | Static Analysis | 1 |
| SD-5 | Dependency Scanning | 1 |
| SD-6 | Secret Detection | 1 |
| SD-7 | CI Environment Variable Secrets Management | 1 |
| SD-8 | Deployment Environment Segregation | 1 |

## DC: Datacentre

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| DC-1 | Separate hosting | 1 |
| DC-2 | Physical Access Controls | 1 |

## CK: Cryptography, Encryption and Key Management

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| CK-1 | Cryptographic Key Establishment | 2 |
| CK-2 | Cryptographic Key Rotation | 2 |
