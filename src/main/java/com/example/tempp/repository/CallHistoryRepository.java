package com.example.tempp.repository;

import com.example.tempp.model.CallHistory;
import com.google.cloud.firestore.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Firebase Firestore repository for {@link CallHistory}.
 * Sub-collection: {@code users/{userId}/call_history}
 */
@Slf4j
@Repository
public class CallHistoryRepository extends BaseFirebaseRepository {

    private static final String USERS = "users";
    private static final String HISTORY = "call_history";

    private final Firestore db;

    public CallHistoryRepository(Firestore db) {
        this.db = db;
    }

    public CallHistory save(CallHistory h) {
        try {
            if (h.getId() == null || h.getId().isBlank()) {
                h.setId(UUID.randomUUID().toString());
            }
            if (h.getCreatedAt() == null)
                h.setCreatedAt(Instant.now());
            db.collection(USERS).document(h.getUserId()).collection(HISTORY).document(h.getId()).set(toMap(h)).get();
            return h;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase save CallHistory failed", e);
        }
    }

    /**
     * Save within an existing Firestore transaction.
     */
    public void saveInTransaction(Transaction tx, CallHistory h, Firestore db) {
        if (h.getId() == null || h.getId().isBlank())
            h.setId(UUID.randomUUID().toString());
        if (h.getCreatedAt() == null)
            h.setCreatedAt(Instant.now());
        DocumentReference ref = db.collection(USERS).document(h.getUserId()).collection(HISTORY).document(h.getId());
        tx.set(ref, toMap(h));
    }

    public List<CallHistory> findTop20ByUserIdOrderByCreatedAtDesc(String userId) {
        try {
            QuerySnapshot qs = db.collection(USERS).document(userId).collection(HISTORY).orderBy("createdAt", Query.Direction.DESCENDING).limit(20).get().get();
            return qs.getDocuments().stream().map(d -> fromDoc(d, userId)).collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findTop20 CallHistory failed", e);
        }
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private Map<String, Object> toMap(CallHistory h) {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", h.getUserId());
        m.put("destination", h.getDestination());
        m.put("callSid", h.getCallSid());
        m.put("twilioNumberUsed", h.getTwilioNumberUsed());
        m.put("durationSeconds", h.getDurationSeconds());
        m.put("connectedDurationSeconds", h.getConnectedDurationSeconds());
        m.put("billedMinutes", h.getBilledMinutes());
        m.put("ratePerMinute", h.getRatePerMinute());
        m.put("amountCharged", h.getAmountCharged());
        m.put("walletBefore", h.getWalletBefore());
        m.put("walletAfter", h.getWalletAfter());
        m.put("status", h.getStatus());
        m.put("startTime", fromInstant(h.getStartTime()));
        m.put("endTime", fromInstant(h.getEndTime()));
        m.put("disconnectReason", h.getDisconnectReason());
        m.put("createdAt", fromInstant(h.getCreatedAt()));
        return m;
    }

    private CallHistory fromDoc(DocumentSnapshot doc, String userId) {
        CallHistory h = new CallHistory();
        h.setId(doc.getId());
        h.setUserId(userId);
        h.setDestination(getString(doc, "destination"));
        h.setCallSid(getString(doc, "callSid"));
        h.setTwilioNumberUsed(getString(doc, "twilioNumberUsed"));
        h.setDurationSeconds(getLong(doc, "durationSeconds", 0));
        h.setConnectedDurationSeconds(getLong(doc, "connectedDurationSeconds", 0));
        h.setBilledMinutes(getLong(doc, "billedMinutes", 0));
        h.setRatePerMinute(getDouble(doc, "ratePerMinute", 0.0));
        h.setAmountCharged(getDouble(doc, "amountCharged", 0.0));
        h.setWalletBefore(getDouble(doc, "walletBefore", 0.0));
        h.setWalletAfter(getDouble(doc, "walletAfter", 0.0));
        h.setStatus(getString(doc, "status"));
        h.setStartTime(toInstant(doc.get("startTime")));
        h.setEndTime(toInstant(doc.get("endTime")));
        h.setDisconnectReason(getString(doc, "disconnectReason"));
        h.setCreatedAt(toInstant(doc.get("createdAt")));
        return h;
    }
}
