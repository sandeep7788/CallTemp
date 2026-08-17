Implement a feature flag to control the login flow using a boolean value stored in the **DB Config** table.

### Configuration

* Add a boolean configuration, for example: `enableGoogleSignIn`.
* Read this value from the database at runtime.
* The login flow must be controlled entirely by this configuration.

### Behavior

#### Case 1: `enableGoogleSignIn = false`

* Completely disable Google Sign-In.
* Do **not** display any Google Sign-In button, popup, or authentication flow.
* Immediately show a simple dialog requesting:

    * User Number (required)
    * Location (optional)
* If the user leaves the location empty or location detection fails, automatically use the default value `"x"`.
* Create/update the user record in the **User** table using default values for all Google-related fields (e.g., Google ID, email, profile picture, display name, etc., as applicable in the schema).
* Store the entered User Number and the resolved Location (`"x"` if unavailable) in the User table.
* Authentication and user creation should work without requiring any Google account.

#### Case 2: `enableGoogleSignIn = true`

* Google Sign-In is **mandatory**.
* Display only the Google Sign-In flow.
* Do **not** ask the user to manually enter the User Number or Location.
* Create/update the user record using the Google account information.
* If the user's location cannot be fetched or is empty, store the default value `"x"`.

### Additional Requirements

* No Google Sign-In UI, popup, API call, or authentication logic should be triggered when `enableGoogleSignIn` is `false`.
* Always use `"x"` as the default location whenever the location is unavailable, regardless of the login method.
* Ensure all required columns in the User table are populated. When Google Sign-In is disabled, populate Google-related fields with appropriate default values (`NULL`, empty string, or other schema-defined defaults).
* Keep the implementation modular so additional feature flags can be added through the DB Config table in the future.
