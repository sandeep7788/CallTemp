package com.example.tempp.config;

import com.example.tempp.interceptor.AdminAuthInterceptor;
import com.example.tempp.interceptor.GlobalRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for registering global interceptors.
 * Registers the global rate limit interceptor for all API requests, and the
 * admin authorization interceptor for the admin panel.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final GlobalRateLimitInterceptor globalRateLimitInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(globalRateLimitInterceptor)
                .addPathPatterns("/api/**", "/user/**", "/call/**", "/payment/**", "/admin/**")
                .excludePathPatterns(
                    "/index",
                    "/",
                    "/static/**",
                    "/favicon.ico",
                    "/health",
                    "/public/**"
                );

        // Admin authorization: requires isAdmin=true (and not blocked) on the resolved
        // session user. Excludes /admin/payment/** which predates this interceptor and
        // is already guarded by its own X-Reconcile-Key shared secret.
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/admin", "/admin/**")
                .excludePathPatterns("/admin/payment/**");
    }
}

