package com.example.tempp.repository;

import com.example.tempp.model.WalletTransaction;
import com.google.cloud.firestore.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Firebase Firestore repository for {@link WalletTransaction} (wallet audit ledger).
 * Sub-collection: {@code users/{userId}/wallet_transactions}
 */
@Slf4j
@Repository
public class WalletTransactionRepository extends BaseFirebaseRepository {

    private static final String USERS = "users";
    private static final String TX_COLLECTION = "wallet_transactions";

    private final Firestore db;

    public WalletTransactionRepository(Firestore db) {
        this.db = db;
    }

    public WalletTransaction save(WalletTransaction tx) {
        try {
            if (tx.getId() == null || tx.getId().isBlank()) {
                tx.setId(UUID.randomUUID().toString());
            }
            if (tx.getCreatedAt() == null) {
                tx.setCreatedAt(Instant.now());
            }
            db.collection(USERS).document(tx.getUserId()).collection(TX_COLLECTION).document(tx.getId()).set(toMap(tx)).get();
            return tx;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase save WalletTransaction failed", e);
        }
    }

    /**
     * Save within an existing Firestore transaction (for atomic wallet deductions).
     */
    public void saveInTransaction(Transaction firestoreTx, WalletTransaction tx, Firestore db) {
        if (tx.getId() == null || tx.getId().isBlank()) {
            tx.setId(UUID.randomUUID().toString());
        }
        if (tx.getCreatedAt() == null) {
            tx.setCreatedAt(Instant.now());
        }
        DocumentReference ref = db.collection(USERS).document(tx.getUserId()).collection(TX_COLLECTION).document(tx.getId());
        firestoreTx.set(ref, toMap(tx));
    }

    public boolean existsByReferenceId(String userId, String referenceId) {
        try {
            QuerySnapshot qs = db.collection(USERS).document(userId).collection(TX_COLLECTION).whereEqualTo("referenceId", referenceId).limit(1).get().get();
            return !qs.isEmpty();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase existsByReferenceId failed", e);
        }
    }

    // ─── Admin listing & aggregates ────────────────────────────────────────────

    /**
     * Ledger entries for a single user (sub-collection query – no composite index needed).
     */
    public List<WalletTransaction> findAllByUserId(String userId, String type, Instant from, Instant to, int limit) {
        try {
            Query query = applyFilters(db.collection(USERS).document(userId).collection(TX_COLLECTION), type, from, to)
                    .orderBy("createdAt", Query.Direction.DESCENDING).limit(limit);
            QuerySnapshot qs = query.get().get();
            List<WalletTransaction> result = new ArrayList<>();
            for (DocumentSnapshot doc : qs.getDocuments()) {
                result.add(fromDoc(doc, userId));
            }
            return result;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findAllByUserId WalletTransaction failed", e);
        }
    }

    /**
     * Ledger entries across all users via a Firestore collection-group query on
     * {@code wallet_transactions}. Filtering by type and/or date range together with
     * {@code orderBy(createdAt)} may require a one-time Firestore composite index –
     * Firestore surfaces a direct "create index" console link in the error if so.
     */
    public List<WalletTransaction> findAll(String type, Instant from, Instant to, int limit) {
        try {
            Query query = applyFilters(db.collectionGroup(TX_COLLECTION), type, from, to)
                    .orderBy("createdAt", Query.Direction.DESCENDING).limit(limit);
            QuerySnapshot qs = query.get().get();
            List<WalletTransaction> result = new ArrayList<>();
            for (DocumentSnapshot doc : qs.getDocuments()) {
                result.add(fromDoc(doc, extractUserId(doc)));
            }
            return result;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findAll WalletTransaction failed", e);
        }
    }

    public long countAll(String type, Instant from, Instant to) {
        try {
            Query query = applyFilters(db.collectionGroup(TX_COLLECTION), type, from, to);
            AggregateQuerySnapshot snap = query.count().get().get();
            return snap.getCount();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase countAll WalletTransaction failed", e);
        }
    }

    private Query applyFilters(Query query, String type, Instant from, Instant to) {
        Query result = query;
        if (type != null && !type.isBlank()) {
            result = result.whereEqualTo("type", type.trim().toUpperCase());
        }
        if (from != null) {
            result = result.whereGreaterThanOrEqualTo("createdAt", Date.from(from));
        }
        if (to != null) {
            result = result.whereLessThanOrEqualTo("createdAt", Date.from(to));
        }
        return result;
    }

    /**
     * Derives the owning user's document ID from a collection-group query result
     * (path shape: {@code users/{userId}/wallet_transactions/{id}}).
     */
    private String extractUserId(DocumentSnapshot doc) {
        DocumentReference parent = doc.getReference().getParent().getParent();
        return parent != null ? parent.getId() : null;
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private Map<String, Object> toMap(WalletTransaction t) {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", t.getUserId());
        m.put("type", t.getType());
        m.put("amount", t.getAmount());
        m.put("balanceBefore", t.getBalanceBefore());
        m.put("balanceAfter", t.getBalanceAfter());
        m.put("reason", t.getReason());
        m.put("referenceId", t.getReferenceId());
        m.put("createdAt", fromInstant(t.getCreatedAt()));
        return m;
    }

    public WalletTransaction fromDoc(DocumentSnapshot doc, String userId) {
        WalletTransaction t = new WalletTransaction();
        t.setId(doc.getId());
        t.setUserId(userId);
        t.setType(getString(doc, "type"));
        t.setAmount(getDouble(doc, "amount", 0.0));
        t.setBalanceBefore(getDouble(doc, "balanceBefore", 0.0));
        t.setBalanceAfter(getDouble(doc, "balanceAfter", 0.0));
        t.setReason(getString(doc, "reason"));
        t.setReferenceId(getString(doc, "referenceId"));
        t.setCreatedAt(toInstant(doc.get("createdAt")));
        return t;
    }
}
