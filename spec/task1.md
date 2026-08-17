## Feature Specification: Wallet Management & Dynamic Call Billing

### Title

Implement Dynamic Wallet Management, Call Billing, and Auto Disconnect for **makecall.in**

### Objective

Enhance the existing PSTN calling application by improving the current wallet functionality. The system should use **Firebase Firestore** for configuration and wallet data, ensuring call billing is dynamic, configurable, and reliable.

---

## Scope

### 1. Wallet Management

* Improve the existing wallet functionality.
* Validate wallet balance before initiating a call.
* Deduct call charges accurately during the call.
* Prevent duplicate deductions using Firestore transactions.
* Prevent users from making calls with insufficient balance.

---

### 2. Dynamic Configuration

Move all call-related configuration to Firebase Firestore.

Configuration examples:

* Call charge per minute
* Call charge per second
* Billing interval
* Maximum call duration
* Minimum wallet balance required
* Auto disconnect threshold
* Currency

If a configuration is unavailable, use a predefined default value.

---

### 3. Call Billing

* Calculate call charges based on Firestore configuration.
* Support configurable billing intervals.
* Continue billing until:

    * User ends the call
    * Wallet balance is exhausted
    * Maximum call duration is reached
* Ensure accurate billing without overcharging.

---

### 4. Auto Disconnect

Automatically disconnect calls when:

* Wallet balance reaches zero or below.
* Remaining balance cannot cover the next billing interval.
* Configured maximum call duration is reached.

Use the existing Twilio integration for disconnecting calls.

---

### 5. Razorpay Integration

Enhance the existing Razorpay implementation to:

* Credit wallet after successful payment verification.
* Prevent duplicate wallet credits.
* Record recharge transactions.

---

### 6. Call History

Enhance existing call history by storing:

* Call SID
* User ID
* Start time
* End time
* Call duration
* Amount charged
* Disconnect reason
* Wallet balance before call
* Wallet balance after call

---

### 7. Wallet Transactions

Maintain transaction history for:

* Wallet recharge
* Call deductions
* Refunds (if applicable)

Store all transaction records in Firestore.

---

### 8. Logging

Add logging for:

* Call initiation
* Call completion
* Wallet deduction
* Wallet recharge
* Auto disconnect
* Twilio callbacks
* Razorpay callbacks
* Firestore errors
* Application exceptions

Avoid logging sensitive information.

---

### 9. Code Cleanup

* Remove unused classes and methods.
* Remove duplicate logic.
* Reuse existing services wherever possible.
* Keep the implementation clean and maintainable.
* Do not introduce unnecessary dependencies.

---

## Technical Constraints

* Modify the existing project only.
* Use Firebase Firestore for all persistence.
* Do not introduce SQL or relational databases.
* Do not create unnecessary new collections if existing ones can be reused.
* Keep documentation minimal.
* Do not create unit or integration test cases as part of this task.

---

## Acceptance Criteria

* Users cannot start calls without sufficient wallet balance.
* Wallet deductions are accurate and consistent.
* Call charges are configurable through Firestore.
* Calls automatically disconnect when wallet balance or configured limits are reached.
* Razorpay recharge updates the wallet correctly.
* Call history and wallet transactions are stored successfully.
* Existing functionality remains unaffected.
* The codebase is cleaned up by removing unused or duplicate code while preserving existing architecture.
