---
title: IM8 Reform Digital Service Standards Control Catalog
catalog:
  id: im8-reform-digital-service-standards-control-catalog
  version: 2026-03-26
  source: https://info.standards.tech.gov.sg/control-catalog/dss/
---

# IM8 Reform Digital Service Standards Control Catalog

> Source: [Singapore Government ICT&SS Policy Reform — Digital Service Standards Control Catalog](https://info.standards.tech.gov.sg/control-catalog/dss/). Extracted 7 September 2026. The source catalog was last updated 26 March 2026; individual control groups may have different update dates.

This single-file representation follows an OSCAL/Trestle-inspired hierarchy: catalog, control group, control, statement, recommendations, rationale, and parameters. It preserves the source wording and parameter placeholders.

## Baseline Design Practices

Controls based on foundational design principles.

### BD-1: Responsive Web Design

**Group:** Baseline Design Practices

#### Control Statement

Adopt Responsive Web Design for web-based digital services.

If specific content is deemed unsuitable for mobile devices, disable mobile device access, and clearly explain why the content or service is disabled and how it can be accessed.

#### Control Recommendations

Implement Responsive Web Design techniques to ensure web-based digital services are optimised for various devices and screen sizes. Test and resolve any layout or functionality issues.

#### Rationale

A significant amount of web traffic comes from mobile devices. Responsive Web Design allows for optimised browsing experience on devices with different screen sizes.

### BD-2: Site Search

**Group:** Baseline Design Practices

#### Control Statement

Provide a site search function for multi-page websites.

#### Control Recommendations

Implement a site search function like SearchSG. Make it easily accessible and discoverable. Regularly review search analytics to optimise the search functionality based on user behaviour and needs.

Not required for

a) Mobile applications

b) Transactional services

c) Websites where search is the service offered (e.g. CDCGoWhere).

#### Rationale

Search is a known and effective alternative to navigation for users who know what they are looking for.

### BD-3: Support multiple languages

**Group:** Baseline Design Practices

#### Control Statement

Provide content in multiple languages to accommodate language preferences.

#### Control Recommendations

Implement language selection mechanisms at key entry points, such as login or homepage, or select language based on device setting. Ensure that end users can easily switch between languages at any time.

#### Rationale

Allowing end users to select their preferred language enhances usability and inclusivity, allowing broader access and understanding.

### BD-4: Clear and Concise Content

**Group:** Baseline Design Practices

#### Control Statement

Write in a clear and concise manner that is easy to read and understand; Choose simple words that most people can understand.

#### Control Recommendations

Conduct user testing to validate that content is clear and accessible to your target audience.

Readability formulas such as Flesch Reading Ease or Flesch-Kincaid grade level provide an indication of the difficulty of text and identify areas for simplification.

#### Rationale

Writing in a simple and concise manner allows more users with different backgrounds and language proficiency to understand your content.

### BD-5: Search Engine Optimisation

**Group:** Baseline Design Practices

#### Control Statement

Implement Search Engine Optimisation (SEO) best practices to improve website search engine rankings and results.

#### Control Recommendations

Fill out all metadata fields and optimise your website's meta tags. Check that information presented on the search engine results page such as page titles and abstracts are informative and relevant.

Not required for digital services that are restricted to a specific audience and accessed directly, and experimental or beta services.

#### Rationale

SEO increases the findability and reach of web content by helping search platforms effectively crawl your content and optimising the content displayed in search listings. Good SEO practices are essential for end users who access your pages via search.

### BD-6: Consistent UI Design

**Group:** Baseline Design Practices

#### Control Statement

Use a design system or style guide to maintain a consistent user interface design throughout the entire service.

#### Control Recommendations

Use a design system such as the Singapore Government Design System (SGDS).

#### Rationale

Consistent design and placement of interface components improves usability by allowing end users to quickly learn interface patterns. Design systems or style guides help with consistency and scalability by consolidating reusable designs and components.

### BD-7: Mandatory and Optional Fields

**Group:** Baseline Design Practices

#### Control Statement

Indicate if input fields are mandatory or optional.

#### Control Recommendations

Use consistent visual indicators, such as asterisks (*) for mandatory fields. Providing an 'optional' label for optional fields is a good alternative if majority of the fields are mandatory. Ensure these indicators are accessible to screen readers.

Not required for login pages asking for unique identifier (username) and password.

#### Rationale

Reduces completion time by allowing end users to skip unnecessary fields and increases completion rates by helping end users understand the effort required.

### BD-8: Log-in Indication

**Group:** Baseline Design Practices

#### Control Statement

Prominently display the name or identifier of the individual associated with the account after login.

#### Control Recommendations

Display the end user's name or identifier in a clear, accessible location, such as the header or near the top of the page.

#### Rationale

Provides a clear indication that the end user is logged into the right account. This is especially important for shared devices.

### BD-9: Contact Channels

**Group:** Baseline Design Practices

#### Control Statement

Provide at least one contact channel for help or assistance.

#### Control Recommendations

Implement contact channels such as phone numbers, email, contact forms, live chat.

TL-4: Official Government Footer requires a link to contact channels within the footer and WU-9: Consistent Help requires consistent placement of help and support mechanisms across pages.

#### Rationale

Allow end users to contact the relevant parties if they require help or assistance. Reassures users by providing a clear method to acquire information or resolve issues.

## Performance and Reliability

Controls to ensure consistent system performance and dependability.

### PR-1: Digital Service Review

**Group:** Performance and Reliability

#### Control Statement

Review digital services every [ insert: param, pr-1_prm_1 ] day(s). Shut down services that are no longer needed or fail to meet their intended objectives.

#### Control Recommendations

Review factors such as relevance, importance and usage rates of the service.Ensure that there is a process to review each digital service such as websites and mobile apps periodically.

Review factors such as relevance, importance and usage rates of the service.

#### Rationale

Services that are not effective incur unnecessary costs for the organisation.

#### Parameters

PR-1 Parameters

| ID | Type | Description |
| --- | --- | --- |
| pr-1_prm_1 | time period (days) (int) | The time period in days within which a digital service review must be conducted. |

### PR-2: Digital Service Registration

**Group:** Performance and Reliability

#### Control Statement

Register and implement WOGAA on all public facing digital services.

#### Control Recommendations

Registration and implementation includes:

- Registration of all public facing digital services
- Implementation of WOGAA tracking code
- Enabling Sentiments

