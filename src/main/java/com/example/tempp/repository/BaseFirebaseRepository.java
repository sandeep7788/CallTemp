package com.example.tempp.repository;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Date;

/**
 * Shared helpers for mapping Firestore documents to domain objects.
 */
@Slf4j
public abstract class BaseFirebaseRepository {

    protected Instant toInstant(Object value) {
        if (value == null)
            return null;
        if (value instanceof Date d)
            return d.toInstant();
        // Avoid a hard compile-time dependency in this helper; Firestore Timestamp exposes toDate().
        try {
            Object date = value.getClass().getMethod("toDate").invoke(value);
            if (date instanceof Date d)
                return d.toInstant();
        } catch (ReflectiveOperationException ignored) {
            // Not a Firestore Timestamp-like object.
        }
        if (value instanceof Long l)
            return Instant.ofEpochMilli(l);
        return null;
    }

    protected Date fromInstant(Instant instant) {
        if (instant == null)
            return null;
        return Date.from(instant);
    }

    protected String getString(Object doc, String field) {
        Object value = invokeGetter(doc, "getString", field);
        return value instanceof String s ? s : null;
    }

    protected boolean getBoolean(Object doc, String field, boolean defaultValue) {
        Boolean v = (Boolean) invokeGetter(doc, "getBoolean", field);
        return v != null ? v : defaultValue;
    }

    protected double getDouble(Object doc, String field, double defaultValue) {
        Double v = (Double) invokeGetter(doc, "getDouble", field);
        return v != null ? v : defaultValue;
    }

    protected long getLong(Object doc, String field, long defaultValue) {
        Long v = (Long) invokeGetter(doc, "getLong", field);
        return v != null ? v : defaultValue;
    }

    private Object invokeGetter(Object doc, String methodName, String field) {
        try {
            return doc.getClass().getMethod(methodName, String.class).invoke(doc, field);
        } catch (ReflectiveOperationException ex) {
            log.warn("Unable to invoke {}('{}') on Firestore document snapshot: {}", methodName, field, ex.getMessage());
            return null;
        }
    }
}

