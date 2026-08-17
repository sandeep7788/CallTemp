package com.example.tempp.repository;

import com.example.tempp.model.OutboundCall;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Firebase Firestore repository for {@link OutboundCall}.
 * Collection: {@code outbound_calls}, keyed by {@code publicId}.
 */
@Slf4j
@Repository
public class OutboundCallRepository extends BaseFirebaseRepository {

    private static final String COLLECTION = "outbound_calls";
    private final Firestore db;

    public OutboundCallRepository(Firestore db) {
        this.db = db;
    }

    public Optional<OutboundCall> findByPublicId(String publicId) {
        try {
            DocumentSnapshot doc = db.collection(COLLECTION).document(publicId).get().get();
            return doc.exists() ? Optional.of(fromDoc(doc)) : Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findByPublicId failed", e);
        }
    }

    public OutboundCall save(OutboundCall call) {
        try {
            Instant now = Instant.now();
            if (call.getCreatedAt() == null)
                call.setCreatedAt(now);
            call.setUpdatedAt(now);
            db.collection(COLLECTION).document(call.getPublicId()).set(toMap(call)).get();
            return call;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase save OutboundCall failed", e);
        }
    }

    /**
     * Run a Firestore transaction on an OutboundCall document.
     * Returns null if the document doesn't exist.
     */
    public OutboundCall runTransaction(String publicId, FirestoreCallTransaction txFn) {
        try {
            ApiFuture<OutboundCall> future = db.runTransaction(tx -> {
                DocumentReference ref = db.collection(COLLECTION).document(publicId);
                DocumentSnapshot snap = tx.get(ref).get();
                if (!snap.exists())
                    return null;
                OutboundCall call = fromDoc(snap);
                OutboundCall updated = txFn.execute(call, tx, ref);
                if (updated != null) {
                    tx.set(ref, toMap(updated));
                }
                return updated;
            });
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof IllegalStateException ise)
                throw ise;
            throw new RuntimeException("Firebase OutboundCall transaction failed", cause);
        }
    }

    public OutboundCall fromDoc(DocumentSnapshot doc) {
        OutboundCall c = new OutboundCall();
        c.setPublicId(doc.getId());
        c.setUserId(getString(doc, "userId"));
        c.setDestination(getString(doc, "destination"));
        c.setFromNumber(getString(doc, "fromNumber"));
        c.setCallSid(getString(doc, "callSid"));
        c.setStatus(getString(doc, "status"));
        c.setMessage(getString(doc, "message"));
        c.setTwimlRequestedAt(toInstant(doc.get("twimlRequestedAt")));
        c.setExpiresAt(toInstant(doc.get("expiresAt")));
        c.setCreatedAt(toInstant(doc.get("createdAt")));
        c.setUpdatedAt(toInstant(doc.get("updatedAt")));
        return c;
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private Map<String, Object> toMap(OutboundCall c) {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", c.getUserId());
        m.put("destination", c.getDestination());
        m.put("fromNumber", c.getFromNumber());
        m.put("callSid", c.getCallSid());
        m.put("status", c.getStatus());
        m.put("message", c.getMessage());
        m.put("twimlRequestedAt", fromInstant(c.getTwimlRequestedAt()));
        m.put("expiresAt", fromInstant(c.getExpiresAt()));
        m.put("createdAt", fromInstant(c.getCreatedAt()));
        m.put("updatedAt", fromInstant(c.getUpdatedAt()));
        return m;
    }

    @FunctionalInterface
    public interface FirestoreCallTransaction {
        OutboundCall execute(OutboundCall current, Transaction tx, DocumentReference ref) throws Exception;
    }
}
