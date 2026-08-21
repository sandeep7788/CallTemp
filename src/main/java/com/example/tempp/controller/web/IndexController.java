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

    // Additional long-tail keywords for the FAQ / privacy & acceptable-use page only.
    private static final String FAQ_KEYWORDS = COMMON_KEYWORDS + ", unknown call, unknown calling, unknown number call, unknown caller, "
            + "make unknown call, make an unknown call, private call, private calling, private phone call, "
            + "secret call, secret calling, secret phone call, hidden number call, hidden caller, "
            + "anonymous call, anonymous calling, call without showing number, call without revealing number, "
            + "number hide call, hidden number se call, private number se call, unknown number se call, secret number call, "
            + "prank call, prank calling, prank phone call, online prank call, prank call online, prank call app, "
            + "prank call website, prank call kaise kare, "
            + "अनजान नंबर से कॉल, अनजान कॉल, प्राइवेट कॉल, सीक्रेट कॉल, गुप्त कॉल, कॉल का पता न चले, कॉल का पता ना चले, "
            + "बिना पता चले कॉल, बिना नंबर दिखाए कॉल, नंबर छुपाकर कॉल, नंबर छुपा कर कॉल, कॉल कैसे छुपाएं, प्रैंक कॉल, "
            + "call ka pata na chale, call ka pata na chale kaise kare, bina pata chale call, bina number dikhaye call, "
            + "number chhupa kar call, secret call kaise kare";

    private static final List<Map<String, String>> FAQ_ENTRIES = List.of(
            faq("How do I make online calls with MakeCall?",
                    "Sign in to MakeCall.in, add credit to your wallet, enter the phone number on the dial pad and tap Call. "
                            + "You can call mobile and landline (PSTN) numbers in India directly from your browser – no app download needed."),
            faq("What is the MakeCall calling rate?",
                    "MakeCall charges ₹6 per minute for calls to mobile and landline numbers, billed per second from the moment your call connects."),
            faq("Can I call landline numbers with MakeCall?",
                    "Yes. MakeCall supports both mobile and landline (PSTN) numbers in India – enter the number with its STD code and tap Call."),
            faq("Do I need to download an app to use MakeCall?",
                    "No. MakeCall works entirely inside your web browser using WebRTC technology. Just visit makecall.in, sign in, and start calling – no app install required."),
            faq("Can I make an unknown, private or secret call with MakeCall?",
                    "MakeCall is an online calling platform for making regular voice calls to mobile and landline numbers – it is not an anonymous or secret-calling service. "
                            + "When you call someone through MakeCall, the person you're calling sees a MakeCall-assigned virtual number, not your personal mobile number. "
                            + "That gives you a genuine layer of number privacy, but you cannot make the call appear as \"Unknown\", blank or untraceable, and you cannot choose or spoof a custom caller ID."),
            faq("Does MakeCall hide or mask my personal phone number from the person I call?",
                    "Yes, in the sense that your personal mobile number is never shown to the person you call – calls are routed through MakeCall's own virtual number, not your SIM number. "
                            + "This is different from a \"hidden number\" or \"unknown caller\" service: the recipient still sees a real, valid number on their phone, just not yours."),
            faq("Can I use MakeCall to make anonymous calls that cannot be traced back to me?",
                    "No. MakeCall requires sign-in and links every call to your account for billing, and call records are retained as described in our Privacy Policy. It is not designed for anonymous or untraceable calling."),
            faq("Can I use MakeCall for prank calls?",
                    "No. MakeCall's Terms & Conditions explicitly prohibit prank, hoax, harassing or misleading calls. MakeCall is intended for genuine personal and business communication, not entertainment-style prank calling."),
            faq("कॉल का पता न चले — क्या ऐसा MakeCall से संभव है?",
                    "MakeCall एक ऑनलाइन कॉलिंग प्लेटफ़ॉर्म है, कोई सीक्रेट या एनोनिमस कॉलिंग सर्विस नहीं। "
                            + "MakeCall से कॉल करने पर सामने वाले व्यक्ति को आपका पर्सनल मोबाइल नंबर नहीं दिखता – "
                            + "इसके बजाय MakeCall का अपना वर्चुअल नंबर दिखता है, जिससे आपकी नंबर प्राइवेसी बनी रहती है। "
                            + "लेकिन कॉल \"Unknown\" या खाली नंबर के रूप में नहीं दिखाई जा सकती, कॉल पूरी तरह untraceable नहीं होती, और आप कोई कस्टम नंबर चुन या छुपा नहीं सकते।"),
            faq("Bina number dikhaye ya number chhupa kar call — kya MakeCall se ho sakta hai?",
                    "MakeCall se call karne par aapka personal mobile number receiver ko nahi dikhta – uske jagah MakeCall ka apna virtual number dikhta hai, "
                            + "isliye number privacy real hai. Lekin yeh \"hidden number call\" ya \"unknown number se call\" jaisi service nahi hai: "
                            + "receiver ko ek valid number dikhta hai (blank ya \"Unknown\" nahi), aur account sign-in aur call history ki wajah se call anonymous ya untraceable nahi hoti."),
            faq("क्या MakeCall प्रैंक कॉल के लिए इस्तेमाल किया जा सकता है? (Prank call kaise kare)",
                    "नहीं। MakeCall की नियम एवं शर्तें प्रैंक, धमकी या भ्रामक कॉल की अनुमति नहीं देतीं। "
                            + "MakeCall केवल असली पर्सनल और बिज़नेस कम्युनिकेशन के लिए है, prank ya entertainment calling app nahi hai।")
    );

    private static Map<String, String> faq(String question, String answer) {
        Map<String, String> qa = new LinkedHashMap<>();
        qa.put("question", question);
        qa.put("answer", answer);
        return qa;
    }

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
                "MakeCall – Secure Online Calling Platform for India | Cloud & PSTN Calling",
                "MakeCall (makecall.in) is a secure browser-based online calling platform built for India – Cloud Calling, PSTN Calling, Twilio-powered Voice Calling and Business Communication with INR wallet billing.",
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
                "Terms & Conditions – MakeCall Secure Cloud Calling Platform (India)",
                "Read MakeCall Terms & Conditions for using makecall.in in India, including wallet billing, PSTN Calling, Twilio-powered secure voice calling, permitted use and business communication responsibilities.",
                "/terms");
        return mav;
    }

    @GetMapping("/about")
    public ModelAndView aboutPage() {
        log.info("Serving about page");
        return publicPage("about", "about",
                "About Us – MakeCall Online Calling Platform for India",
                "Learn about MakeCall, a secure browser-based online calling platform built for India – Cloud Calling, PSTN Calling, Twilio-powered Voice Calling and modern Business Communication on makecall.in.",
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
                "Pricing – MakeCall Cloud Telephony & PSTN Calling Rates in India",
                "View MakeCall's ₹-based pricing for secure voice calling, PSTN Calling, Twilio-powered Cloud Calling, wallet recharge and virtual calling platform usage in India on makecall.in.",
                "/pricing");
    }

    @GetMapping({"/privacy-policy", "/privacy"})
    public ModelAndView privacyPolicyPage() {
        log.info("Serving privacy policy page");
        return publicPage("privacy-policy", "privacy",
                "Privacy Policy – MakeCall Secure Voice Calling Platform (India)",
                "Read the MakeCall Privacy Policy to understand how makecall.in handles account data, call records, payments and secure Cloud Calling, PSTN Calling and Business Communication information for users in India.",
                "/privacy-policy");
    }

    @GetMapping({"/refund-policy", "/refunds"})
    public ModelAndView refundPolicyPage() {
        log.info("Serving refund policy page");
        return publicPage("refund-policy", "refund",
                "Refund Policy – MakeCall Wallet Recharge & Calling Payments (India)",
                "Review the MakeCall Refund Policy for INR wallet recharges, Razorpay payments, failed transactions, PSTN Calling charges and secure voice calling billing for users in India on makecall.in.",
                "/refund-policy");
    }

    @GetMapping("/faq")
    public ModelAndView faqPage() {
        log.info("Serving FAQ page");
        ModelAndView mav = new ModelAndView("public/faq");
        addCommonSiteModel(mav, "faq",
                "MakeCall FAQ – Online Calling, Number Privacy & Acceptable Use in India",
                "Answers about MakeCall's browser-based online calling platform in India, including how private, unknown-number and secret calling searches (कॉल का पता न चले) actually relate to number privacy on PSTN calls, pricing, and our policy on prank or anonymous calling.",
                "/faq",
                FAQ_KEYWORDS,
                FAQ_ENTRIES);
        return mav;
    }

    private ModelAndView publicPage(String template, String activePage, String title, String description, String canonicalPath) {
        ModelAndView mav = new ModelAndView("public/" + template);
        addCommonSiteModel(mav, activePage, title, description, canonicalPath);
        return mav;
    }

    private void addCommonSiteModel(ModelAndView mav, String activePage, String title, String description, String canonicalPath) {
        addCommonSiteModel(mav, activePage, title, description, canonicalPath, COMMON_KEYWORDS, null);
    }

    private void addCommonSiteModel(ModelAndView mav, String activePage, String title, String description, String canonicalPath,
                                     String keywords, List<Map<String, String>> faqEntries) {
        mav.addObject("siteUrl", normalizedSiteUrl());
        mav.addObject("activePage", activePage);
        mav.addObject("pageTitle", title);
        mav.addObject("pageDescription", description);
        mav.addObject("pageKeywords", keywords);
        mav.addObject("canonicalPath", canonicalPath);
        mav.addObject("structuredDataJson", buildStructuredDataJson(title, description, canonicalPath, faqEntries));
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

    private String buildStructuredDataJson(String title, String description, String canonicalPath, List<Map<String, String>> faqEntries) {
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
            contactPoint.put("telephone", "+91-9452707778");
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
            organization.put("telephone", "+91-9452707778");
            organization.put("areaServed", "IN");
            organization.put("sameAs", List.of("https://instagram.com/makecall.in"));
            organization.put("address", address);
            organization.put("contactPoint", contactPoint);

            Map<String, Object> website = new LinkedHashMap<>();
            website.put("@type", "WebSite");
            website.put("@id", "https://makecall.in/#website");
            website.put("url", "https://makecall.in/");
            website.put("name", "MakeCall");
            website.put("publisher", Map.of("@id", "https://makecall.in/#organization"));
            website.put("inLanguage", List.of("en-IN", "hi-IN"));

            Map<String, Object> priceSpecification = new LinkedHashMap<>();
            priceSpecification.put("@type", "UnitPriceSpecification");
            priceSpecification.put("price", "6.00");
            priceSpecification.put("priceCurrency", "INR");
            priceSpecification.put("unitText", "per minute");

            Map<String, Object> offer = new LinkedHashMap<>();
            offer.put("@type", "Offer");
            offer.put("price", "6.00");
            offer.put("priceCurrency", "INR");
            offer.put("priceSpecification", priceSpecification);

            Map<String, Object> webApplication = new LinkedHashMap<>();
            webApplication.put("@type", "WebApplication");
            webApplication.put("@id", "https://makecall.in/#webapp");
            webApplication.put("name", "MakeCall");
            webApplication.put("url", "https://makecall.in/");
            webApplication.put("description", "Browser-based online calling platform for making phone calls to mobile and landline (PSTN) numbers over the internet in India.");
            webApplication.put("applicationCategory", "CommunicationApplication");
            webApplication.put("operatingSystem", "Any (Web Browser)");
            webApplication.put("browserRequirements", "Requires JavaScript, WebRTC support");
            webApplication.put("areaServed", "IN");
            webApplication.put("offers", offer);
            webApplication.put("featureList", List.of(
                    "Online calling to mobile numbers in India",
                    "Online calling to landline PSTN numbers in India",
                    "Browser-based web dialer",
                    "No app download required",
                    "Wallet-based billing in INR",
                    "Call history tracking"
            ));

            Map<String, Object> webPage = new LinkedHashMap<>();
            webPage.put("@type", "WebPage");
            webPage.put("@id", url + "#webpage");
            webPage.put("url", url);
            webPage.put("name", title);
            webPage.put("description", description);
            webPage.put("isPartOf", Map.of("@id", "https://makecall.in/#website"));
            webPage.put("about", Map.of("@id", "https://makecall.in/#organization"));
            webPage.put("inLanguage", "en-IN");

            List<Object> graph = new java.util.ArrayList<>(List.of(organization, website, webApplication, webPage));
            if (faqEntries != null && !faqEntries.isEmpty()) {
                List<Map<String, Object>> questions = new java.util.ArrayList<>();
                for (Map<String, String> qa : faqEntries) {
                    Map<String, Object> answer = new LinkedHashMap<>();
                    answer.put("@type", "Answer");
                    answer.put("text", qa.get("answer"));

                    Map<String, Object> question = new LinkedHashMap<>();
                    question.put("@type", "Question");
                    question.put("name", qa.get("question"));
                    question.put("acceptedAnswer", answer);
                    questions.add(question);
                }
                Map<String, Object> faqPage = new LinkedHashMap<>();
                faqPage.put("@type", "FAQPage");
                faqPage.put("mainEntity", questions);
                graph.add(faqPage);
            }

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("@context", "https://schema.org");
            root.put("@graph", graph);
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