Refer to Setup Guide for details.

Only registration is required for SaaS and COTS products that do not allow customisation and the implementation of the tracking code and sentiments.

#### Rationale

A centralised registry and tracking of digital services across Whole-of-Government facilitates central oversight and benchmarking of digital services. WOGAA implementation enables analytics data and feedback collection that is essential to identifying issues and opportunities for continuous improvement of the digital service.

### PR-3: Digital Service Availability

**Group:** Performance and Reliability

#### Control Statement

Make digital services available to users 24/7.

Clearly communicate the operational hours if a service cannot be available 24/7.

#### Control Recommendations

Do not limit the availability of services to specific hours unless justified by operational or business requirements.

#### Rationale

End users expect to have access to digital channels anytime excluding planned and unplanned downtime.

### PR-4: Notify of Scheduled Downtime

**Group:** Performance and Reliability

#### Control Statement

Provide notice of scheduled downtime at least [ insert: param, pr-4_prm_1 ] day(s) in advance.

#### Control Recommendations

Clearly communicate scheduled downtime using methods like maintenance banners and in-app notifications. Useful information include date, time, duration, and reason for the downtime (if applicable), feature(s) or section(s) of the digital service that will be unavailable (if applicable).

Balance between providing enough lead time, and ensuring that end users can remember when the maintenance will happen.

#### Rationale

Awareness of scheduled downtime minimises disruption to users by allowing them to plan around it.

#### Parameters

PR-4 Parameters

| ID | Type | Description |
| --- | --- | --- |
| pr-4_prm_1 | time period (days) (int) | The time period in days to provide notice of schedule downtime. |

### PR-5: Manage Broken Links

**Group:** Performance and Reliability

#### Control Statement

Establish processes to detect broken links, and address them within [ insert: param, pr-5_prm_1 ] day(s) from detection.

#### Control Recommendations

Use automated tools like WOGAA's broken link scanner to regularly scan for broken links. This applies to both internal and external links.

#### Rationale

Broken links affect content delivery and prevent end users from completing tasks. Digital services with many broken links are perceived as untrustworthy and unreliable.

#### Parameters

PR-5 Parameters

| ID | Type | Description |
| --- | --- | --- |
| pr-5_prm_1 | time period (days) (int) | The time period in days to address broken links. |

### PR-6: Browser Compatibility

**Group:** Performance and Reliability

#### Control Statement

Ensure web-based services are compatible with the latest major versions of at least [ insert: param, pr-6_prm_1 ] widely used web browsers, based on usage statistics.

#### Control Recommendations

Regularly test your service on the latest two major versions of popular browsers such as Chrome, Firefox, Safari, and Edge. Get browser usage data from analytics tools.

Consider using browser compatibility tools like BrowserStack or LambdaTest to automate and expand testing across multiple environments.

#### Rationale

Browser incompatibility can cause layout bugs and other functional issues.

#### Parameters

PR-6 Parameters

| ID | Type | Description |
| --- | --- | --- |
| pr-6_prm_1 | number of browsers (int) | The number of web browsers. |

### PR-7: Optimise Load Times

**Group:** Performance and Reliability

#### Control Statement

Ensure that page load time of web-based services is [ insert: param, pr-7_prm_1 ] second(s) or less.

#### Control Recommendations

Track or regularly test and optimise performance using tools like WOGAA and Google PageSpeed Insights.

#### Rationale

Long page load times frustrate end users and lead to longer transaction times and increased abandonment rate.

#### Parameters

PR-7 Parameters

| ID | Type | Description |
| --- | --- | --- |
| pr-7_prm_1 | time period (seconds) (int) | The time period in seconds for page load times. |

## Transactions and Payments

Controls to enhance and simplify the payments and transactions experience.

### TX-1: Digital-First Approach

**Group:** Transactions and Payments

#### Control Statement

Design digital transactions to be fully digital from start to finish, unless otherwise stated in legal regulations.

#### Control Recommendations

Avoid requiring non-digital interactions such as physical signatures, paper-based approvals, or in-person submissions as part of a digital service.

Implement digital signature solutions such as Singpass, and enable common digital payment methods like credit card and Paynow.

#### Rationale

A transaction that is not fully digital diminishes the convenience of being able to transact anywhere and anytime.

### TX-2: Transaction Prerequisites

**Group:** Transactions and Payments

#### Control Statement

Provide all information required to complete the transaction before the start of the transaction.

#### Control Recommendations

Information to include where applicable:

a) Prerequisites or eligibility criteria

b) Estimated completion time/time range

c) Required documents and accepted file formats

d) Cost and payment modes available

#### Rationale

Knowing the information and documents required allows end users to assess their readiness and ability to complete the transaction before committing to it.

### TX-3: Break Down Long Transactions

**Group:** Transactions and Payments

#### Control Statement

Break long transactions down into logical steps or sections.

#### Control Recommendations

Group related input fields together to reduce cognitive load and streamline data entry.

#### Rationale

Makes long or multi-step tasks feel more manageable.

### TX-4: Progress Indicators

**Group:** Transactions and Payments

#### Control Statement

Provide progress indicators for multi-step transactions.

#### Control Recommendations

Use a clear and intuitive design to visually convey the process and progress.

#### Rationale

Progress indicators can help end users estimate time required for a task, and know their progress within a transaction or flow.

### TX-5: Save Draft Function

**Group:** Transactions and Payments

#### Control Statement

Provide a save draft function for long transactions.

#### Control Recommendations

Ensure the option to save drafts is clearly visible and accessible. Provide clear feedback when a draft has been saved and provide easy access to retrieve and resume the draft.

#### Rationale

Some transactions have long estimated completion time or many fields. An option to save and continue later provides end users with the flexibility of performing the transaction in chunks, and assurance that input can be retrieved if something goes wrong.

### TX-6: Pre-fill Data

**Group:** Transactions and Payments

#### Control Statement

Pre-fill forms with known data where applicable;

Where SingPass/CorpPass is used, provide the option to pre-fill personal or business data with MyInfo and Enterprise Data Hub (EDH).

#### Control Recommendations

Properly assess accuracy and relevance of any pre-filled data before implementation.

#### Rationale

Pre-filled data saves end users time and minimises the risk of errors from manual input.

### TX-7: Payment and Refund

**Group:** Transactions and Payments

