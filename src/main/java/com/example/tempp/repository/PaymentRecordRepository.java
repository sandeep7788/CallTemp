package com.example.tempp.repository;

import com.example.tempp.model.PaymentRecord;
import com.google.cloud.firestore.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Firebase Firestore repository for {@link PaymentRecord} (Razorpay payment tracking).
 * Collection: {@code payment_records}, keyed by {@code razorpayOrderId}.
 */
@Slf4j
@Repository
public class PaymentRecordRepository extends BaseFirebaseRepository {

    private static final String COLLECTION = "payment_records";
    private final Firestore db;

    public PaymentRecordRepository(Firestore db) {
        this.db = db;
    }

    public Optional<PaymentRecord> findByOrderId(String orderId) {
        try {
            DocumentSnapshot doc = db.collection(COLLECTION).document(orderId).get().get();
            return doc.exists() ? Optional.of(fromDoc(doc)) : Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findByOrderId PaymentRecord failed", e);
        }
    }

    /**
     * Find all payment records by status (for reconciliation)
     */
    public List<PaymentRecord> findByStatus(String status) {
        try {
            QuerySnapshot querySnapshot = db.collection(COLLECTION)
                .whereEqualTo("status", status)
                .get()
                .get();
            
            List<PaymentRecord> records = new ArrayList<>();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                records.add(fromDoc(doc));
            }
            
            log.info("Found {} payment records with status: {}", records.size(), status);
            return records;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findByStatus PaymentRecord failed", e);
        }
    }

    public boolean existsByPaymentId(String paymentId) {
        try {
            QuerySnapshot razorpay = db.collection(COLLECTION).whereEqualTo("razorpayPaymentId", paymentId).limit(1).get().get();
            if (!razorpay.isEmpty()) {
                return true;
            }
            QuerySnapshot cashfree = db.collection(COLLECTION).whereEqualTo("cashfreePaymentId", paymentId).limit(1).get().get();
            return !cashfree.isEmpty();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase existsByPaymentId failed", e);
        }
    }

    /**
     * Find by orderId+userId and run an update atomically.
     */
    public Optional<PaymentRecord> findByOrderIdAndUserId(String orderId, String userId) {
        try {
            DocumentSnapshot doc = db.collection(COLLECTION).document(orderId).get().get();
            if (!doc.exists())
                return Optional.empty();
            PaymentRecord record = fromDoc(doc);
            if (!userId.equals(record.getUserId()))
                return Optional.empty();
            return Optional.of(record);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findByOrderIdAndUserId failed", e);
        }
    }

    public PaymentRecord save(PaymentRecord record) {
        try {
            Instant now = Instant.now();
            if (record.getCreatedAt() == null)
                record.setCreatedAt(now);
            record.setUpdatedAt(now);
            String docId = record.getRazorpayOrderId() != null && !record.getRazorpayOrderId().isBlank()
                    ? record.getRazorpayOrderId()
                    : record.getCashfreeOrderId();
            db.collection(COLLECTION).document(docId).set(toMap(record)).get();
            record.setId(docId);
            return record;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase save PaymentRecord failed", e);
        }
    }

    // ─── Aggregates ───────────────────────────────────────────────────────────

    public double sumPaidAmount() {
        try {
            AggregateField sumField = AggregateField.sum("amount");
            AggregateQuerySnapshot snap = db.collection(COLLECTION)
                    .whereEqualTo("status", "PAID")
                    .aggregate(sumField).get().get();
            Object sum = snap.get(sumField);
            return sum instanceof Number n ? n.doubleValue() : 0.0;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase sumPaidAmount failed", e);
        }
    }

    public double sumPaidAmountBetween(Instant from, Instant to) {
        try {
            if (from == null || to == null) return 0.0;
            AggregateField sumField = AggregateField.sum("amount");
            AggregateQuerySnapshot snap = db.collection(COLLECTION)
                    .whereEqualTo("status", "PAID")
                    .whereGreaterThanOrEqualTo("paidAt", Date.from(from))
                    .whereLessThanOrEqualTo("paidAt", Date.from(to))
                    .aggregate(sumField).get().get();
            Object sum = snap.get(sumField);
            return sum instanceof Number n ? n.doubleValue() : 0.0;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase sumPaidAmountBetween failed", e);
        }
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private PaymentRecord fromDoc(DocumentSnapshot doc) {
        PaymentRecord r = new PaymentRecord();
        r.setId(doc.getId());
        r.setUserId(getString(doc, "userId"));
        String gateway = getString(doc, "gateway");
        r.setGateway(gateway == null || gateway.isBlank() ? "razorpay" : gateway);
        String razorpayOrderId = getString(doc, "razorpayOrderId");
        r.setRazorpayOrderId(!"cashfree".equals(r.getGateway()) && (razorpayOrderId == null || razorpayOrderId.isBlank()) ? doc.getId() : razorpayOrderId);
        r.setRazorpayPaymentId(getString(doc, "razorpayPaymentId"));
        r.setRazorpaySignature(getString(doc, "razorpaySignature"));
        r.setCashfreeOrderId(getString(doc, "cashfreeOrderId"));
        r.setCashfreePaymentId(getString(doc, "cashfreePaymentId"));
        r.setAmount(getDouble(doc, "amount", 0.0));
        r.setAmountPaise((int) getLong(doc, "amountPaise", 0));
        r.setCurrency(getString(doc, "currency"));
        r.setReceipt(getString(doc, "receipt"));
        r.setStatus(getString(doc, "status"));
        r.setFailureReason(getString(doc, "failureReason"));
        r.setCreatedAt(toInstant(doc.get("createdAt")));
        r.setUpdatedAt(toInstant(doc.get("updatedAt")));
        r.setPaidAt(toInstant(doc.get("paidAt")));
        return r;
    }

    private Map<String, Object> toMap(PaymentRecord r) {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", r.getUserId());
        m.put("gateway", r.getGateway());
        m.put("razorpayOrderId", r.getRazorpayOrderId());
        m.put("razorpayPaymentId", r.getRazorpayPaymentId());
        m.put("razorpaySignature", r.getRazorpaySignature());
        m.put("cashfreeOrderId", r.getCashfreeOrderId());
        m.put("cashfreePaymentId", r.getCashfreePaymentId());
        m.put("amount", r.getAmount());
        m.put("amountPaise", r.getAmountPaise());
        m.put("currency", r.getCurrency());
        m.put("receipt", r.getReceipt());
        m.put("status", r.getStatus());
        m.put("failureReason", r.getFailureReason());
        m.put("createdAt", fromInstant(r.getCreatedAt()));
        m.put("updatedAt", fromInstant(r.getUpdatedAt()));
        m.put("paidAt", fromInstant(r.getPaidAt()));
        return m;
    }
}

