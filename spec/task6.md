# Production Readiness Task Specification - Spring Boot + Twilio PSTN + Firebase

## Context

This is a production-ready learning project built with:

* Spring Boot
* Twilio Programmable Voice (PSTN Calling)
* Firebase Firestore
* Dynamic TwiML (XML) generation
* Mobile browser support

The calling functionality was working correctly previously. After implementing dynamic TwiML (XML) generation, outgoing/incoming calling stopped working.

The issue occurs on desktop and mobile browsers.

Your task is to perform a complete investigation and fix the implementation.

---

# Objectives

Perform a complete end-to-end review of the calling flow and make the project production-ready.

Do **not** create or update any test cases.

---

# Required Tasks

## 1. Understand the Project

Before making any changes, inspect the project completely.

Read and understand at minimum:

* pom.xml
* application.properties
* application-*.properties
* README.md (if available)
* Twilio configuration classes
* Voice controllers
* TwiML generation logic
* Firebase configuration
* Firestore repositories
* Security configuration
* REST endpoints related to calling
* Frontend call integration (if present)

Understand how the complete call flow is expected to work.

---

## 2. Investigate Calling Flow End-to-End

Trace the complete calling lifecycle.

Verify:

* Browser initiates call
* Backend receives request
* Authentication
* Firestore data lookup
* Twilio REST API request
* TwiML generation
* XML response
* Voice webhook callbacks
* Status callback
* PSTN dialing
* Call completion
* Error handling

Document where the flow breaks.

---

## 3. Investigate Dynamic TwiML

The project previously used working TwiML.

After introducing dynamic XML generation, calling stopped working.

Investigate:

* XML generation
* XML encoding
* Response headers
* Content-Type
* Character escaping
* Invalid XML
* Missing VoiceResponse elements
* Dial element
* Number element
* CallerId
* StatusCallback
* Answer URL
* Action URL
* HTTP method
* Public webhook accessibility

Compare the generated XML with Twilio's current documentation.

Correct every issue.

---

## 4. Verify Twilio Integration

Review every Twilio-related implementation.

Check:

* Account SID
* Auth Token usage
* API Keys
* Twilio SDK version
* Twilio Voice API usage
* VoiceResponse builder
* TwiML generation
* Call creation
* Webhook endpoints
* Signature validation (if implemented)
* Callback URLs
* HTTPS requirements

Upgrade any deprecated implementation if necessary.

Follow the latest Twilio documentation.

---

## 5. Review Mobile Browser Calling

Calling also fails on mobile browsers.

Investigate:

* Mobile browser differences
* CORS
* HTTPS
* Microphone permissions
* WebRTC requirements (if applicable)
* Redirect issues
* XML response
* Browser compatibility

Fix any mobile-specific issue.

---

## 6. Review Backend Configuration

Inspect:

* Environment variables
* application.properties
* Bean configuration
* Dependency versions
* Spring Boot compatibility
* Firebase initialization
* Firestore configuration

Correct any configuration issues.

---

## 7. Logging

Improve logging around calling.

Ensure logs exist for:

* Call initiation
* Generated TwiML
* Twilio REST requests
* Callback requests
* Status callbacks
* Errors
* Exceptions

Avoid exposing secrets in logs.

---

## 8. Error Handling

Improve production-grade error handling.

Verify:

* Invalid numbers
* Missing users
* Firestore failures
* Twilio API failures
* Invalid XML
* Timeout handling
* Callback failures

Return meaningful responses.

---

## 9. Dependency Review

Review:

* pom.xml
* Twilio SDK version
* Spring Boot version
* Firebase dependencies

Update only if necessary.

Avoid unnecessary dependency upgrades.

---

## 10. Security Review

Ensure:

* Secrets are not hardcoded
* Credentials come from configuration
* Sensitive logs are removed
* Proper validation exists
* Webhook endpoints are secure

---

# Documentation

Create the following documentation.

## File 1

`docs/TWILIO_CONFIGURATION.md`

Include:

* Required Twilio Console configuration
* Phone Number configuration
* Voice webhook URL
* Status callback URL
* HTTP methods
* TwiML App configuration (if required)
* Caller ID requirements
* Verified numbers (trial account)
* API credentials
* Environment variables
* Required HTTPS setup
* Common Twilio mistakes
* Production checklist

---

## File 2

`docs/CALL_FLOW.md`

Document the complete call flow.

Include:

1. Browser request
2. Backend processing
3. Firestore lookup
4. Twilio API request
5. TwiML generation
6. XML response
7. PSTN dialing
8. Callback handling
9. Call completion

Include diagrams using Markdown where helpful.

---

# Constraints

* Do NOT create test cases.
* Do NOT update existing tests.
* Preserve the existing architecture whenever possible.
* Minimize breaking changes.
* Follow Spring Boot best practices.
* Follow Twilio best practices.
* Follow Firebase best practices.
* Keep code production-ready.
* Validate all changes against the latest Twilio documentation before implementation.
* Ensure compatibility for both desktop and mobile browsers.

---

# Deliverables

1. Fully fixed calling functionality.
2. Working dynamic TwiML generation.
3. Verified end-to-end calling flow.
4. Mobile browser compatibility.
5. Production-ready Twilio integration.
6. Improved logging and error handling.
7. `docs/TWILIO_CONFIGURATION.md`
8. `docs/CALL_FLOW.md`
9. Summary of:

    * Root cause(s)
    * Files changed
    * Configuration changes
    * Twilio Console changes required
    * Any remaining limitations or recommendations