#### Control Statement

Provide payment and refund information before payment is made.

#### Control Recommendations

Ensure this information is easily accessible on the payment page and before the start of the transaction.

#### Rationale

Being transparent and upfront about payment related details reassures end users and minimises the unpleasantness of rectifying an error.

### TX-8: Managing Stored Payment Details

**Group:** Transactions and Payments

#### Control Statement

Allow updating and removal of stored payment details in all digital services involving payments.

#### Control Recommendations

Provide clear instructions and confirmation prompts to guide end users through the process to minimise errors.

#### Rationale

Provides end users with control over sensitive payment information.

### TX-9: Success or failure message

**Group:** Transactions and Payments

#### Control Statement

Display a success or failure message at the end of the transaction.

#### Control Recommendations

Use familiar and unambiguous visual cues, such as clear copy, iconography, and colour(e.g. green for success, red for failure) to enhance immediate recognition.

#### Rationale

Clear feedback on the outcome of the transaction reassures end users.

### TX-10: Failed Transaction Details

**Group:** Transactions and Payments

#### Control Statement

Provide the following details for failed transactions:

- a) Clear reasons for failure or rejection (unless limited by policy considerations)
- b) Alternative actions
- c) Channels for appeals or further enquiries

#### Control Recommendations

Ensuring explanations are clear and as detailed as possible within policy constraints.

#### Rationale

Providing specific and actionable information to end users when a transaction fails allows them to quickly understand the reasons, and resolve issues if applicable.

### TX-11: Payment details

**Group:** Transactions and Payments

#### Control Statement

Display payment details once payment has been completed.

#### Control Recommendations

Important payment details include:

i) Acknowledgement code or receipt number (where applicable);

ii) Transaction date;

iii) List of products and/or services transacted (where applicable);

iv) Chosen digital payment mechanism (where applicable);

v) Amount paid

Payment information can be displayed on the success screen and/or sent via other acknowledgement channels. Consider providing the option to download or print the receipt for record-keeping.

#### Rationale

Transactions involving payment are important to end users. Post transaction acknowledgement serves as a secondary check and can potentially reduce the consequences of errors.

### TX-12: Transaction Outcome

**Group:** Transactions and Payments

#### Control Statement

Provide an estimated timeframe for the transaction outcome when it is not immediately available.

#### Control Recommendations

This information can be provided before the start of the transaction, on the submission success screen, and/or in acknowledgement or receipts.

#### Rationale

Keeping users informed about when to expect a decision reduces uncertainty and manages user expectations.

### TX-13: Post-transaction Acknowledgement

**Group:** Transactions and Payments

#### Control Statement

Send and/or allow download of an acknowledgement after a successful transaction.

#### Control Recommendations

Send acknowledgement via channels like SMS, email, push notification, or in-app notifications. Ensure that the acknowledgment includes key details about the submission.

#### Rationale

Allowing the end user to retain a receipt or acknowledgement of the transaction that can be revisited provides reassurance that the submission or transaction has been received.

### TX-14: Transaction Status Updates

**Group:** Transactions and Payments

#### Control Statement

Provide notifications when there are updates to transaction status.

#### Control Recommendations

Notifications can be sent via SMS, email, push notification, or in-app notifications.

#### Rationale

An active approach of notifying end users when they are updates to their transaction status reduces the need to manually check, and allows for timely follow-ups.

### TX-15: Tracking Transaction Status

**Group:** Transactions and Payments

#### Control Statement

Enable online tracking of transaction status.

#### Control Recommendations

Provide clear status labels and descriptions, estimated completion timelines, and any required end user actions.

#### Rationale

Allowing end users to track their transactions online increases transparency and builds trust. The ability to self-service provides a sense of control, reducing anxiety for users, and reducing reliance and load on customer support.

## Trust and Legitimacy

Controls to establish credibility and build user trust in digital services.

### TL-1: Official Government Domain

**Group:** Trust and Legitimacy

#### Control Statement

Use .gov.sg domain for web-based digital services. Education Institutions may use .edu.sg. domain.

#### Control Recommendations

Register .gov.sg and .edu.sg domain names on the Whole of Government Domain Name Server (DNS) portal on the IT Service Management (ITSM) portal.

#### Rationale

Helps end users easily recognise that they are interacting with an authentic government source, reducing the risk of fraud, phishing, or misinformation.

### TL-2: Agency or Initiative Logo

**Group:** Trust and Legitimacy

#### Control Statement

Display the agency/initiative logo on the top left of every page of web-based digital services; Hyperlink the logo to the homepage.

#### Control Recommendations

The logo should render clearly without distortion or pixellation to maintain trust and reinforce brand reputation.

#### Rationale

Provides reassurance about the legitimacy of the service.

### TL-3: Official Government Banner

**Group:** Trust and Legitimacy

#### Control Statement

Display the Official Government Banner on every page of web-based .gov.sg digital services.

#### Control Recommendations

The Official Government banner must be the topmost component of the web service. Use the Singapore Government Design System (SGDS) ‘Official Government Banner’ component.

Not required for SaaS and COTS products that do not allow customisation.

#### Rationale

Helps end users easily recognize that they are interacting with an authentic government service, reducing the risk of fraud, phishing, or misinformation.

### TL-4: Official Government Footer

**Group:** Trust and Legitimacy

#### Control Statement

Adopt the Official Global Footer on every page of .gov.sg web-based services.

#### Control Recommendations

Use the basic footer provided by SGDS. The footer must contain

A) The following footer links in this order

i) Contact / Contact Us

ii) Feedback

iii) FAQ (if any)

iv) Sitemap (if any)

v) Report Vulnerability

vi) Privacy Statement

vii) Terms of Use

B) Copyright Statement in the format

'© [current year in YYYY], Government of Singapore’

Digital services owned by statutory boards may use the agency’s name instead of 'Government of Singapore'. Not required for pages that are part of transactional services.

#### Rationale

Provides consistency across government digital services and ensures that end users have access to consistently placed important utility links.

### TL-5: Mobile App Ownership and Distribution

**Group:** Trust and Legitimacy

#### Control Statement

Distribute government mobile applications through official app stores and retain IP rights.

#### Control Recommendations

Retain full ownership of all intellectual property rights, including copyright. Avoid releasing mobile applications under licenses that permit free distribution. Ensure compliance with platform guidelines for official government endorsement.

