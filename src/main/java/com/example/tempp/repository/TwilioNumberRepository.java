package com.example.tempp.repository;

import com.example.tempp.constants.TwilioNumberStatus;
import com.example.tempp.model.TwilioNumber;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Firebase Firestore repository for {@link TwilioNumber}.
 * Collection: {@code twilio_numbers}.
 * Allocation uses Firestore transactions for race-condition safety.
 */
@Slf4j
@Repository
public class TwilioNumberRepository extends BaseFirebaseRepository {

    private static final String COLLECTION = "twilio_numbers";
    private final Firestore db;

    public TwilioNumberRepository(Firestore db) {
        this.db = db;
    }

    // ─── Read ────────────────────────────────────────────────────────────────

    public Optional<TwilioNumber> findById(String id) {
        try {
            DocumentSnapshot doc = db.collection(COLLECTION).document(id).get().get();
            return doc.exists() ? Optional.of(fromDoc(doc)) : Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findById TwilioNumber failed", e);
        }
    }

    public Optional<TwilioNumber> findByPhoneNumber(String phoneNumber) {
        try {
            QuerySnapshot qs = db.collection(COLLECTION).whereEqualTo("phoneNumber", phoneNumber).limit(1).get().get();
            return qs.isEmpty() ? Optional.empty() : Optional.of(fromDoc(qs.getDocuments().get(0)));
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findByPhoneNumber failed", e);
        }
    }

    public Optional<TwilioNumber> findByCurrentCallSid(String callSid) {
        try {
            QuerySnapshot qs = db.collection(COLLECTION).whereEqualTo("currentCallSid", callSid).limit(1).get().get();
            return qs.isEmpty() ? Optional.empty() : Optional.of(fromDoc(qs.getDocuments().get(0)));
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findByCurrentCallSid failed", e);
        }
    }

    public List<TwilioNumber> findAll() {
        try {
            return db.collection(COLLECTION).get().get().getDocuments().stream().map(this::fromDoc).collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findAll TwilioNumber failed", e);
        }
    }

    public List<TwilioNumber> findByIsActiveTrue() {
        try {
            QuerySnapshot qs = db.collection(COLLECTION).whereEqualTo("isActive", true).get().get();
            return qs.getDocuments().stream().map(this::fromDoc).collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findByIsActiveTrue failed", e);
        }
    }

    public List<TwilioNumber> findByInUseTrue() {
        try {
            QuerySnapshot qs = db.collection(COLLECTION).whereEqualTo("inUse", true).get().get();
            return qs.getDocuments().stream().map(this::fromDoc).collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findByInUseTrue failed", e);
        }
    }

    public long countByInUseTrue() {
        return findByInUseTrue().size();
    }

    // ─── Write ───────────────────────────────────────────────────────────────

    public TwilioNumber save(TwilioNumber number) {
        try {
            Instant now = Instant.now();
            if (number.getId() == null || number.getId().isBlank()) {
                number.setId(UUID.randomUUID().toString());
                number.setCreatedAt(now);
            }
            number.setUpdatedAt(now);
            db.collection(COLLECTION).document(number.getId()).set(toMap(number)).get();
            return number;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase save TwilioNumber failed", e);
        }
    }

    public void delete(TwilioNumber number) {
        try {
            db.collection(COLLECTION).document(number.getId()).delete().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase delete TwilioNumber failed", e);
        }
    }

