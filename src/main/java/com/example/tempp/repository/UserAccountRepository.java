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

    // ─── Admin listing & aggregates ────────────────────────────────────────────

    public List<UserAccount> findAll(int limit) {
        try {
            QuerySnapshot qs = db.collection(COLLECTION).orderBy("createdAt", Query.Direction.DESCENDING).limit(limit).get().get();
            List<UserAccount> result = new ArrayList<>();
            for (DocumentSnapshot doc : qs.getDocuments()) {
                result.add(fromDoc(doc));
            }
            return result;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findAll UserAccount failed", e);
        }
    }

    public List<UserAccount> findByBlocked(boolean blocked, int limit) {
        try {
            QuerySnapshot qs = db.collection(COLLECTION).whereEqualTo("isBlocked", blocked)
                    .orderBy("createdAt", Query.Direction.DESCENDING).limit(limit).get().get();
            List<UserAccount> result = new ArrayList<>();
            for (DocumentSnapshot doc : qs.getDocuments()) {
                result.add(fromDoc(doc));
            }
            return result;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findByBlocked UserAccount failed", e);
        }
    }

    public long countAll() {
        try {
            AggregateQuerySnapshot snap = db.collection(COLLECTION).count().get().get();
            return snap.getCount();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase countAll UserAccount failed", e);
        }
    }

    /**
     * Count users that have the given device identifier in their deviceIdentifiers array.
     */
    public long countByDeviceIdentifier(String deviceIdentifier) {
        try {
            if (deviceIdentifier == null || deviceIdentifier.isBlank()) return 0;
            AggregateQuerySnapshot snap = db.collection(COLLECTION)
                    .whereArrayContains("deviceIdentifiers", deviceIdentifier)
                    .count().get().get();
            return snap.getCount();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase countByDeviceIdentifier UserAccount failed", e);
        }
    }

    public long countByBlocked(boolean blocked) {
        try {
            AggregateQuerySnapshot snap = db.collection(COLLECTION).whereEqualTo("isBlocked", blocked).count().get().get();
            return snap.getCount();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase countByBlocked UserAccount failed", e);
        }
    }

    /**
     * Sum of {@code walletBalance} across all users, computed server-side via a
     * Firestore aggregation query (avoids loading every user document into memory).
     */
    public double sumWalletBalance() {
        try {
            AggregateField sumField = AggregateField.sum("walletBalance");
            AggregateQuerySnapshot snap = db.collection(COLLECTION).aggregate(sumField).get().get();
            Object sum = snap.get(sumField);
            return sum instanceof Number n ? n.doubleValue() : 0.0;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase sumWalletBalance failed", e);
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
        // Support both legacy single deviceIdentifier string and newer deviceIdentifiers array
        Object devicesObj = doc.get("deviceIdentifiers");
        if (devicesObj instanceof List<?> list) {
            List<String> ids = new ArrayList<>();
            for (Object item : list) if (item instanceof String s) ids.add(s);
            u.setDeviceIdentifiers(ids);
            // keep deviceIdentifier as last-known for compatibility
            if (!ids.isEmpty()) u.setDeviceIdentifier(ids.get(ids.size() - 1));
        } else {
            String single = getString(doc, "deviceIdentifier");
            u.setDeviceIdentifier(single);
            if (single != null) u.getDeviceIdentifiers().add(single);
        }
        u.setBlocked(getBoolean(doc, "isBlocked", false));
        u.setAdmin(getBoolean(doc, "isAdmin", false));
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
        // Persist deviceIdentifiers array for multi-device support. Keep legacy deviceIdentifier too for compatibility.
        m.put("deviceIdentifiers", u.getDeviceIdentifiers() == null ? new ArrayList<>() : u.getDeviceIdentifiers());
        m.put("deviceIdentifier", u.getDeviceIdentifier());
        m.put("isBlocked", u.isBlocked());
        m.put("isAdmin", u.isAdmin());
        m.put("createdAt", fromInstant(u.getCreatedAt()));
        m.put("updatedAt", fromInstant(u.getUpdatedAt()));
        return m;
    }

    @FunctionalInterface
    public interface FirestoreUserTransaction {
        UserAccount execute(UserAccount current, Transaction tx, DocumentReference ref) throws Exception;
    }
}