Distribute apps only through the following platforms:

- Apple App Store
- Google Play Store
- Huawei AppGallery

#### Rationale

Minimise the risk of end users downloading unauthorised versions of Government apps. Owning the IP rights provides agencies with a legal basis to request third-party app stores or websites to remove unauthorised versions of Government mobile applications.

### TL-6: Application Store Listings

**Group:** Trust and Legitimacy

#### Control Statement

Ensure that the agency name is used as the application store developer account name when publishing mobile applications.

#### Control Recommendations

Use the full agency name

#### Rationale

Having clear and properly named developer accounts reassures end users of the legitimacy of the mobile app.

## Understand Users

Controls to ensure services are informed by real user needs and behaviors.

### UU-1: Understand user needs

**Group:** Understand Users

#### Control Statement

Understand and document Public Users' needs and context when creating a new digital service, redesigning existing services or introducing major features.

#### Control Recommendations

Take steps to understand end user context, needs, and problems before deciding on a solution.

This includes activities such as:

a) conducting user research (e.g. surveys, interviews, ethnographic studies)

b) analysing end user feedback

c) using web analytics and other relevant data.

#### Rationale

Understanding end user context, goals, behaviour and mindsets allows you to design solutions that truly meet their needs. Better-aligned digital services effective meet user needs from the start, improving user satisfaction, and reducing costly rework.

### UU-2: Test with users

**Group:** Understand Users

#### Control Statement

Conduct and document usability testing with representative Public Users when creating a new digital service, redesigning existing services or introducing major features. Remediate or risk accept findings within [ insert: param, uu-2_prm_1 ] day(s).

#### Control Recommendations

Conduct usability testing at key milestones such as prior to public release. Identify key end user tasks based on real scenarios and create prototypes to test them.

Document findings in a structured report, prioritising issues based on severity.

While structured usability testing is recommended, lightweight methods—such as guerrilla testing with a small number of users—are acceptable if they still yield actionable insights.

Usability testing is not user acceptance testing.

#### Rationale

Testing with end users allows early identification of usability issues at various points in the design and development process. This ensures the product is intuitive and usable, and reduces the risk of costly fixes later.

#### Parameters

UU-2 Parameters

| ID | Type | Description |
| --- | --- | --- |
| uu-2_prm_1 | time period (days) (int) | The time period in days to remediate or risk accept findings. |

## WCAG : Operable

Controls to make interface elements easy to operate for all users.

### WO-1: Keyboard equivalent

**Group:** WCAG - Operable

#### Control Statement

Ensure any action done using a pointer device can also be done using a keyboard.

#### Control Recommendations

Provide keyboard operation for all the functionality of the page unless the function requires path-dependent input such as handwriting.

Avoid requiring specific timings for individual keystrokes such as requirements to repeat or execute multiple keystrokes within a short period of time.

#### Rationale

Ensures that there is no loss of functionality for users who rely on keyboards such as those with motor impairments.

### WO-2: No Keyboard Trap

**Group:** WCAG - Operable

#### Control Statement

Ensure that keyboard users can move keyboard focus away or out from a component or section of the digital service.

#### Control Recommendations

Test forms, widgets, and custom elements with keyboard-only navigation to confirm end users can exit all components using standard keys (like Tab and Shift+Tab).

If the key functionality of the component restricts the focus to a subsection of the content, communicate clearly to users how to leave that state and "untrap" the focus.

#### Rationale

A keyboard trap happens when keyboard focus is moved to a component or subsection of the digital service without a way of the end user navigating back out using a keyboard interface. End users who who rely on exclusive keyboard interface usage such as people with vision and physical disabilities get stuck and are unable to proceed to other parts of the service or complete a critical transaction.

### WO-3: Character Key Shortcuts

**Group:** WCAG - Operable

#### Control Statement

Provide a mechanism to disable or remap shortcuts that are activated using only one character key press.

#### Control Recommendations

If character key shortcuts are used, provide end users with the ability to disable or remap them. Implement keyboard settings within the interface for customisation, ensuring shortcuts do not interfere with common keyboard interactions.

#### Rationale

Reduces the risk of accidental activation of keyboard shortcuts for keyboard users and speech input users, whose dictation is interpreted as strings of letters.

### WO-4: Adjustable Timings

**Group:** WCAG - Operable

#### Control Statement

Allow time limits to be turned off, adjusted, or extended.

#### Control Recommendations

Avoid time-dependent functions unless absolutely necessary. Ensure timers are non-disruptive and adjustable or extendable.

Not required when the time limit is:

a) Mandatory from a legal point of view

b) Required as part of a real-time event where extensions would invalidate the activity

c) Longer than 20 hours

#### Rationale

Persons with disabilities may need more time to complete complex tasks such as filling out lengthy grant forms. Designing functions that are not time-dependent or allow for adjustable timings will help everyone, especially people with disabilities, succeed at completing these tasks.

### WO-5: Pause, Stop, Hide

**Group:** WCAG - Operable

#### Control Statement

Provide a function to pause, stop, hide or control the frequency of moving, blinking, scrolling or auto-updating content that occur in parallel with other content.

#### Control Recommendations

Content changes refer to, but are not exclusive to, auto-updating information, animations containing essential information or similar components.

Allow users to pause, stop, or hide content that automatically moves, blinks, scrolls or auto-updates for more than 5 seconds.

#### Rationale

Content that moves or auto-updates can be a barrier to anyone who has trouble reading stationary text quickly, tracking moving objects or noticing page changes easily. It can also cause problems for screen readers.

### WO-6: Reduce Flash Triggers

**Group:** WCAG - Operable

#### Control Statement

Avoid content that flashes more than three times per second or provide a warning and the option to skip such content.

#### Control Recommendations

Provide a clearly displayed warning message such as "This video may potentially trigger seizures for people with photosensitive epilepsy. Viewer discretion is advised."

Excludes flashes contained in a small area, are low contrast or include a small amount of the colour red.

#### Rationale

Flashing content can trigger seizures in end users with photosensitive epilepsy leading to serious health risks for these users.

### WO-7: Bypass Repeating Content

**Group:** WCAG - Operable

#### Control Statement

Provide a means of skipping repetitive blocks of content that appear across multiple pages.

#### Control Recommendations

Implement visible and keyboard accessible link(s) for end users to skip repeated content blocks and/or group blocks of repeated material in a way that can be skipped through the use of ARIA landmarks or heading markup.

