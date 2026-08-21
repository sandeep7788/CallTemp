package com.example.tempp.interceptor;

import com.example.tempp.model.UserAccount;
import com.example.tempp.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

import static com.example.tempp.controller.api.UserController.SESSION_USER_ID;

/**
 * Guards every {@code /admin/**} request (except {@code /admin/payment/**}, which
 * predates this interceptor and has its own shared-secret header check) so that only
 * a signed-in, non-blocked user with {@code isAdmin=true} can proceed.
 *
 * <p>JSON API calls ({@code /admin/api/**}) receive a proper 401/403 JSON body.
 * The admin HTML page ({@code /admin}) redirects unauthorized visitors to {@code /}
 * instead, since there is no dedicated admin login page – admins sign in through
 * the normal login flow on the main site, then navigate to {@code /admin}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        boolean isApiRequest = request.getRequestURI().startsWith("/admin/api/");

        HttpSession session = request.getSession(false);
        Object sessionUserId = session != null ? session.getAttribute(SESSION_USER_ID) : null;
        if (!(sessionUserId instanceof String userId) || userId.isBlank()) {
            log.warn("Admin area access denied - no session. uri={}", request.getRequestURI());
            return reject(response, isApiRequest, 401, "Please login first");
        }

        UserAccount user;
        try {
            user = userService.getUserById(userId);
        } catch (IllegalStateException ex) {
            log.warn("Admin area access denied - session user not found. uri={}", request.getRequestURI());
            return reject(response, isApiRequest, 401, "Please login first");
        }

        if (!user.isAdmin() || user.isBlocked()) {
            log.warn("Admin area access denied - userId={}, isAdmin={}, isBlocked={}, uri={}", userId, user.isAdmin(), user.isBlocked(), request.getRequestURI());
            return reject(response, isApiRequest, 403, "Access denied");
        }

        request.setAttribute("adminUserId", userId);
        return true;
    }

    private boolean reject(HttpServletResponse response, boolean isApiRequest, int status, String message) throws IOException {
        if (isApiRequest) {
            response.setStatus(status);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + message + "\",\"message\":\"" + message + "\"}");
        } else {
            response.sendRedirect("/");
        }
        return false;
    }
}
