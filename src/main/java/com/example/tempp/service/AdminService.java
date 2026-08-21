package com.example.tempp.service;

import com.example.tempp.model.CallHistory;
import com.example.tempp.model.UserAccount;
import com.example.tempp.model.WalletTransaction;
import com.example.tempp.repository.CallHistoryRepository;
import com.example.tempp.repository.UserAccountRepository;
import com.example.tempp.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-side listing/aggregation and moderation logic for the admin panel.
 * Reuses existing repositories and {@link UserService}; introduces no new
 * Firestore collections ("wallets" is a view over {@code UserAccount}, and
 * "transactions" is the existing {@code wallet_transactions} ledger).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final int DEFAULT_USER_LIST_LIMIT = 300;
    private static final int DEFAULT_TX_LIST_LIMIT = 200;
    private static final int USER_DETAIL_TX_LIMIT = 20;

    private final UserAccountRepository userAccountRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final CallHistoryRepository callHistoryRepository;
    private final UserService userService;

    // ─── Users ───────────────────────────────────────────────────────────────

    /**
     * Lists users, optionally filtered by blocked status (pushed down to Firestore)
     * and/or a free-text search on name/number/email/id (applied in-memory, since
     * Firestore has no substring text search).
     */
    public List<UserAccount> listUsers(String search, Boolean blocked, int limit) {
        int effectiveLimit = limit > 0 ? Math.min(limit, 1000) : DEFAULT_USER_LIST_LIMIT;
        List<UserAccount> users = blocked != null
                ? userAccountRepository.findByBlocked(blocked, effectiveLimit)
                : userAccountRepository.findAll(effectiveLimit);

        if (search == null || search.isBlank()) {
            return users;
        }
        String needle = search.trim().toLowerCase();
        return users.stream()
                .filter(u -> containsIgnoreCase(u.getName(), needle)
                        || containsIgnoreCase(u.getNumber(), needle)
                        || containsIgnoreCase(u.getEmail(), needle)
                        || containsIgnoreCase(u.getId(), needle))
                .toList();
    }

    /**
     * Wallet-focused view of the same user data, sorted by wallet balance (desc).
     * There is no separate "wallets" collection – balance lives on the user document.
     */
    public List<UserAccount> listWallets(String search, int limit) {
        return listUsers(search, null, limit).stream()
                .sorted((a, b) -> Double.compare(b.getWalletBalance(), a.getWalletBalance()))
                .toList();
    }

    public Map<String, Object> getUserDetail(String userId) {
        UserAccount user = userService.getUserById(userId);
        List<WalletTransaction> recentTransactions = walletTransactionRepository.findAllByUserId(userId, null, null, null, USER_DETAIL_TX_LIMIT);
        List<CallHistory> recentCalls = callHistoryRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);

        Map<String, Object> detail = new HashMap<>();
        detail.put("user", user);
        detail.put("recentTransactions", recentTransactions);
        detail.put("recentCalls", recentCalls);
        return detail;
    }

    /**
     * Blocks a user. Refuses to let an admin block their own account (a common
     * real-world footgun – an admin locking themselves out with no way back in).
     */
    public UserAccount blockUser(String targetUserId, String actingAdminId) {
        if (targetUserId != null && targetUserId.equals(actingAdminId)) {
            throw new IllegalArgumentException("You cannot block your own admin account");
        }
        UserAccount saved = userService.setBlocked(targetUserId, true);
        log.info("Admin {} blocked user {}", actingAdminId, targetUserId);
        return saved;
    }

    public UserAccount unblockUser(String targetUserId, String actingAdminId) {
        UserAccount saved = userService.setBlocked(targetUserId, false);
        log.info("Admin {} unblocked user {}", actingAdminId, targetUserId);
        return saved;
    }

    // ─── Transactions ────────────────────────────────────────────────────────

    /**
     * Lists wallet ledger transactions. When {@code userId} is provided this is a
     * cheap sub-collection query; otherwise it is a Firestore collection-group
     * query across all users (see {@link WalletTransactionRepository#findAll}).
     */
    public List<WalletTransaction> listTransactions(String userId, String type, Instant from, Instant to, int limit) {
        int effectiveLimit = limit > 0 ? Math.min(limit, 1000) : DEFAULT_TX_LIST_LIMIT;
        if (userId != null && !userId.isBlank()) {
            return walletTransactionRepository.findAllByUserId(userId, type, from, to, effectiveLimit);
        }
        return walletTransactionRepository.findAll(type, from, to, effectiveLimit);
    }

    // ─── Dashboard ───────────────────────────────────────────────────────────

    public Map<String, Object> getDashboardStats() {
        long totalUsers = userAccountRepository.countAll();
        long blockedUsers = userAccountRepository.countByBlocked(true);
        long activeUsers = Math.max(0, totalUsers - blockedUsers);
        double totalWalletBalance = userAccountRepository.sumWalletBalance();

        long totalTransactions = walletTransactionRepository.countAll(null, null, null);
        long creditTransactions = walletTransactionRepository.countAll("CREDIT", null, null);
        long debitTransactions = walletTransactionRepository.countAll("DEBIT", null, null);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", totalUsers);
        stats.put("activeUsers", activeUsers);
        stats.put("blockedUsers", blockedUsers);
        stats.put("walletCount", totalUsers);
        stats.put("totalWalletBalance", UserService.round2(totalWalletBalance));
        stats.put("totalTransactions", totalTransactions);
        stats.put("creditTransactions", creditTransactions);
        stats.put("debitTransactions", debitTransactions);
        return stats;
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }
}
