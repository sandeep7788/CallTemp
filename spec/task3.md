# Feature Specification: Call Processing, Smart Twilio Routing & Call Lifecycle Improvements

## Title

Improve Call Processing, Smart Twilio Number Routing, and Overall Call Reliability for **makecall.in**

## Objective

Strengthen the existing calling module by improving reliability, Twilio number allocation, billing accuracy, and call lifecycle management. The goal is to ensure every call is processed correctly from initiation to completion while supporting multiple Twilio numbers for concurrent calls.

---

## Scope

### 1. Review Existing Calling Module

Perform a complete review of the existing calling implementation.

* Validate the current call flow.
* Remove bugs and unnecessary logic.
* Improve reliability without changing the overall architecture.
* Reuse existing services wherever possible.

---

### 2. Smart Twilio Number Allocation

The application can have multiple Twilio numbers stored in Firebase.

Implement a smart allocation mechanism.

Requirements:

* Automatically select an available Twilio number.
* If the selected number is already handling an active call, select another available number.
* If all numbers are busy, return an appropriate message to the user.
* Mark a number as **Busy** when a call starts.
* Mark it as **Available** immediately after the call finishes, fails, or is canceled.
* Prevent two active calls from using the same Twilio number simultaneously.

---

### 3. Call Request Validation

Before placing a call:

* Validate the destination number.
* Validate wallet balance.
* Validate Twilio number availability.
* Validate required configuration.
* Prevent duplicate call requests.

Reject invalid requests with meaningful error messages.

---

### 4. Dialed Number Accuracy

Ensure that:

* The exact number entered on the dial pad is passed to Twilio.
* No digits are lost, modified, or reformatted incorrectly.
* International dialing formats are handled correctly where supported.

---

### 5. Call Lifecycle Management

Manage the complete call lifecycle.

Track every stage:

* Call Requested
* Call Initiated
* Ringing
* Answered
* In Progress
* Completed
* Busy
* Failed
* No Answer
* Canceled

Synchronize the application state with Twilio callbacks.

---

### 6. Twilio Integration Review

Review the current Twilio implementation against the latest Twilio Voice API documentation.

Ensure:

* API usage follows recommended practices.
* Callback handling is reliable.
* Error responses are handled correctly.
* Resources are cleaned up after every call.

---

### 7. Concurrent Call Support

Support multiple users making calls simultaneously.

Requirements:

* Allocate different Twilio numbers for concurrent calls.
* Avoid conflicts when multiple users initiate calls at the same time.
* Use Firestore transactions where necessary to maintain consistency.

---

### 8. Accurate Billing

Ensure billing is based on actual connected call duration.

Requirements:

* Start billing only after the call is answered.
* Stop billing immediately when the call ends.
* Apply configured billing rules.
* Record the exact deducted amount.

---

### 9. Call History Improvements

Enhance call history with accurate information.

Store:

* User ID
* Destination number
* Twilio number used
* Call SID
* Call status
* Connected duration
* Total duration
* Deducted amount
* Wallet balance before and after the call
* Disconnect reason
* Timestamp

---

### 10. Failure Recovery

Handle unexpected scenarios gracefully.

Examples:

* Twilio API timeout
* Callback failure
* Firestore update failure
* Network interruption
* Call initiation failure
* Unexpected call termination

Ensure resources are released correctly and the Twilio number is made available again.

---

### 11. Logging & Monitoring

Improve application logging.

Log:

* Call initiation
* Twilio number assignment
* Call state changes
* Wallet deductions
* Callback processing
* Errors
* Recovery actions

Keep logs structured and avoid sensitive data.

---

### 12. Code Optimization

* Remove unused calling logic.
* Eliminate duplicate code.
* Simplify complex methods.
* Improve readability and maintainability.
* Reuse existing services and utilities.

---

## Technical Constraints

* Modify the existing source code only.
* Continue using Firebase Firestore.
* Do not introduce SQL databases.
* Do not rewrite the calling module from scratch.
* Preserve existing project architecture.
* Do not create unit or integration tests.
* Keep documentation minimal.

---



You can add this as a separate section in the specification.

---

## 13. Dial Pad UI & User Experience Enhancement

Improve the existing dial pad to provide a modern, engaging, and professional user experience while keeping it optimized for mobile WebView.

### Requirements

* Redesign the existing dial pad with a modern and clean appearance.
* Use a light theme with vibrant ("funky") accent colors.
* Add smooth animations without affecting performance.
* Keep the UI responsive for different mobile screen sizes.
* Improve spacing, typography, and button alignment.
* Use rounded buttons and modern icons.
* Provide subtle touch feedback for every key press.
* Animate the dialed number as digits are entered.
* Add smooth transitions for call start, connecting, in-call, and call end states.
* Include micro-interactions for:

    * Key press
    * Delete button
    * Call button
    * Incoming status changes
    * Loading indicators
* Ensure animations remain lightweight and smooth on low-end Android devices.
* Do **not** display the native mobile keyboard. Continue using the existing custom numeric keypad for all number input.
* Keep the UI intuitive and user-friendly, with a premium, polished feel.

## Acceptance Criteria

* Calls are placed successfully using the number entered by the user.
* An available Twilio number is automatically selected.
* Multiple concurrent calls use different Twilio numbers.
* Busy Twilio numbers are not reused until released.
* Billing is based on actual connected call duration.
* Wallet deductions are accurate.
* Call history contains correct duration, deducted amount, status, and Twilio number.
* Twilio numbers are automatically released after every completed or failed call.
* The complete calling workflow is reliable, scalable, and production-ready.
* The dial pad has a modern, professional, and visually appealing design.
* Button presses provide smooth visual feedback.
* Animations are fluid and do not impact performance.
* The dial pad is fully optimized for mobile WebView.
* Users can enter numbers only through the custom keypad.
* The calling interface feels polished, responsive, and production-ready.