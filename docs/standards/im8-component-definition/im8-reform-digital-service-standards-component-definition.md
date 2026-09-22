---
title: IM8 Reform Digital Service Standards Control Catalog Component Definition
component-definition:
  id: im8-reform-digital-service-standards-component-definition
  imports:
    - ../im8-reform-digital-service-standards-control-catalog.md
  component:
    name: java-app-web-api-server
    type: software
    title: Spring Boot Web API Server Template
---

# IM8 Reform Digital Service Standards Control Catalog Component Definition

This is a [control implementation](../../adr/0003-control-implementation-terminology.md) for the [IM8 Reform Digital Service Standards Control Catalog](../im8-reform-digital-service-standards-control-catalog.md), in the same style as the [Cybersecurity Control Catalog component definition](im8-reform-cybersecurity-component-definition.md): for each control in the imported catalog, it records this template's implementation status and, where relevant, an implementation statement.

Unlike the Cybersecurity catalog, almost the entire Digital Service Standards catalog turns out not to apply to this component. This template is a backend administrative API: it renders no web page, mobile screen, or transaction flow of its own. Its only HTML-producing surface is Spring Security's default login page, which is a framework fallback rather than a page the template designs, and authentication itself is delegated to Keycloak. Every control in this catalog that asks about a page, a form, a piece of content, a transaction, or a WCAG success criterion is therefore a concern for whatever web or mobile frontend an adopter eventually builds against this API, not for the template itself.

## Scope and status meanings

