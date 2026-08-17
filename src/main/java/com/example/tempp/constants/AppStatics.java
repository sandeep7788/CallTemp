package com.example.tempp.constants;

/**
 * Compatibility marker for old application static variables.
 * <p>
 * Do not store logged-in user data in static fields. Spring controllers and
 * services are shared across concurrent requests; use HttpSession/request data
 * or persistent identifiers instead.
 */
public final class AppStatics {
    private AppStatics() {
        // Utility class - prevent instantiation
    }
}
