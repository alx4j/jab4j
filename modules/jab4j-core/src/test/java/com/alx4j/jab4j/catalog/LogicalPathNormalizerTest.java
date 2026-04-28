package com.alx4j.jab4j.catalog;

import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Logical path normalization")
class LogicalPathNormalizerTest {

    @Test
    @DisplayName("Normalizes dot segments into forward-slash logical paths")
    void normalizesDotSegmentsIntoForwardSlashPaths() {
        assertEquals("alpha/beta/gamma", LogicalPathNormalizer.normalize(Path.of("alpha//beta/./gamma")));
    }

    @Test
    @DisplayName("Rejects relative escapes outside the logical root")
    void rejectsRelativeEscapes() {
        PackagingException exception =
                assertThrows(PackagingException.class, () -> LogicalPathNormalizer.normalize(Path.of("..", "escape.txt")));

        assertEquals("Illegal normalized logical path: ../escape.txt", exception.getMessage());
    }
}
