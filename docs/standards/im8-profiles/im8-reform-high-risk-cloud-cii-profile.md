---
title: IM8 Reform High Risk Cloud Cii Profile
profile:
  id: im8-reform-high-risk-cloud-cii-profile
  imports:
    - ../im8-reform-cybersecurity-control-catalog.md
  source: https://info.standards.tech.gov.sg/ssp/high-risk-cloud/
  type: baseline-template
---

# High-Risk Cloud CII Profile

> **Profile interpretation:** IM8 publishes this as a System Security Plan template. This file is a profile-style control selection: it imports the authoritative catalog rather than copying its control prose. It is not a system-specific SSP and does not claim implementation status or evidence.

> Source: [https://info.standards.tech.gov.sg/ssp/high-risk-cloud/](https://info.standards.tech.gov.sg/ssp/high-risk-cloud/). Extracted 7 September 2026.

## Template applicability

- **Name:** High-Risk Cloud System
- **Description:** A generic system hosted on the cloud through a third-party Cloud Service Provider.
- **Security Sensitivity Level:** Confidential, Sensitive High

## Selected controls

The IM8 profile level is retained as source metadata: Level 0 is cardinal and mandatory, Level 1 is basic hygiene subject to risk assessment, and Level 2 is a best practice to consider.

## AS: Application Security

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| AS-1 | Input Validation | 0 |
| AS-2 | Parameterised Interfaces | 1 |
| AS-3 | Output Sanitisation | 0 |
| AS-4 | Authentication Mechanism Rate-Limiting | 0 |
| AS-5 | Password Requirements | 0 |
| AS-6 | Password Salting and Hashing | 0 |
| AS-7 | Access Control Check Enforcement | 0 |
| AS-8 | Secrets Management | 0 |
| AS-9 | Content Security Policy (CSP) | 1 |
| AS-10 | HTTP Strict Transport Security (HSTS) | 2 |
| AS-11 | Session Management | 0 |
| AS-12 | Malware Scanning of Uploaded Files | 0 |
| AS-13 | Exposure of Internal System Details | 1 |
| AS-14 | Secure Cryptographic Libraries | 0 |
| AS-15 | Password Change | 1 |

## SC: Software Supply Chain

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| SC-1 | Code Repository | 1 |
| SC-2 | Commit Signing | 2 |
| SC-3 | Peer Review | 0 |
| SC-4 | Dependency Manifest Version Pinning | 1 |
| SC-5 | Build and Release Process | 1 |
| SC-6 | Dependency Installation during Deployment | 1 |
| SC-7 | Software Artefact Signing | 2 |
| SC-8 | Software Artefact Signature Verification | 2 |
| SC-9 | Internal Code Collaboration and Sharing | 2 |

## ST: Security Testing

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| ST-1 | Vulnerability Assessment | 0 |
| ST-2 | Cloud Security Posture Management | 0 |
| ST-3 | Public Vulnerability Disclosure Programme | 0 |
| ST-4 | Security Testing Programme | 0 |
| ST-5 | Vulnerability Management | 0 |

## NS: Network Security

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| NS-1 | Network and System Component Segmentation | 0 |
| NS-2 | Access Restrictions on CSP Resources Outside Virtual Network | 1 |
| NS-3 | Deny by Default - Allow by Exception | 1 |
| NS-4 | Inter-Private Network Connectivity | 1 |
| NS-5 | Network and Application Layer Filtering | 0 |
| NS-6 | Valid and Trusted SSL/TLS Certificates | 1 |
| NS-7 | Secure Inter-Service Communication | 1 |
| NS-8 | Secure Cloud and On-Premises Connectivity | 0 |
| NS-9 | Intrusion Prevention System (IPS)/Intrusion Detection System (IDS) | 1 |
| NS-10 | Private Network Connectivity | 1 |
| NS-11 | Alerts on Firewall Configuration Changes | 0 |

## BR: Backup and Recovery

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| BR-1 | Backup | 0 |
| BR-2 | Recovery Testing | 0 |
| BR-3 | Backup Retention | 0 |
| BR-4 | Disaster Recovery Plan | 0 |
| BR-5 | Business Continuity Plan | 0 |
| BR-6 | Business Continuity Exercise | 0 |

## DP: Data Protection

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| DP-1 | Data Residency | 0 |
| DP-2 | Data at Rest Encryption | 1 |
| DP-3 | Data in Transit Encryption | 1 |
| DP-4 | Central Cloud Tenant Management | 1 |

## LM: Logging and Monitoring

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| LM-1 | Separate Log Storage | 0 |
| LM-2 | Tamper-Resistant Log Storage | 0 |
| LM-3 | Network Flow Logging | 0 |
| LM-4 | Audit Logging | 0 |
| LM-5 | Database Logging | 0 |
| LM-6 | Access Logging | 0 |
| LM-7 | Host Security Event Logging | 0 |
| LM-8 | Security Log Retention | 0 |
| LM-9 | Security Monitoring and Alerting | 0 |
| LM-10 | Resource Usage Monitoring and Alerting | 1 |
| LM-11 | Service Level Monitoring and Alerting | 1 |
| LM-12 | Central Security Log Management and Monitoring | 0 |
| LM-13 | Anomalous Database Activity Monitoring | 0 |
| LM-14 | Web Defacement Monitoring | 1 |
| LM-15 | Structured Log Formatting | 1 |
| LM-16 | Key Signals Monitoring | 2 |
| LM-17 | Software delivery performance monitoring | 2 |
| LM-19 | Log Sanitisation | 1 |
| LM-21 | Detection Updates | 1 |

## AC: Access Control

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| AC-1 | Principle of Least Privilege | 0 |
| AC-2 | Multi-Factor Authentication (MFA) | 0 |
| AC-3 | Inactive and Expired Accounts | 0 |
| AC-4 | Access Review | 1 |
| AC-5 | Endpoint Device Hardening | 0 |
| AC-6 | Default Credentials | 0 |
| AC-7 | Singpass/Corppass for Public Users | 1 |
| AC-8 | Automated Account Lifecycle Management | 1 |
| AC-9 | Endpoint Device Management | 1 |
| AC-10 | Identity and Device-Based Access Control | 2 |
| AC-11 | Single User Endpoints | 1 |
| AC-12 | Single Sign-On (SSO) for Internal Services and Accounts | 1 |
| AC-13 | Static Credential Expiry and Rotation | 1 |
| AC-14 | Inventory of Accounts | 1 |
| AC-16 | Separation of Duties | 1 |

## CS: Container Security

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| CS-1 | Unique Base Container Image Tags | 2 |
| CS-2 | Minimal Base Container Images | 2 |
| CS-3 | Runtime Container Secrets | 1 |
| CS-4 | Non-Privileged Container User | 1 |
| CS-5 | Dockerfile Linting | 2 |
| CS-6 | Read-Only Container Root Filesystem | 2 |
| CS-7 | Container Image Scanning | 1 |
| CS-8 | Private Container Image Registries | 1 |
| CS-9 | Container Orchestrator API Access Control | 1 |
| CS-10 | Container Workload Segmentation | 1 |
| CS-11 | Container Runtime Security | 2 |

## PM: Security Programme Management

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| PM-1 | Cybersecurity Incident Management Plan | 0 |
| PM-2 | Risk Assessment | 0 |
| PM-3 | System Security Plan (SSP) Development | 0 |
| PM-4 | Approval of Residual Risks | 0 |
| PM-5 | Central Submission of Approved System Security Plan (SSP) | 0 |
| PM-6 | System Documentation | 0 |
| PM-9 | Cybersecurity Incident Response Testing | 0 |
| PM-10 | Cybersecurity Leadership and Oversight | 0 |

## IS: Infrastructure Security

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| IS-1 | Management Agents | 1 |
| IS-2 | Automated Patch Management Tools | 1 |
| IS-3 | Restricted Administrator Privileges | 0 |
| IS-4 | Least Functionality | 1 |
| IS-5 | Host System Hardening | 0 |
| IS-6 | Remote Administration | 1 |
| IS-7 | Malware Protection | 0 |
| IS-8 | Endpoint Detection and Response (EDR) | 1 |
| IS-9 | End-of-Support (EOS) Assets | 1 |
| IS-10 | Synchronise time clocks | 1 |
| IS-11 | Central Domain Name Registration | 0 |
| IS-12 | DNS Security Extensions (DNSSEC) | 1 |
| IS-13 | Defensive Domain Name Registration | 1 |
| IS-14 | Singapore SMS Sender ID Registry Registration | 2 |

## SD: Secure Development

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| SD-1 | Push Protection for Secrets | 1 |
| SD-2 | Default Branch Push Permissions | 1 |
| SD-3 | Continuous Integration (CI) Tests | 1 |
| SD-4 | Static Analysis | 1 |
| SD-5 | Dependency Scanning | 1 |
| SD-6 | Secret Detection | 0 |
| SD-7 | CI Environment Variable Secrets Management | 1 |
| SD-8 | Deployment Environment Segregation | 0 |
| SD-9 | Dynamic Analysis | 2 |
| SD-10 | Secure Software Development Lifecycle (SSDLC) | 1 |

## CK: Cryptography, Encryption and Key Management

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| CK-1 | Cryptographic Key Establishment | 0 |
| CK-2 | Cryptographic Key Rotation | 1 |
| CK-3 | Cryptographic Key Management | 1 |
| CK-4 | Cryptographic Key Storage | 0 |

## HR: Human Resource

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| HR-1 | Security Awareness Training | 0 |
| HR-2 | Security Screening of Employees | 1 |
| HR-3 | Employee Termination Process | 1 |

## RS: Resiliency

| Control ID | Title | IM8 profile level |
| --- | --- | --- |
| RS-1 | Multi-AZ Deployment | 0 |
| RS-2 | Dynamic Resource Scaling | 1 |
| RS-3 | Load Testing | 2 |