#### Rationale

End users who navigate sequentially with keyboards or screen readers often encounter repeated content such as headers and navigation links across multiple pages. Providing a mechanism to bypass these repetitive sections enables users to access the main content more efficiently.

### WO-8: Page Title And Purpose

**Group:** WCAG - Operable

#### Control Statement

Provide a page title describing the purpose of every web page or distinct Single-Page Application view served from the same Uniform Resource Identifier (URI).

#### Control Recommendations

Provide a clear and succinct page title that describes the purpose of the page. For example, "Budget 2024 Support Schemes Calculator" is a clear title stating the page contains a calculator for Support Schemes from Budget 2024.

#### Rationale

Clear, descriptive titles provide quick context, enabling all end users (including users of assistive technologies) to quickly understand where they are without having to scan page content. They also facilitate efficient navigation in site maps and search results, allowing rapid identification of relevant information.

### WO-9: Sequential Focus Order

**Group:** WCAG - Operable

#### Control Statement

Ensure focusable components receive focus in an order that preserves meaning and operability when the navigation sequences affect meaning or operation.

#### Control Recommendations

When a user triggers a focusable element like a modal dialog via an element (button or link), the keyboard focus should be set to within the modal and limited to the elements of modal until dismissed.

When the modal is dismissed, the keyboard focus should logically return to the original trigger element to minimise confusion for the user.

#### Rationale

Allows keyboard users to navigate in a sequence that reflects the content's logical structure, preventing disorientation from unexpected tabbing focus. This approach accommodates various valid navigation patterns while preserving content coherence. Implementing this improves accessibility, often results in cleaner markup, and can enhance SEO, benefiting all users.

### WO-10: Link Text And Purpose

**Group:** WCAG - Operable

#### Control Statement

Ensure that link text identifies the purpose of the link and can be interpreted easily on its own.

#### Control Recommendations

Avoid using vague link text such as "click here" or "read more".

#### Rationale

Descriptive and meaningful link text helps users understand the content and purpose of each link without needing to read the surrounding text. This is especially important for screen reader users who often navigate web pages by jumping from one link to another. It also improves the SEO value of a page and helps all users find the information they need more efficiently.

### WO-11: Multiple Ways

**Group:** WCAG - Operable

#### Control Statement

Provide at least two ways to reach the same content.

#### Control Recommendations

Focus on information architecture and implement multiple ways to reach content such as clear and logical navigation structures, search function and shortcuts.

#### Rationale

Providing multiple ways to access content allows end users to locate content in ways that best meet their needs. Users may find one navigation path easier or more comprehensible than another, improving both the user experience of the service and the discoverability of content.

### WO-12: Headings and Labels

**Group:** WCAG - Operable

#### Control Statement

Provide descriptive headers and labels that identify the context of surrounding text and the component's purpose.

#### Control Recommendations

Do not disable platform or user-agent default visual focus indicators. If default focus indicators are not available or do not meet other accessible guidelines like colour contrast, modify the background colour or border of the element with focus.

Ensure that 'floating' components like widgets and sticky headers do not cover elements receiving focus.

#### Rationale

Allows end users to quickly identify which element or control has keyboard focus. This is especially important for users relying exclusively on keyboards to navigate.

### WO-13: Focus Visible and Not Obscured

**Group:** WCAG - Operable

#### Control Statement

Ensure components receiving focus have distinguishable indicators and are not obscured by other elements.

#### Control Recommendations

Do not disable platform or user-agent default visual focus indicators. If default focus indicators are not available or do not meet other accessible guidelines like colour contrast, modify the background colour or border of the element with focus.

Ensure that 'floating' components like widgets and sticky headers do not cover elements receiving focus.

#### Rationale

Allows end users to quickly identify which element or control has keyboard focus. This is especially important for users relying exclusively on keyboards to navigate.

### WO-14: Simple Pointer Alternatives

**Group:** WCAG - Operable

#### Control Statement

Provide single-point alternatives to multipoint or path-based interactions or gestures.

#### Control Recommendations

If multipoint or path-based gestures such as sliders and drag-and-drop functions are used, provide alternative options to perform the same interaction through simple single-point alternatives.

#### Rationale

Interactions that involve complicated gestures or precise movements such as dragging or pinching can be challenging or impossible for end users with mobility disabilities, elderly users, or those using adaptive input devices.

### WO-15: Pointer Cancellation

**Group:** WCAG - Operable

#### Control Statement

Provide a predictable and consistent way to cancel or undo pointer interactions.

#### Control Recommendations

Implement clear and accessible methods to cancel critical actions such as an "undo" function. Confirmation dialogs can be used as a secondary confirmation for critical actions.

Unless absolutely necessary, only execute a function when the click or touch is released (up-event) instead of when the click or touch is made (down-event). This gives end users the opportunity to cancel the activation after by moving their pointer or finger away from the target before releasing.

#### Rationale

People with various disabilities can accidentally initiate touch or mouse events. Allowing such end users to easily recover from such unintended pointer actions minimises unintended consequences such as accidental submissions.

### WO-16: Label In Name

**Group:** WCAG - Operable

#### Control Statement

Ensure that visible text labels of user interface components match or are contained within their programmatic or accessible names.

#### Control Recommendations

Ensure that accessibility labels meaningfully describe the equivalent visual UI element by containing both the visible text label and other contextual descriptors that help assistive technology users fully understand the purpose and interaction.

#### Rationale

Matching visible text labels with the programmatic component supports the use of assistive technologies such as allowing speech-input users to navigate by speaking the visible text labels of components.

### WO-17: Motion Actuation

**Group:** WCAG - Operable

#### Control Statement

Provide an option to disable motion control for motion-operated features, and provide alternative means to operate such features.

#### Control Recommendations

When implementing motion-operated features, provide a way to disable motion actuation and offer alternative means to operate these features through user interface components.

Not required when motion is essential to the function, such as in pedometers that require device motion to count steps.

#### Rationale

End users with motor impairments may have difficulty holding or moving a device steadily, which can prevent them from using motion-operated features and may cause accidental triggering of functions.

### WO-18: Minimum Pointer Target Size

**Group:** WCAG - Operable

#### Control Statement

Ensure that interactive areas for pointer input are at least 24 by 24 CSS pixels.

#### Control Recommendations

