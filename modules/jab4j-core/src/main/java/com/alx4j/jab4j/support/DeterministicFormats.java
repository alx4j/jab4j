package com.alx4j.jab4j.support;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Time-independent formatting helpers for deterministic artifacts and logs.
 */
public final class DeterministicFormats {

    private static final DateTimeFormatter INSTANT_FORMATTER =
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private DeterministicFormats() {
    }

    /**
     * Formats an instant in UTC ISO-8601 form.
     *
     * @param instant instant to format
     * @return UTC ISO-8601 string
     */
    public static String formatInstant(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("instant must not be null");
        }
        return INSTANT_FORMATTER.format(instant);
    }

    /**
     * Returns a lowercase, locale-stable identifier token.
     *
     * @param value value to normalize
     * @return lowercase token
     */
    public static String lowerCaseToken(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * Formats bytes as lowercase hexadecimal.
     *
     * @param bytes bytes to format
     * @return lowercase hexadecimal string
     */
    public static String toHex(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must not be null");
        }
        return HexFormat.of().formatHex(bytes);
    }
}
