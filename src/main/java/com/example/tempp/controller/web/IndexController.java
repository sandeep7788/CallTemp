package com.example.tempp.controller.web;

import com.example.tempp.service.AppConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Web Controller for serving HTML pages.
 * Firebase web-app config is resolved in priority order:
 * 1. Environment variable  (FIREBASE_CLIENT_API_KEY / FIREBASE_CLIENT_APP_ID)
 * 2. application.properties literal value
 * 3. firebase-web-config.json in the classpath (local dev convenience file)
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class IndexController {

    private final AppConfigService appConfigService;
    private final ObjectMapper objectMapper;

    @Value("${firebase.client.api-key:}")
    private String firebaseApiKey;

    @Value("${firebase.client.auth-domain:}")
    private String firebaseAuthDomain;

    @Value("${firebase.client.project-id:}")
    private String firebaseProjectId;

    @Value("${firebase.client.app-id:}")
    private String firebaseAppId;

    @Value("${app.site.url:https://makecall.in}")
    private String siteUrl;

    private static final String COMMON_KEYWORDS = "MakeCall, makecall.in, Cloud Calling, PSTN Calling, Voice Calling, Twilio Calling, "
            + "Business Calling, Phone Call API, Cloud Telephony, Business Communication, Virtual Calling, Online Calling Platform, "
            + "मेककॉल, makecall, क्लाउड कॉलिंग, फोन कॉल, व्यवसायिक कॉलिंग, ट्विलियो कॉलिंग, क्लाउड टेलीफोनी, "
            + "ऑनलाइन कॉल, वॉयस कॉल, बिजनेस कम्युनिकेशन";

    // ─── Pages ───────────────────────────────────────────────────────────────

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Serve the index page for both root and /index paths
     *
     * @return ModelAndView pointing to index template
     */
    @GetMapping({"/", "/index"})
    public ModelAndView indexPage() {
        log.info("Serving index page");
        ModelAndView mav = new ModelAndView("public/index");

        // Add dashboard message to the model
        Map<String, Object> dashboardMessage = appConfigService.getDashboardMessage();
        mav.addObject("dashboardMessage", dashboardMessage.get("message"));
        mav.addObject("dashboardMessageEnabled", dashboardMessage.get("enabled"));

        Map<String, Object> maintenanceStatus = appConfigService.getMaintenanceStatus();
        mav.addObject("maintenanceMode", maintenanceStatus.get("enabled"));
        mav.addObject("maintenanceMessage", maintenanceStatus.get("message"));

        addFirebaseConfig(mav);
        addCommonSiteModel(mav, "home",
                "MakeCall – Secure Cloud Calling, PSTN Calling & Business Voice Solutions",
                "MakeCall (makecall.in) is a secure virtual calling platform for Cloud Calling, PSTN Calling, Twilio-powered Voice Calling and Business Communication solutions.",
                "/");

        // Feature flag: controls whether the Google Sign-In flow is shown
        boolean enableGoogleSignIn = appConfigService.isGoogleSignInEnabled();
        mav.addObject("enableGoogleSignIn", enableGoogleSignIn);

        log.debug("Dashboard message enabled: {}, message: {}", dashboardMessage.get("enabled"), dashboardMessage.get("message"));
        log.debug("Maintenance mode enabled: {}, message: {}", maintenanceStatus.get("enabled"), maintenanceStatus.get("message"));

        return mav;
    }

    // ─── Firebase web config resolution ──────────────────────────────────────

    /**
     * Serve the terms and conditions page
     *
     * @return ModelAndView pointing to terms template
     */
    @GetMapping({"/terms", "/terms-and-conditions"})
    public ModelAndView termsPage() {
        log.info("Serving terms and conditions page");
        ModelAndView mav = new ModelAndView("public/terms");
        addCommonSiteModel(mav, "terms",
                "Terms & Conditions – MakeCall Secure Cloud Calling Platform",
                "Read MakeCall Terms & Conditions for using makecall.in, including wallet billing, PSTN Calling, Twilio-powered secure voice calling, permitted use and business communication responsibilities.",
                "/terms");
        return mav;
    }

    @GetMapping("/about")
    public ModelAndView aboutPage() {
        log.info("Serving about page");
        return publicPage("about", "about",
                "About Us – MakeCall Cloud Calling & Business Communication",
                "Learn about MakeCall, a secure virtual calling platform for Cloud Calling, PSTN Calling, Twilio-powered Voice Calling and modern Business Communication on makecall.in.",
                "/about");
    }

    @GetMapping("/contact")
    public ModelAndView contactPage() {
        log.info("Serving contact page");
        return publicPage("contact", "contact",
                "Contact Us – MakeCall Support for Cloud Calling & PSTN Calling",
                "Contact MakeCall for secure Cloud Calling, PSTN Calling, Twilio Calling, online voice calling support and Business Communication solutions in India.",
                "/contact");
    }

    @GetMapping("/pricing")
    public ModelAndView pricingPage() {
        log.info("Serving pricing page");
        return publicPage("pricing", "pricing",
                "Pricing – MakeCall Cloud Telephony & PSTN Calling Rates",
                "View MakeCall pricing for secure voice calling, PSTN Calling, Twilio-powered Cloud Calling, wallet recharge and virtual calling platform usage on makecall.in.",
                "/pricing");
    }

    @GetMapping({"/privacy-policy", "/privacy"})
    public ModelAndView privacyPolicyPage() {
        log.info("Serving privacy policy page");
        return publicPage("privacy-policy", "privacy",
                "Privacy Policy – MakeCall Secure Voice Calling Platform",
                "Read the MakeCall Privacy Policy to understand how makecall.in handles account data, call records, payments and secure Cloud Calling, PSTN Calling and Business Communication information.",
                "/privacy-policy");
    }

    @GetMapping({"/refund-policy", "/refunds"})
    public ModelAndView refundPolicyPage() {
        log.info("Serving refund policy page");
        return publicPage("refund-policy", "refund",
                "Refund Policy – MakeCall Wallet Recharge & Calling Payments",
                "Review the MakeCall Refund Policy for wallet recharges, Razorpay payments, failed transactions, PSTN Calling charges and secure voice calling billing on makecall.in.",
                "/refund-policy");
    }

    private ModelAndView publicPage(String template, String activePage, String title, String description, String canonicalPath) {
        ModelAndView mav = new ModelAndView("public/" + template);
        addCommonSiteModel(mav, activePage, title, description, canonicalPath);
        return mav;
    }

    private void addCommonSiteModel(ModelAndView mav, String activePage, String title, String description, String canonicalPath) {
        mav.addObject("siteUrl", normalizedSiteUrl());
        mav.addObject("activePage", activePage);
        mav.addObject("pageTitle", title);
        mav.addObject("pageDescription", description);
        mav.addObject("pageKeywords", COMMON_KEYWORDS);
        mav.addObject("canonicalPath", canonicalPath);
        mav.addObject("structuredDataJson", buildStructuredDataJson(title, description, canonicalPath));
    }

    private String normalizedSiteUrl() {
        if (isBlank(siteUrl)) {
            return "https://makecall.in";
        }
        return siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
    }

    private String canonicalUrl(String canonicalPath) {
        return "/".equals(canonicalPath) ? normalizedSiteUrl() + "/" : normalizedSiteUrl() + canonicalPath;
    }

    private String buildStructuredDataJson(String title, String description, String canonicalPath) {
        try {
            String url = canonicalUrl(canonicalPath);

            Map<String, Object> address = new LinkedHashMap<>();
            address.put("@type", "PostalAddress");
            address.put("streetAddress", "P-9/1583, Sanjay Nagar, D.C.M. Ajmer Road");
            address.put("addressLocality", "Jaipur");
            address.put("addressRegion", "Rajasthan");
            address.put("postalCode", "302021");
            address.put("addressCountry", "IN");

            Map<String, Object> contactPoint = new LinkedHashMap<>();
            contactPoint.put("@type", "ContactPoint");
            contactPoint.put("telephone", "+91-7239962886");
            contactPoint.put("contactType", "customer support");
            contactPoint.put("email", "s.pareekpro@gmail.com");
            contactPoint.put("areaServed", "IN");
            contactPoint.put("availableLanguage", List.of("English", "Hindi"));

            Map<String, Object> organization = new LinkedHashMap<>();
            organization.put("@type", "Organization");
            organization.put("@id", "https://makecall.in/#organization");
            organization.put("name", "MakeCall");
            organization.put("url", "https://makecall.in/");
            organization.put("logo", "https://makecall.in/favicon.svg");
            organization.put("description", "MakeCall provides secure cloud-based calling, PSTN calling, business communication and voice solutions powered by modern cloud technologies.");
            organization.put("email", List.of("mailto:s.pareekpro@gmail.com", "mailto:s.pareek7788@gmail.com"));
            organization.put("telephone", "+91-7239962886");
            organization.put("sameAs", List.of("https://instagram.com/makecall.in"));
            organization.put("address", address);
            organization.put("contactPoint", contactPoint);

            Map<String, Object> website = new LinkedHashMap<>();
            website.put("@type", "WebSite");
            website.put("@id", "https://makecall.in/#website");
            website.put("url", "https://makecall.in/");
            website.put("name", "MakeCall");
            website.put("publisher", Map.of("@id", "https://makecall.in/#organization"));
            website.put("inLanguage", "en-IN");

            Map<String, Object> webPage = new LinkedHashMap<>();
            webPage.put("@type", "WebPage");
            webPage.put("@id", url + "#webpage");
            webPage.put("url", url);
            webPage.put("name", title);
            webPage.put("description", description);
            webPage.put("isPartOf", Map.of("@id", "https://makecall.in/#website"));
            webPage.put("about", Map.of("@id", "https://makecall.in/#organization"));
            webPage.put("inLanguage", "en-IN");

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("@context", "https://schema.org");
            root.put("@graph", List.of(organization, website, webPage));
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.debug("Could not build structured data JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Resolves Firebase client-SDK config with three layers:
     * <ol>
     *   <li>application.properties / environment variables (highest priority)</li>
     *   <li>firebase-web-config.json on the classpath (local dev fallback)</li>
     *   <li>Auto-derive authDomain from projectId</li>
     * </ol>
     */
    private void addFirebaseConfig(ModelAndView mav) {
        String apiKey = firebaseApiKey;
        String authDomain = firebaseAuthDomain;
        String projectId = firebaseProjectId;
        String appId = firebaseAppId;

        // Fallback: read firebase-web-config.json when properties are blank
        if (isBlank(apiKey) || isBlank(appId)) {
            Map<String, String> fileCfg = readFirebaseWebConfigFile();
            if (!fileCfg.isEmpty()) {
                if (isBlank(apiKey))
                    apiKey = fileCfg.getOrDefault("apiKey", "");
                if (isBlank(authDomain))
                    authDomain = fileCfg.getOrDefault("authDomain", "");
                if (isBlank(projectId))
                    projectId = fileCfg.getOrDefault("projectId", "");
                if (isBlank(appId))
                    appId = fileCfg.getOrDefault("appId", "");
            }
        }

        // Auto-derive authDomain from projectId when not explicitly set
        if (isBlank(authDomain) && !isBlank(projectId) && !"your-firebase-project-id".equals(projectId.trim())) {
            authDomain = projectId.trim() + ".firebaseapp.com";
        }

        boolean configured = !isBlank(apiKey) && !isBlank(appId);
        if (!configured) {
            log.warn("Firebase client config is incomplete – Google Sign-In will be disabled. " + "Set FIREBASE_CLIENT_API_KEY and FIREBASE_CLIENT_APP_ID, " + "or fill in src/main/resources/firebase-web-config.json.");
        } else {
            log.debug("Firebase client config loaded (project={})", projectId);
        }

        mav.addObject("firebaseApiKey", apiKey);
        mav.addObject("firebaseAuthDomain", authDomain);
        mav.addObject("firebaseProjectId", projectId);
        mav.addObject("firebaseAppId", appId);
        mav.addObject("firebaseConfigured", configured);
    }

    /**
     * Reads firebase-web-config.json from the classpath (returns empty map on any error).
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> readFirebaseWebConfigFile() {
        try {
            ClassPathResource resource = new ClassPathResource("firebase-web-config.json");
            if (!resource.exists()) {
                return Map.of();
            }
            try (InputStream is = resource.getInputStream()) {
                Map<String, Object> raw = objectMapper.readValue(is, new TypeReference<>() {
                });
                // Skip comment / instruction keys; keep only real Firebase config keys
                Map<String, String> result = new java.util.HashMap<>();
                for (String key : new String[]{"apiKey", "authDomain", "projectId", "appId", "measurementId"}) {
                    Object val = raw.get(key);
                    if (val instanceof String s && !s.isBlank()) {
                        result.put(key, s);
                    }
                }
                return result;
            }
        } catch (Exception e) {
            log.debug("Could not read firebase-web-config.json: {}", e.getMessage());
            return Map.of();
        }
    }
}