Use min-height and min-width to ensure sufficient target spacing.

If targets have to be smaller than 24-pixels, they must be spaced so that a 24-pixel area around each target does not overlap adjacent targets.

Follow platform guidelines and consider larger target sizes for mobile applications and mobile web browsing where touch is the input mechanism.

Not required if the size of the interactive area is constrained by the line-height of non-interactive text such as text links within a body of text.

#### Rationale

End users with physical and fine motor disabilities require more effort to accurately activate small targets or targets that are close to each other. Ensuring pointer inputs are large enough with sufficient spacing between targets reduces the likelihood of errors from accidentally activating the wrong control.

## WCAG : Perceivable

Controls that ensure users can perceive the content in various forms.

### WP-1: Text Alternatives for Non-Text content

**Group:** WCAG - Perceivable

#### Control Statement

Provide text alternatives for essential non-text content.

#### Control Recommendations

Provide accurate, concise alt text or a relevant textual description for non-text content (images, charts, media).

Implement purely decorative non-text content in a way that can be ignored by assistive technology.

#### Rationale

Ensures that equivalent information is conveyed to all end users by allowing assistive technologies to read and communicate essential non-text content.

### WP-2: Captions for Prerecorded Media

**Group:** WCAG - Perceivable

#### Control Statement

Provide captions for prerecorded video or audio content.

#### Control Recommendations

Use transcribing or captioning tools to generate captions. Ensure captions are aligned with audio and are readable.

Not required when the content is a media alternative for text and is clearly labeled as such.

#### Rationale

Ensures that individuals with hearing impairments can access and understand the material. Captions also help people engage better with content and allow people to access the content without audio.

### WP-3: Text or Audio Alternatives for Prerecorded Media

**Group:** WCAG - Perceivable

#### Control Statement

Provide alternatives for all prerecorded audio and video media.

#### Control Recommendations

Provide text transcripts for audio-only content. Provide text alternatives or audio descriptions for video-only content and synchronised media (audio and video).

Not required when the content is a media alternative for text and is clearly labeled as such.

#### Rationale

Ensures information conveyed by prerecorded audio and video content is available to all end users.

### WP-4: Live Captions

**Group:** WCAG - Perceivable

#### Control Statement

Provide captions for live audio and video content.

#### Control Recommendations

Use automated or live captioning services for content like livestream and webinars. Ensure that the service or tool is sufficiently accurate.

#### Rationale

Live captions make live audio and video content accessible to end users who are deaf or hard of hearing.

### WP-5: Audio Description for Prerecorded Video Content

**Group:** WCAG - Perceivable

#### Control Statement

Provided an audio description for all prerecorded video content.

#### Control Recommendations

Provide user-selectable audio tracks focusing on critical visual elements necessary for understanding.

This is an extension of wp-3 which recommends but does not prescribe audio descriptions.

#### Rationale

Ensures that individuals who have visual impairments can better experience video content. This involves both information conveyed through the audio track and also descriptions of visual information such as actions, signs and facial expressions.

### WP-6: Presentation of Info and Relationships

**Group:** WCAG - Perceivable

#### Control Statement

Present the structure, order and relationships of information on a page in a way that can be programmatically determined by assistive technology.

#### Control Recommendations

Apply techniques such as correct use of semantic HTML elements (e.g., headings, lists, tables), text descriptions and where necessary, ARIA markups. Do not overuse ARIA as it creates clutter and overwhelm end users.

#### Rationale

Ensures that the structure and organisation of content are clear to all end users, including those who use assistive technologies.

### WP-7: Meaningful Content Order

**Group:** WCAG - Perceivable

#### Control Statement

Ensure that the logical reading order of content can be programmatically determined by software.

#### Control Recommendations

Ensure code is structured to preserve the logical reading order of content. Validate with screen reader testing to confirm that end users can navigate content in a logical sequence.

#### Rationale

Allows assistive technology to present content in the intended reading order needed to understand the meaning.

### WP-8: Describing Displayed Controls

**Group:** WCAG - Perceivable

#### Control Statement

Do not rely only on sensory characteristics such shape, colour, size, visual location, orientation, or sound when describing controls.

#### Control Recommendations

Describe controls by name.

Avoid using expressions like "click the blue button" "or" "Select an option after the beep" when describing or referring to controls. Instead use clear, obvious and meaningful instructions like "Click the 'next' button to continue with the survey", "When you have finished filling in your application form, click 'Submit' to complete your submission"

#### Rationale

Reliance of specific sensory characteristics may make descriptions or instructions inaccessible to some users with specific disabilities. E.g. Colour and shape may not perceivable by blind users.

### WP-9: Display Orientation

**Group:** WCAG - Perceivable

#### Control Statement

Do not restrict content to a single display orientation.

#### Control Recommendations

Avoid locking orientation to a particular view. Ensure content can adapt to various orientations such as landscape and portrait without layout or functionality issues.

Not required if a specific display orientation is necessary to provide the content in an accurate and functional manner.

#### Rationale

To ensure that content displays in the orientation (portrait or landscape) preferred by the end user. Users with dexterity impairments such as those in wheelchairs may have devices mounted in a particular orientation.

### WP-10: Identify Input Purpose

**Group:** WCAG - Perceivable

#### Control Statement

Use labels and code to clearly specify the purpose and format of common inputs.

#### Control Recommendations

Clearly distinguish similar fields such as billing address and shipping address. Specify the exact format of input fields that may have multiple valid interpretations such as dates.

#### Rationale

Ensures that both regular end users and users of assistive technology can clearly discern the purpose of an input function.

### WP-11: Use of Color

**Group:** WCAG - Perceivable

#### Control Statement

Avoid using colour as the only visual means of conveying information, indicating an action, prompting a response, or distinguishing a visual element.

#### Control Recommendations

Use a mix of shape (icons, symbols), colour and text for essential content such as error messages.

#### Rationale

Using multiple modes in addition to colour, ensures that crucial information and state changes are well communicated to end users, especially those with colour vision deficiencies.

### WP-12: Audio Control

**Group:** WCAG - Perceivable

#### Control Statement

Providing options to pause, stop or adjust the volume of audio that automatically plays for more than 3 seconds.

#### Control Recommendations

Only play sounds on user request or provide the user with functions to pause, stop or adjust the volume near the beginning of the digital service so it is easily and quickly discovered.