The five statuses below carry the same meaning as OSCAL's `implementation-status` vocabulary (`implemented`, `alternative`, `partial`, `planned`, `not-applicable`), written as prose here rather than as the standard's literal enum tokens; see the [Cybersecurity Control Catalog component definition](im8-reform-cybersecurity-component-definition.md#scope-and-status-meanings) for the full definitions.

| Status | Meaning |
| --- | --- |
| Implemented | The template's own code or configuration satisfies the control. |
| Alternative | The control is satisfied through a different mechanism than the one it describes, typically by requiring and integrating with an external identity provider (Keycloak). |
| Partial | The template provides part of what the control asks for; the remainder depends on a deployment or product choice outside the template's code. |
| Planned | The control is one this template's own codebase could reasonably satisfy, but does not yet. |
| Not applicable | The control addresses a page, a piece of content, a transaction flow, or a UI surface this template does not render; it becomes relevant only for the frontend an adopter builds against this API. |

## Baseline Design Practices

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| BD-1 | Responsive Web Design | Not applicable | The template has no web-based UI of its own to make responsive; it is a JSON API consumed by whatever frontend an adopter builds. |
| BD-2 | Site Search | Not applicable | The template serves no multi-page website of its own to search. |
| BD-3 | Support multiple languages | Not applicable | The template has no user-facing content of its own to localise. |
| BD-4 | Clear and Concise Content | Not applicable | The template has no user-facing content of its own. |
| BD-5 | Search Engine Optimisation | Not applicable | The template has no publicly indexable web pages of its own. |
| BD-6 | Consistent UI Design | Not applicable | The template has no UI of its own to apply a design system to. |
| BD-7 | Mandatory and Optional Fields | Not applicable | The template's admin endpoints accept JSON request bodies validated by Bean Validation; there is no visual form with fields to mark mandatory or optional. |
| BD-8 | Log-in Indication | Not applicable | The login page, if any, is rendered by Keycloak; the template renders no page after login of its own to display an identifier on. |
| BD-9 | Contact Channels | Not applicable | The template has no UI of its own to place a contact channel in. |

## Performance and Reliability

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| PR-1 | Digital Service Review | Not applicable | Reviewing whether a deployed system built from this template is still needed is a product/organisational decision, not something the template's code does. |
| PR-2 | Digital Service Registration | Not applicable | The template is an internal/administrative API template, not a registered public-facing digital service. |
| PR-3 | Digital Service Availability | Not applicable | Service availability is a deployment/operational responsibility, not an application-code concern. |
| PR-4 | Notify of Scheduled Downtime | Not applicable | Notifying users of downtime requires a UI or communication channel the template does not provide. |
| PR-5 | Manage Broken Links | Not applicable | The template has no hyperlinked content of its own. |
| PR-6 | Browser Compatibility | Not applicable | The template renders no browser-facing pages of its own beyond Keycloak's login page, which is outside the template's control. |
| PR-7 | Optimise Load Times | Not applicable | The template returns JSON API responses; it has no page load of its own to measure against a load-time target. |

## Transactions and Payments

The template exposes an administrative JSON API with no end-user transaction or payment feature; every control in this group is not applicable for the same underlying reason, listed individually below for completeness.

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| TX-1 | Digital-First Approach | Not applicable | The template has no transaction flow of its own. |
| TX-2 | Transaction Prerequisites | Not applicable | The template has no transaction flow of its own. |
| TX-3 | Break Down Long Transactions | Not applicable | The template has no multi-step transaction UI of its own. |
| TX-4 | Progress Indicators | Not applicable | The template has no multi-step transaction UI of its own. |
| TX-5 | Save Draft Function | Not applicable | The template has no multi-step transaction UI of its own. |
| TX-6 | Pre-fill Data | Not applicable | The template has no form UI of its own to pre-fill. |
| TX-7 | Payment and Refund | Not applicable | The template has no payment feature. |
| TX-8 | Managing Stored Payment Details | Not applicable | The template has no payment feature and stores no payment details. |
| TX-9 | Success or failure message | Not applicable | The template has no transaction UI of its own to display a message in. |
| TX-10 | Failed Transaction Details | Not applicable | The template has no transaction UI of its own. |
| TX-11 | Payment details | Not applicable | The template has no payment feature. |
| TX-12 | Transaction Outcome | Not applicable | The template has no transaction flow of its own. |
| TX-13 | Post-transaction Acknowledgement | Not applicable | The template has no transaction flow of its own to acknowledge. |
| TX-14 | Transaction Status Updates | Not applicable | The template has no transaction flow of its own. |
| TX-15 | Tracking Transaction Status | Not applicable | The template has no transaction flow of its own. |

## Trust and Legitimacy

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| TL-1 | Official Government Domain | Not applicable | Domain registration belongs to whichever deployment hosts the system built from this template. |
| TL-2 | Agency or Initiative Logo | Not applicable | The template has no UI of its own to place a logo in. |
| TL-3 | Official Government Banner | Not applicable | The template has no UI of its own. |
| TL-4 | Official Government Footer | Not applicable | The template has no UI or footer of its own. |
| TL-5 | Mobile App Ownership and Distribution | Not applicable | The template is a backend API, not a mobile application. |
| TL-6 | Application Store Listings | Not applicable | The template is a backend API, not a mobile application. |

## Understand Users

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| UU-1 | Understand user needs | Not applicable | User research about the eventual system's Public Users is a product/organisational activity outside the template's own code. |
| UU-2 | Test with users | Not applicable | Usability testing applies to the frontend an adopter builds against this API, not to the template's code. |

## WCAG: Operable

Every control in this group evaluates an interactive web or mobile UI. The template renders none of its own (see the introduction above), so the whole group is not applicable; each row is listed individually below for completeness against the imported catalog.

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| WO-1 | Keyboard equivalent | Not applicable | The template has no interactive UI of its own. |
| WO-2 | No Keyboard Trap | Not applicable | The template has no interactive UI of its own. |
| WO-3 | Character Key Shortcuts | Not applicable | The template has no interactive UI of its own. |
| WO-4 | Adjustable Timings | Not applicable | The template has no time-limited UI of its own. |
| WO-5 | Pause, Stop, Hide | Not applicable | The template has no moving or auto-updating content of its own. |
| WO-6 | Reduce Flash Triggers | Not applicable | The template has no visual content of its own. |
| WO-7 | Bypass Repeating Content | Not applicable | The template has no multi-page UI of its own. |
| WO-8 | Page Title And Purpose | Not applicable | The template has no pages of its own to title. |
| WO-9 | Sequential Focus Order | Not applicable | The template has no interactive UI of its own. |
| WO-10 | Link Text And Purpose | Not applicable | The template has no hyperlinked content of its own. |
| WO-11 | Multiple Ways | Not applicable | The template has no navigable content of its own. |
| WO-12 | Headings and Labels | Not applicable | The template has no content of its own to label. |
| WO-13 | Focus Visible and Not Obscured | Not applicable | The template has no interactive UI of its own. |
| WO-14 | Simple Pointer Alternatives | Not applicable | The template has no pointer-based interactions of its own. |
| WO-15 | Pointer Cancellation | Not applicable | The template has no pointer-based interactions of its own. |
| WO-16 | Label In Name | Not applicable | The template has no interactive UI of its own. |
| WO-17 | Motion Actuation | Not applicable | The template has no motion-operated features. |
| WO-18 | Minimum Pointer Target Size | Not applicable | The template has no pointer-based UI of its own. |

## WCAG: Perceivable

Every control in this group evaluates content the template does not render (see the introduction above); each row is listed individually below for completeness against the imported catalog.

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| WP-1 | Text Alternatives for Non-Text content | Not applicable | The template has no non-text content of its own. |
| WP-2 | Captions for Prerecorded Media | Not applicable | The template has no media content of its own. |
| WP-3 | Text or Audio Alternatives for Prerecorded Media | Not applicable | The template has no media content of its own. |
| WP-4 | Live Captions | Not applicable | The template has no live media content of its own. |
| WP-5 | Audio Description for Prerecorded Video Content | Not applicable | The template has no video content of its own. |
| WP-6 | Presentation of Info and Relationships | Not applicable | The template has no content of its own to structure. |
| WP-7 | Meaningful Content Order | Not applicable | The template has no content of its own to order. |
| WP-8 | Describing Displayed Controls | Not applicable | The template has no UI controls of its own. |
| WP-9 | Display Orientation | Not applicable | The template has no display of its own. |
| WP-10 | Identify Input Purpose | Not applicable | The template's inputs are JSON request fields validated by Bean Validation, not a labelled UI form. |
| WP-11 | Use of Color | Not applicable | The template has no visual content of its own. |
| WP-12 | Audio Control | Not applicable | The template has no audio content of its own. |
| WP-13 | Minimum Contrast | Not applicable | The template has no visual content of its own. |
| WP-14 | Text Scaling | Not applicable | The template has no rendered text of its own. |
| WP-15 | Images of Text | Not applicable | The template has no images of its own. |
| WP-16 | Content Reflow | Not applicable | The template has no rendered layout of its own. |
| WP-17 | Non-text Contrast | Not applicable | The template has no visual UI components of its own. |
| WP-18 | Text Spacing | Not applicable | The template has no rendered text of its own. |
| WP-19 | Content on Hover or Focus | Not applicable | The template has no hover/focus UI of its own. |

## WCAG: Robust

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| WR-1 | Name, Role, Value | Not applicable | The template has no custom UI components of its own. |
| WR-2 | Status Messages | Not applicable | The template has no UI of its own to announce status changes in; API responses are consumed programmatically, not read by assistive technology. |

## WCAG: Understandable

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| WU-1 | Language of Page | Not applicable | The template has no page of its own to set a language attribute on. |
| WU-2 | Language of Parts | Not applicable | The template has no page content of its own. |
| WU-3 | Unusual Words | Not applicable | The template has no user-facing content of its own. |
| WU-4 | Abbreviations | Not applicable | The template has no user-facing content of its own. |
| WU-5 | Changes On Focus | Not applicable | The template has no interactive UI of its own. |
| WU-6 | Changes On Input | Not applicable | The template has no interactive UI of its own. |
| WU-7 | Consistent Navigation | Not applicable | The template has no navigation UI of its own. |
| WU-8 | Consistent Identification | Not applicable | The template has no repeated UI components of its own. |
| WU-9 | Consistent Help | Not applicable | The template has no UI of its own to place help mechanisms in. |
| WU-10 | Error Identification | Not applicable | The template returns RFC 9457 Problem Details as structured API errors, not a visually/audibly identified form error; see [Error responses](../../system-design/08-crosscutting-concepts/02-security-and-authentication/error-responses.md). |
| WU-11 | Error Suggestion | Not applicable | The template returns structured Problem Details error responses for API consumers, not plain-language messages for an end user reading a page. |
| WU-12 | Error Prevention | Not applicable | The template has no submission review UI of its own. |
| WU-13 | Redundant Entry | Not applicable | The template has no multi-step form UI of its own. |
| WU-14 | Accessible Authentication (Minimum) | Alternative | Authentication is delegated to Keycloak; any accessible authentication alternative (e.g. Singpass QR login, one-time codes) is Keycloak's login page responsibility, not this template's. |
