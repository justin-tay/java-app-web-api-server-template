---
title: IM8 Reform Sandbox Profile
profile:
  id: im8-reform-sandbox-profile
  imports:
    - ../im8-reform-cybersecurity-control-catalog.md
  source: https://info.standards.tech.gov.sg/ssp/sandbox/
  type: baseline-template
---

# Sandbox Profile

> **Profile interpretation:** IM8 publishes this as a System Security Plan template. This file is a profile-style control selection: it imports the authoritative catalog rather than copying its control prose. It is not a system-specific SSP and does not claim implementation status or evidence.

> Source: [https://info.standards.tech.gov.sg/ssp/sandbox/](https://info.standards.tech.gov.sg/ssp/sandbox/). Extracted 7 September 2026.

## Template applicability

- **Name:** Pilot Sandbox System
- **Description:** A generic pilot sandbox system.
- **Security Sensitivity Level:** Restricted, Sensitive Normal

## Selected controls

The IM8 profile level is retained as source metadata: Level 0 is cardinal and mandatory, Level 1 is basic hygiene subject to risk assessment, and Level 2 is a best practice to consider.

## AS: Application Security

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| AS-1 | Input Validation | 2 |
| AS-2 | Parameterised Interfaces | 2 |
| AS-3 | Output Sanitisation | 2 |
| AS-4 | Authentication Mechanism Rate-Limiting | 2 |
| AS-5 | Password Requirements | 2 |
| AS-6 | Password Salting and Hashing | 2 |
| AS-7 | Access Control Check Enforcement | 2 |
| AS-8 | Secrets Management | 2 |
| AS-9 | Content Security Policy (CSP) | 2 |
| AS-10 | HTTP Strict Transport Security (HSTS) | 2 |
| AS-11 | Session Management | 2 |
| AS-12 | Malware Scanning of Uploaded Files | 2 |
| AS-13 | Exposure of Internal System Details | 2 |
| AS-14 | Secure Cryptographic Libraries | 2 |
| AS-15 | Password Change | 2 |

## SC: Software Supply Chain

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| SC-1 | Code Repository | 2 |
| SC-2 | Commit Signing | 2 |
| SC-3 | Peer Review | 2 |
| SC-4 | Dependency Manifest Version Pinning | 2 |
| SC-5 | Build and Release Process | 2 |
| SC-6 | Dependency Installation during Deployment | 2 |
| SC-7 | Software Artefact Signing | 2 |
| SC-8 | Software Artefact Signature Verification | 2 |
| SC-9 | Internal Code Collaboration and Sharing | 2 |

## ST: Security Testing

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| ST-1 | Vulnerability Assessment | 2 |
| ST-2 | Cloud Security Posture Management | 2 |
| ST-3 | Public Vulnerability Disclosure Programme | 2 |
| ST-4 | Security Testing Programme | 2 |
| ST-5 | Vulnerability Management | 2 |

## NS: Network Security

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| NS-1 | Network and System Component Segmentation | 2 |
| NS-2 | Access Restrictions on CSP Resources Outside Virtual Network | 2 |
| NS-3 | Deny by Default - Allow by Exception | 2 |
| NS-4 | Inter-Private Network Connectivity | 2 |
| NS-5 | Network and Application Layer Filtering | 2 |
| NS-6 | Valid and Trusted SSL/TLS Certificates | 2 |
| NS-7 | Secure Inter-Service Communication | 2 |
| NS-8 | Secure Cloud and On-Premises Connectivity | 2 |

## BR: Backup and Recovery

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| BR-1 | Backup | 2 |
| BR-2 | Recovery Testing | 2 |
| BR-3 | Backup Retention | 2 |

## DP: Data Protection

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| DP-1 | Data Residency | 2 |
| DP-2 | Data at Rest Encryption | 2 |
| DP-3 | Data in Transit Encryption | 2 |
| DP-4 | Central Cloud Tenant Management | 2 |

## LM: Logging and Monitoring

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| LM-1 | Separate Log Storage | 2 |
| LM-2 | Tamper-Resistant Log Storage | 2 |
| LM-3 | Network Flow Logging | 2 |
| LM-4 | Audit Logging | 2 |
| LM-5 | Database Logging | 2 |
| LM-6 | Access Logging | 2 |
| LM-7 | Host Security Event Logging | 2 |
| LM-8 | Security Log Retention | 2 |
| LM-9 | Security Monitoring and Alerting | 2 |
| LM-10 | Resource Usage Monitoring and Alerting | 2 |
| LM-11 | Service Level Monitoring and Alerting | 2 |
| LM-12 | Central Security Log Management and Monitoring | 2 |
| LM-13 | Anomalous Database Activity Monitoring | 2 |
| LM-14 | Web Defacement Monitoring | 2 |
| LM-15 | Structured Log Formatting | 2 |
| LM-16 | Key Signals Monitoring | 2 |
| LM-17 | Software delivery performance monitoring | 2 |
| LM-19 | Log Sanitisation | 2 |

## AC: Access Control

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| AC-1 | Principle of Least Privilege | 2 |
| AC-2 | Multi-Factor Authentication (MFA) | 2 |
| AC-3 | Inactive and Expired Accounts | 2 |
| AC-4 | Access Review | 2 |
| AC-5 | Endpoint Device Hardening | 2 |
| AC-6 | Default Credentials | 2 |
| AC-7 | Singpass/Corppass for Public Users | 2 |
| AC-8 | Automated Account Lifecycle Management | 2 |
| AC-9 | Endpoint Device Management | 2 |
| AC-10 | Identity and Device-Based Access Control | 2 |
| AC-11 | Single User Endpoints | 2 |
| AC-12 | Single Sign-On (SSO) for Internal Services and Accounts | 2 |
| AC-13 | Static Credential Expiry and Rotation | 2 |
| AC-14 | Inventory of Accounts | 2 |

## CS: Container Security

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| CS-1 | Unique Base Container Image Tags | 2 |
| CS-2 | Minimal Base Container Images | 2 |
| CS-3 | Runtime Container Secrets | 2 |
| CS-4 | Non-Privileged Container User | 2 |
| CS-5 | Dockerfile Linting | 2 |
| CS-6 | Read-Only Container Root Filesystem | 2 |
| CS-7 | Container Image Scanning | 2 |
| CS-8 | Private Container Image Registries | 2 |
| CS-9 | Container Orchestrator API Access Control | 2 |
| CS-10 | Container Workload Segmentation | 2 |
| CS-11 | Container Runtime Security | 2 |

## PM: Security Programme Management

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| PM-1 | Cybersecurity Incident Management Plan | 2 |
| PM-2 | Risk Assessment | 2 |
| PM-3 | System Security Plan (SSP) Development | 0 |
| PM-4 | Approval of Residual Risks | 0 |
| PM-5 | Central Submission of Approved System Security Plan (SSP) | 0 |
| PM-6 | System Documentation | 2 |

## IS: Infrastructure Security

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| IS-1 | Management Agents | 2 |
| IS-2 | Automated Patch Management Tools | 2 |
| IS-3 | Restricted Administrator Privileges | 2 |
| IS-4 | Least Functionality | 2 |
| IS-5 | Host System Hardening | 2 |
| IS-6 | Remote Administration | 2 |
| IS-7 | Malware Protection | 2 |
| IS-8 | Endpoint Detection and Response (EDR) | 2 |
| IS-9 | End-of-Support (EOS) Assets | 2 |
| IS-10 | Synchronise time clocks | 2 |
| IS-11 | Central Domain Name Registration | 2 |
| IS-12 | DNS Security Extensions (DNSSEC) | 2 |
| IS-13 | Defensive Domain Name Registration | 2 |
| IS-14 | Singapore SMS Sender ID Registry Registration | 2 |

## SD: Secure Development

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| SD-1 | Push Protection for Secrets | 2 |
| SD-2 | Default Branch Push Permissions | 2 |
| SD-3 | Continuous Integration (CI) Tests | 2 |
| SD-4 | Static Analysis | 2 |
| SD-5 | Dependency Scanning | 2 |
| SD-6 | Secret Detection | 2 |
| SD-7 | CI Environment Variable Secrets Management | 2 |
| SD-8 | Deployment Environment Segregation | 2 |

## CK: Cryptography, Encryption and Key Management

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| CK-1 | Cryptographic Key Establishment | 2 |
| CK-2 | Cryptographic Key Rotation | 2 |