#### Rationale

To prevent unexpected or disruptive audio from affecting users' experience, particularly those with cognitive or hearing impairments. Background audio also interferes with narration by screen reading software.

### WP-13: Minimum Contrast

**Group:** WCAG - Perceivable

#### Control Statement

Ensure a minimum contrast ratio of 4.5:1 between text and its background.

Large text (at least 18 point or 14 point bold) can have a reduced contrast ratio of at least 3:1.

#### Control Recommendations

Use tools like WebAIM's Contrast Checker or Oobee to verify that text and images of text meet the minimum contrast ratio.

Not required for

a) Text that is decorative, conveys no meaningful information

b) Part of a component in an inactive state

#### Rationale

Adequate contrast between text and background is essential for good readability and usability, especially for end users with low vision or colour vision deficiencies.

### WP-14: Text Scaling

**Group:** WCAG - Perceivable

#### Control Statement

Ensure text can be scaled up to 200% without loss of content or functionality.

#### Control Recommendations

Use relative units like REM. Ensure at least one one browser or platform text scaling mechanism can be used. This includes zoom (of the entire page's content), magnification, and text-only resizing. Test to confirm that no content is clipped, truncated, or obscured after resizing.

This excludes captions and images of text.

#### Rationale

Resizable text makes digital content accessible to a wide range of end users with different visual capabilities, particularly those without access to screen magnification tools.

### WP-15: Images of Text

**Group:** WCAG - Perceivable

#### Control Statement

Use text instead of images of text.

#### Control Recommendations

Avoid using images of text except when essential, such as for logos. Where text in images are necessary, ensure that alternative text or captions provide the same information.

#### Rationale

Text in images are not accessible, especially for end users with visual impairments or reading disabilities. Users cannot adjust the size or font for better readability and the text is not readable by common screen reader technology.

### WP-16: Content Reflow

**Group:** WCAG - Perceivable

#### Control Statement

Enable lines of text and content to reflow across different viewports.

#### Control Recommendations

Use responsive design frameworks like CSS Flexbox or Grid and test on a variety of devices to confirm that content remains legible and usable without excessive scrolling even when text is resized.

Not required for content that requires two-dimensional layout for understanding or functionality, such as maps, complex data tables or data visualisations.

#### Rationale

Enabling reflowing of content allows users who rely on large fonts, including those using devices with smaller screens to be able to view and navigate digital content without scrolling horizontally even when text size is increased.

### WP-17: Non-text Contrast

**Group:** WCAG - Perceivable

#### Control Statement

Ensure a minimum contrast ratio of 3:1 between colours of user interface components and graphical objects that convey important information, and their respective backgrounds.

#### Control Recommendations

Use tools like WebAIM's Contrast Checker to test contrast. Focus on elements critical for interaction, such as buttons and form inputs.

#### Rationale

Sufficient contrast helps end users better perceive and identify non-text user interface components and key graphical objects, improving usability.

### WP-18: Text Spacing

**Group:** WCAG - Perceivable

#### Control Statement

Ensure that text implemented using markup languages can be adjusted to the following text style properties with no loss of content or functionality:

- a) Line height to 1.5 times the font size
- b) Paragraph spacing to 2 times the font size
- c) Letter spacing to 0.12 times the font size
- d) Word spacing to 0.16 times the font size

#### Control Recommendations

Allow for text spacing override by assistive technology and test with tools that simulate the spacing configurations specified.

Text content does not need to use these text spacing values as defaults, and there is no need to implement controls for users to adjust text properties within the digital service.

#### Rationale

Ensures that end users, in particular those with low vision or dyslexia are: a) Not restricted from overriding default text spacing through the use of assistive technology, stylesheets or extensions. b) Content or functionality is not lost and the content remains legible and functional.

### WP-19: Content on Hover or Focus

**Group:** WCAG - Perceivable

#### Control Statement

Ensure content appearing on hover or focus

- a) Appears long enough to be read and interacted with
- b) Does not disappear unexpectedly
- c) Can be easily dismissed using a pointer device or keyboard

#### Control Recommendations

Design custom tooltips, sub-menus and other non-modal popups which display on hover and focus to be perceivable and dismissible without disrupting the overall user experience.

#### Rationale

Elements that appear on hover or focus can be difficult to manage. End users with limited motor control may have trouble keeping their pointer steady, and keyboard-only users need a reliable way to dismiss such content.

## WCAG : Robust

Controls to ensure content remains accessible across various technologies and assistive tools.

### WR-1: Name, Role, Value

**Group:** WCAG - Robust

#### Control Statement

Ensure that essential information of custom components and controls can be identified and read by assistive technologies.

#### Control Recommendations

Essential information including name, roles, properties, values, states, and state changes are provided using techniques like ARIA labels.

Standard HTML controls should already meet this criterion when used according to specification. (example: text input field)

#### Rationale

Enables end users of assistive technology to properly understand and interact with custom components.

### WR-2: Status Messages

**Group:** WCAG - Robust

#### Control Statement

Ensure that assistive technology can identify and announce system status changes that don't receive focus.

#### Control Recommendations

Use ARIA roles and properties to inform users of status changes, such as when an incorrect text in an input is entered and a status message appears above.

#### Rationale

Allows screen readers to identify and announce changes to content that may otherwise be missed by users of assistive technologies. This benefits users with visual impairments who unlike sighted users may not notice changes outside of their area of focus.

## WCAG : Understandable

Controls to ensure content and user interfaces are clear and comprehensible.

### WU-1: Language of Page

**Group:** WCAG - Understandable

#### Control Statement

Ensure that the primary language on a page is programmatically determined.

#### Control Recommendations

For websites, specify the primary language of each webpage using the lang attribute in the HTML tag.

For mobile apps, set the language of the app content using the platform's appropriate language or accessibility attribute such the accessibilityLanguage attribute for iOS.

For PDFs, use the /Lang entry to specify the language of the document or section.

#### Rationale

Specifying the language ensures that assistive technology can present content to end users using the correct language settings. This includes pronunciation rules and rendering of display characters and scripts.

### WU-2: Language of Parts

**Group:** WCAG - Understandable

#### Control Statement

Ensure the language of words or sections that differ from the page's primary language is programmatically determined.

#### Control Recommendations

Specify the language of text that differs from the primary language of the page using the lang attribute in HTML elements or the platform’s equivalent language tagging feature.

