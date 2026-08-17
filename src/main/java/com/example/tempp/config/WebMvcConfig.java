package com.example.tempp.config;

import com.example.tempp.interceptor.GlobalRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for registering global interceptors.
 * Registers the global rate limit interceptor for all API requests.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final GlobalRateLimitInterceptor globalRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(globalRateLimitInterceptor)
                .addPathPatterns("/api/**", "/user/**", "/call/**", "/payment/**")
                .excludePathPatterns(
                    "/index",
                    "/",
                    "/static/**",
                    "/favicon.ico",
                    "/health",
                    "/public/**"
                );
    }
}

