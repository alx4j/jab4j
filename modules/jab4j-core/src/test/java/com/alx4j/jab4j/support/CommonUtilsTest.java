package com.alx4j.jab4j.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Common utility helpers")
class CommonUtilsTest {

    @Test
    @DisplayName("CRC32C matches a known value")
    void crc32cMatchesKnownValue() {
        assertEquals(0xE3069283, ChecksumUtils.crc32c("123456789".getBytes()));
    }

    @Test
    @DisplayName("SHA-256 matches a known value")
    void sha256MatchesKnownValue() {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                HashingUtils.sha256Hex("abc".getBytes())
        );
    }

    @Test
    @DisplayName("Byte-order helpers round-trip ints and longs")
    void byteOrderRoundTripsIntsAndLongs() {
        byte[] bytes = new byte[12];

        ByteOrderUtils.writeIntBigEndian(bytes, 0, 0x10203040);
        ByteOrderUtils.writeLongBigEndian(bytes, 4, 0x0102030405060708L);

        assertEquals(0x10203040, ByteOrderUtils.readIntBigEndian(bytes, 0));
        assertEquals(0x0102030405060708L, ByteOrderUtils.readLongBigEndian(bytes, 4));
    }

    @Test
    @DisplayName("Stream helpers read bytes fully")
    void streamUtilsReadsFully() {
        assertArrayEquals(
                new byte[] {1, 2, 3, 4},
                StreamUtils.readFully(new ByteArrayInputStream(new byte[] {1, 2, 3, 4}))
        );
    }

    @Test
    @DisplayName("Deterministic formatting helpers stay stable")
    void deterministicFormatsAreStable() {
        Instant instant = Instant.parse("2026-03-22T00:00:00Z");

        assertAll(
                () -> assertEquals("2026-03-22T00:00:00Z", DeterministicFormats.formatInstant(instant)),
                () -> assertEquals("abc", DeterministicFormats.lowerCaseToken("AbC")),
                () -> assertEquals("0a0b", DeterministicFormats.toHex(new byte[] {0x0A, 0x0B}))
        );
    }

    @Test
    @DisplayName("Immutable collection helpers copy their input")
    void immutableCollectionsCopyInput() {
        List<String> values = new ArrayList<>(List.of("a", "b"));
        List<String> copiedValues = ImmutableCollections.listCopyOf(values);
        Map<String, Integer> copiedMap = ImmutableCollections.mapCopyOf(Map.of("a", 1));

        values.clear();

        assertAll(
                () -> assertEquals(List.of("a", "b"), copiedValues),
                () -> assertThrows(UnsupportedOperationException.class, () -> copiedValues.add("c")),
                () -> assertEquals(Map.of("a", 1), copiedMap),
                () -> assertThrows(UnsupportedOperationException.class, () -> copiedMap.put("b", 2))
        );
    }
}