    /**
     * Atomically allocate the first available number.
     * Returns the allocated number or empty if none available.
     *
     * <p>On successful allocation:
     * <ul>
     *   <li>Sets status to IN_USE</li>
     *   <li>Sets inUse to true</li>
     *   <li>Records currentCallSid and lastUsedAt timestamp</li>
     * </ul>
     *
     * <p>If allocation fails, no state changes are made.
     */
    public Optional<TwilioNumber> allocateFirstAvailable(String callSid, int maxConcurrent) {
        try {
            ApiFuture<Optional<TwilioNumber>> future = db.runTransaction(tx -> {
                // Fetch all active numbers under lock
                QuerySnapshot qs = db.collection(COLLECTION).whereEqualTo("isActive", true).get().get();

                long inUseCount = qs.getDocuments().stream().filter(d -> Boolean.TRUE.equals(d.getBoolean("inUse"))).count();

                if (maxConcurrent > 0 && inUseCount >= maxConcurrent) {
                    throw new IllegalStateException("Maximum concurrent calls limit reached");
                }

                // Find first not-in-use
                Optional<QueryDocumentSnapshot> candidateOpt = qs.getDocuments().stream().filter(d -> !Boolean.TRUE.equals(d.getBoolean("inUse"))).min(Comparator.comparingLong(d -> {
                    Object lu = d.get("lastUsedAt");
                    if (lu == null)
                        return Long.MIN_VALUE;
                    if (lu instanceof com.google.cloud.Timestamp ts)
                        return ts.toDate().getTime();
                    return Long.MIN_VALUE;
                }));

                if (candidateOpt.isEmpty())
                    return Optional.empty();

                QueryDocumentSnapshot candidate = candidateOpt.get();
                DocumentReference ref = candidate.getReference();
                Instant now = Instant.now();
                // Set status to IN_USE and update metadata
                tx.update(ref, Map.of(
                        "inUse", true,
                        "status", TwilioNumberStatus.IN_USE,
                        "currentCallSid", callSid != null ? callSid : "",
                        "lastUsedAt", fromInstant(now),
                        "updatedAt", fromInstant(now)
                ));
                TwilioNumber num = fromDoc(candidate);
                num.setInUse(true);
                num.setStatus(TwilioNumberStatus.IN_USE);
                num.setCurrentCallSid(callSid);
                return Optional.of(num);
            });
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof IllegalStateException ise)
                throw ise;
            throw new RuntimeException("Firebase allocateFirstAvailable failed", cause);
        }
    }

    /**
     * Atomically update callSid binding from allocationKey to real Twilio callSid.
     */
    public void bindCallSid(String allocationKey, String realCallSid) {
        try {
            db.runTransaction(tx -> {
                QuerySnapshot qs = db.collection(COLLECTION).whereEqualTo("currentCallSid", allocationKey).limit(1).get().get();
                if (!qs.isEmpty()) {
                    DocumentReference ref = qs.getDocuments().get(0).getReference();
                    tx.update(ref, Map.of("currentCallSid", realCallSid, "updatedAt", fromInstant(Instant.now())));
                }
                return null;
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            log.warn("Firebase bindCallSid failed: {}", e.getMessage());
        }
    }

    /**
     * Atomically release a number identified by callSid.
     *
     * <p>On successful release:
     * <ul>
     *   <li>Sets inUse to false</li>
     *   <li>Sets status to AVAILABLE (if active) or DISABLED (if deactivated)</li>
     *   <li>Clears currentCallSid and lastUsedAt</li>
     * </ul>
     *
     * <p>This ensures status and inUse fields are always consistent.
     * If release fails, the exception is logged but not rethrown,
     * allowing other cleanup operations to complete.
     */
    public void releaseByCallSid(String callSid) {
        try {
            db.runTransaction(tx -> {
                QuerySnapshot qs = db.collection(COLLECTION).whereEqualTo("currentCallSid", callSid).limit(1).get().get();
                if (!qs.isEmpty()) {
                    DocumentSnapshot d = qs.getDocuments().get(0);
                    boolean active = Boolean.TRUE.equals(d.getBoolean("isActive"));
                    // Reset status based on active state and set inUse to false
                    String resetStatus = active ? TwilioNumberStatus.AVAILABLE : TwilioNumberStatus.DISABLED;
                    tx.update(d.getReference(), Map.of(
                            "inUse", false,
                            "status", resetStatus,
                            "currentCallSid", FieldValue.delete(),
                            "updatedAt", fromInstant(Instant.now())
                    ));
                    log.info("Released number for callSid={}, reset status to {}", callSid, resetStatus);
                }
                return null;
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            log.warn("Firebase releaseByCallSid failed for {}: {}", callSid, e.getMessage());
        }
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    public TwilioNumber fromDoc(DocumentSnapshot doc) {
        TwilioNumber n = new TwilioNumber();
        n.setId(doc.getId());
        n.setPhoneNumber(getString(doc, "phoneNumber"));
        n.setFriendlyName(getString(doc, "friendlyName"));
        n.setActive(getBoolean(doc, "isActive", true));
        n.setInUse(getBoolean(doc, "inUse", false));
        n.setStatus(getString(doc, "status") != null ? getString(doc, "status") : TwilioNumberStatus.AVAILABLE);
        n.setCountryCode(getString(doc, "countryCode"));
        n.setDescription(getString(doc, "description"));
        n.setCurrentCallSid(getString(doc, "currentCallSid"));
        n.setLastUsedAt(toInstant(doc.get("lastUsedAt")));
        n.setCreatedAt(toInstant(doc.get("createdAt")));
        n.setUpdatedAt(toInstant(doc.get("updatedAt")));
        return n;
    }

    private Map<String, Object> toMap(TwilioNumber n) {
        Map<String, Object> m = new HashMap<>();
        m.put("phoneNumber", n.getPhoneNumber());
        m.put("friendlyName", n.getFriendlyName());
        m.put("isActive", n.isActive());
        m.put("inUse", n.isInUse());
        m.put("status", n.getStatus());
        m.put("countryCode", n.getCountryCode());
        m.put("description", n.getDescription());
        m.put("currentCallSid", n.getCurrentCallSid());
        m.put("lastUsedAt", fromInstant(n.getLastUsedAt()));
        m.put("createdAt", fromInstant(n.getCreatedAt()));
        m.put("updatedAt", fromInstant(n.getUpdatedAt()));
        return m;
    }
}