#### Rationale

Allows assistive technology to identify language changes and properly present content in the correct intonation and proper pronunciation to end users.

### WU-3: Unusual Words

**Group:** WCAG - Understandable

#### Control Statement

Provide definitions for technical jargon and unusual terms.

#### Control Recommendations

Avoid jargon and unusual terms where possible. If absolutely necessary, provide contextual help such as inline explanations, tooltips, or glossaries.

#### Rationale

Slang, jargon, metaphors, and figures of speech may not be understood by some groups of end users. Identifying and providing relevance guidance make such content more accessible to everyone.

### WU-4: Abbreviations

**Group:** WCAG - Understandable

#### Control Statement

Provide the expanded form of abbreviations.

#### Control Recommendations

Provide expanded forms of abbreviations the first time they appear on a page or flow, or provide contextual help such as inline explanations, tooltips, or glossaries.

Not required for common abbreviations such as PDF and SMS where presenting the expanded form is unnecessary for understanding of meaning, and may negatively affect the reading experience.

#### Rationale

Abbreviations or acronyms may not be understood by some end users, especially when used for the first time in a page or section.

### WU-5: Changes On Focus

**Group:** WCAG - Understandable

#### Control Statement

Avoid changing the context when a user interface component receives focus

#### Control Recommendations

If a component triggers an event when it receives focus, the context should not be changed.

Avoid actions such as form submissions, opening a new window and changing focus to another component when a component receives focus.

#### Rationale

Unexpected changes of context triggered by focus change can be disorienting to users of assistive technology. Ensuring that context changes are predictable helps end users feel secure and in control while navigating a site.

### WU-6: Changes On Input

**Group:** WCAG - Understandable

#### Control Statement

Provide advance warning when input causes a change of context.

#### Control Recommendations

Clicking on links or buttons are not considered inputs or changing the setting of that control in the context of this control.

Explicitly inform end users if provision of input like checking a checkbox or entering text into a text field results in a change in context. Changes in context include responses like navigation to a new page or section, submission, or opening a new window.

Alternatively, provide controls such as buttons that allow end users to intentionally trigger actions like confirming a selection or submission.

#### Rationale

Unexpected changes of context triggered by input can be disorienting to users of assistive technology. Ensuring that context changes are predictable helps users feel secure and in control while navigating a site.

### WU-7: Consistent Navigation

**Group:** WCAG - Understandable

#### Control Statement

Implement a primary or main navigation panel that

- Is placed at or near the top of the website
- Is not hidden or collapsed within an icon on desktop for websites
- Presents navigation links in the same relative order on each page

#### Control Recommendations

Test the top navigation component with users to ensure it is usable and accessible.

#### Rationale

Unexpected changes of context triggered by focus change can be disorienting to end users with sensory or cognitive impairment, or users of assistive technology.

### WU-8: Consistent Identification

**Group:** WCAG - Understandable

#### Control Statement

Identify and label repeated or related components consistently within the digital service.

#### Control Recommendations

Ensure that text used to identify a component such as label, name, or text alternatives are identical for each user interface component with the same function within the digital service.

#### Rationale

Allows end users to quickly learn and understand navigation, design and interaction patterns within the digital service. This reduces cognitive load and increase efficiency of task completion.

### WU-9: Consistent Help

**Group:** WCAG - Understandable

#### Control Statement

Place help and support mechanisms consistently across multiple pages.

#### Control Recommendations

Implementing the tl-4: Official Government Footer ensures consistent placement of contact and FAQ across multiple pages.

Ensure that other help and support mechanisms such as messaging systems or chatbots are also consistently placed across multiple pages.

#### Rationale

Help and assistance are key aspects of a digital service. Consistent placement makes it easy for end users, especially those who may struggle with complex or unfamiliar interfaces to find help and support mechanisms.

### WU-10: Error Identification

**Group:** WCAG - Understandable

#### Control Statement

Indicate input errors by:

- a) Visually and audibly identifying the component or section that generated the error
- b) Providing a text description of the error

#### Control Recommendations

Clearly associate error messages with the source component or section. Ensure the error is easily identifiable and noticeable visually and for users of screen readers by using techniques such as ARIA attributes and requirements in wp-11.

#### Rationale

Helping end users easily and quickly identify, locate and understand errors minimises the frustration of encountering and resolving errors.

### WU-11: Error Suggestion

**Group:** WCAG - Understandable

#### Control Statement

Ensure that error messages:

- a) Explain what went wrong in non-technical language.
- b) Explain how to correct the issue or provide alternative actions (If suggestions for correction are known)

#### Control Recommendations

Write error messages in plain language that is understood by end users of different backgrounds, and helps the user understand or resolve the issue. This applies to all error messages including inline form errors and system error pages such as 404, 500 errors.

#### Rationale

Error messages that are not clear or do not provide useful and actionable information prevent end users from completing their tasks and lead to frustration.

### WU-12: Error Prevention

**Group:** WCAG - Understandable

#### Control Statement

Prevent errors by:

- a) Checking user-entered data for errors and/or providing a mechanism to review, confirm, and correct information before final submission
- b) Allow submissions to be reversed (if feasible)

#### Control Recommendations

Implement proper error validation.

Provide a summary page or section for users to review and correct their inputs/information before submission. This is essential for multi-page forms which are difficult to review, and transactions with significant consequences such as those involving payments or having legal implications.

#### Rationale

Providing ways for end users to confirm, correct, or reverse important submissions reduces the chance of errors.

### WU-13: Redundant Entry

**Group:** WCAG - Understandable

#### Control Statement

Avoid requiring re-entry of information already provided within the same process.

#### Control Recommendations

Either auto-populate or allow the end user to select information that was already provided.

This is not required if the information is required to ensure the security of the content (E.g. Confirm password) or if the previously entered information is no longer valid.

#### Rationale

Repetitive data entry requires unnecessary effort especially for end users with cognitive or motor impairments who may struggle with entering information.

### WU-14: Accessible Authentication (Minimum)

**Group:** WCAG - Understandable

#### Control Statement

Provide accessible authentication alternatives that do not require recalling, solving, or transcribing information.

#### Control Recommendations

Provide at least 1 non-password authentication such as Singpass QR login or text/email based one-time codes.

#### Rationale

End users with cognitive impairments have difficulties recalling or performing cognitively demanding actions.
