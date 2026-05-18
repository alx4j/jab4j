package com.alx4j.jab4j.reader.capture.media.evidence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Shared validation and immutable-copy helpers for internal evidence records.
 */
final class EvidenceValidation {

    private EvidenceValidation() {
    }

    static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    static Optional<String> copyOptionalText(Optional<String> value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        value.ifPresent(present -> requireText(present, fieldName));
        return value.map(String::trim);
    }

    static int requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
        return value;
    }

    static int requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    static double requireFinite(double value, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
        return value;
    }

    static double requireNonNegativeFinite(double value, String fieldName) {
        requireFinite(value, fieldName);
        if (value < 0.0d) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
        return value;
    }

    static double requireUnitScore(double value, String fieldName) {
        requireFinite(value, fieldName);
        if (value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
        return value;
    }

    static <T> List<T> copyList(Collection<T> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + " must not be null");
        List<T> copied = new ArrayList<>(values);
        if (copied.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(fieldName + " must not contain null values");
        }
        return Collections.unmodifiableList(copied);
    }

    static List<CaptureMediaEvidenceReasonCode> copyReasonCodes(
            Collection<CaptureMediaEvidenceReasonCode> reasonCodes,
            String fieldName
    ) {
        List<CaptureMediaEvidenceReasonCode> copied = copyList(reasonCodes, fieldName);
        if (new LinkedHashSet<>(copied).size() != copied.size()) {
            throw new IllegalArgumentException(fieldName + " must not contain duplicate values");
        }
        return copied;
    }

    static List<String> copyTextList(Collection<String> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + " must not be null");
        List<String> copied = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            String text = requireText(value, fieldName);
            if (!seen.add(text)) {
                throw new IllegalArgumentException(fieldName + " must not contain duplicate values");
            }
            copied.add(text);
        }
        return Collections.unmodifiableList(copied);
    }

    static List<Double> copyNonNegativeDoubles(Collection<Double> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + " must not be null");
        List<Double> copied = new ArrayList<>();
        for (Double value : values) {
            if (value == null) {
                throw new IllegalArgumentException(fieldName + " must not contain null values");
            }
            copied.add(requireNonNegativeFinite(value, fieldName));
        }
        return Collections.unmodifiableList(copied);
    }

    static List<Double> copyFiniteDoubles(Collection<Double> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + " must not be null");
        List<Double> copied = new ArrayList<>();
        for (Double value : values) {
            if (value == null) {
                throw new IllegalArgumentException(fieldName + " must not contain null values");
            }
            copied.add(requireFinite(value, fieldName));
        }
        return Collections.unmodifiableList(copied);
    }

    static Map<String, Double> copyMetricMap(Map<String, Double> metrics, String fieldName) {
        Objects.requireNonNull(metrics, fieldName + " must not be null");
        Map<String, Double> copied = new LinkedHashMap<>();
        metrics.forEach((name, value) -> {
            String copiedName = requireText(name, fieldName + " name");
            if (value == null) {
                throw new IllegalArgumentException(fieldName + " values must not be null");
            }
            copied.put(copiedName, requireFinite(value, fieldName + " value"));
        });
        return Collections.unmodifiableMap(copied);
    }

    static Map<String, Double> copyScoreMap(Map<String, Double> scores, String fieldName) {
        Objects.requireNonNull(scores, fieldName + " must not be null");
        Map<String, Double> copied = new LinkedHashMap<>();
        scores.forEach((name, value) -> {
            String copiedName = requireText(name, fieldName + " name");
            if (value == null) {
                throw new IllegalArgumentException(fieldName + " values must not be null");
            }
            copied.put(copiedName, requireUnitScore(value, fieldName + " value"));
        });
        return Collections.unmodifiableMap(copied);
    }
}

