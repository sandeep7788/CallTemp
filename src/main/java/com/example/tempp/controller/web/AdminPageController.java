package com.example.tempp.controller.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the admin panel HTML shell.
 *
 * <p>Access control happens before this method ever runs: {@code AdminAuthInterceptor}
 * (registered in {@code WebMvcConfig} for {@code /admin/**}) redirects unauthorized
 * visitors to {@code /}. This controller only renders the page shell; all data is
 * loaded client-side from {@code /admin/api/**}, which is protected by the same
 * interceptor.
 */
@Slf4j
@Controller
public class AdminPageController {

    @GetMapping("/admin")
    public String adminPage() {
        log.info("Serving admin panel page");
        return "admin/dashboard";
    }
}
