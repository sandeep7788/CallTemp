package com.example.tempp.repository;

import com.example.tempp.model.UserAccount;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Firebase Firestore repository for {@link UserAccount}.
 * Collection: {@code users}
 */
@Slf4j
@Repository
public class UserAccountRepository extends BaseFirebaseRepository {

    private static final String COLLECTION = "users";
    private final Firestore db;

    public UserAccountRepository(Firestore db) {
        this.db = db;
    }

    // ─── Read ────────────────────────────────────────────────────────────────

    public Optional<UserAccount> findById(String id) {
        try {
            DocumentSnapshot doc = db.collection(COLLECTION).document(id).get().get();
            return doc.exists() ? Optional.of(fromDoc(doc)) : Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findById failed", e);
        }
    }

    public Optional<UserAccount> findByNumber(String number) {
        try {
            QuerySnapshot qs = db.collection(COLLECTION).whereEqualTo("number", number).limit(1).get().get();
            return qs.isEmpty() ? Optional.empty() : Optional.of(fromDoc(qs.getDocuments().get(0)));
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findByNumber failed", e);
        }
    }

    public Optional<UserAccount> findByTemp(String temp) {
        try {
            QuerySnapshot qs = db.collection(COLLECTION).whereEqualTo("temp", temp).limit(1).get().get();
            return qs.isEmpty() ? Optional.empty() : Optional.of(fromDoc(qs.getDocuments().get(0)));
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findByTemp failed", e);
        }
    }

    public Optional<UserAccount> findByGoogleUid(String googleUid) {
        try {
            QuerySnapshot qs = db.collection(COLLECTION).whereEqualTo("googleUid", googleUid).limit(1).get().get();
            return qs.isEmpty() ? Optional.empty() : Optional.of(fromDoc(qs.getDocuments().get(0)));
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findByGoogleUid failed", e);
        }
    }

    // ─── Write ───────────────────────────────────────────────────────────────

    public UserAccount save(UserAccount user) {
        try {
            Instant now = Instant.now();
            if (user.getId() == null || user.getId().isBlank()) {
                user.setId(UUID.randomUUID().toString());
                user.setCreatedAt(now);
            }
            user.setUpdatedAt(now);
            db.collection(COLLECTION).document(user.getId()).set(toMap(user)).get();
            return user;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase save UserAccount failed", e);
        }
    }

    // ─── Firestore transaction variants (used for wallet operations) ─────────

    /**
     * Execute a Firestore transaction that reads and writes a UserAccount atomically.
     * The callback receives the current snapshot and returns the data to write.
     * Retries automatically on contention.
     */
    public UserAccount runTransaction(String userId, FirestoreUserTransaction txFn) {
        try {
            ApiFuture<UserAccount> future = db.runTransaction(tx -> {
                DocumentReference ref = db.collection(COLLECTION).document(userId);
                DocumentSnapshot snap = tx.get(ref).get();
                if (!snap.exists()) {
                    throw new IllegalStateException("User not found: " + userId);
                }
                UserAccount user = fromDoc(snap);
                UserAccount updated = txFn.execute(user, tx, ref);
                tx.set(ref, toMap(updated));
                return updated;
            });
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof IllegalStateException ise)
                throw ise;
            throw new RuntimeException("Firebase user transaction failed", cause);
        }
    }

    public UserAccount fromDoc(DocumentSnapshot doc) {
        UserAccount u = new UserAccount();
        u.setId(doc.getId());
        u.setGoogleUid(getString(doc, "googleUid"));
        u.setEmail(getString(doc, "email"));
        u.setName(getString(doc, "name"));
        u.setNumber(getString(doc, "number"));
        u.setLocation(getString(doc, "location"));
        u.setTemp(getString(doc, "temp"));
        u.setActive(getBoolean(doc, "active", true));
        u.setWalletBalance(getDouble(doc, "walletBalance", 0.0));
        u.setWalletRewardCredited(getBoolean(doc, "walletRewardCredited", false));
        u.setTermsAccepted(getBoolean(doc, "termsAccepted", false));
        u.setDeviceIdentifier(getString(doc, "deviceIdentifier"));
        u.setBlocked(getBoolean(doc, "isBlocked", false));
        u.setCreatedAt(toInstant(doc.get("createdAt")));
        u.setUpdatedAt(toInstant(doc.get("updatedAt")));
        return u;
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private Map<String, Object> toMap(UserAccount u) {
        Map<String, Object> m = new HashMap<>();
        m.put("googleUid", u.getGoogleUid());
        m.put("email", u.getEmail());
        m.put("name", u.getName());
        m.put("number", u.getNumber());
        m.put("location", u.getLocation());
        m.put("temp", u.getTemp());
        m.put("active", u.isActive());
        m.put("walletBalance", u.getWalletBalance());
        m.put("walletRewardCredited", u.isWalletRewardCredited());
        m.put("termsAccepted", u.isTermsAccepted());
        m.put("deviceIdentifier", u.getDeviceIdentifier());
        m.put("isBlocked", u.isBlocked());
        m.put("createdAt", fromInstant(u.getCreatedAt()));
        m.put("updatedAt", fromInstant(u.getUpdatedAt()));
        return m;
    }

    @FunctionalInterface
    public interface FirestoreUserTransaction {
        UserAccount execute(UserAccount current, Transaction tx, DocumentReference ref) throws Exception;
    }
}
