package com.alx4j.jab4j.catalog;

import java.nio.file.Path;
import java.util.Objects;

final class LogicalPathNormalizer {

    private LogicalPathNormalizer() {
    }

    static String normalize(Path relativePath) {
        Objects.requireNonNull(relativePath, "relativePath must not be null");

        Path normalized = relativePath.normalize();
        if (normalized.isAbsolute()) {
            throw new PackagingException("Logical paths must remain relative: " + relativePath);
        }
        if (normalized.getNameCount() == 0) {
            throw new PackagingException("Logical paths must not be empty");
        }

        StringBuilder builder = new StringBuilder();
        for (Path segment : normalized) {
            String token = segment.toString();
            if (token.isBlank() || ".".equals(token) || "..".equals(token)) {
                throw new PackagingException("Illegal normalized logical path: " + relativePath);
            }
            if (builder.length() > 0) {
                builder.append('/');
            }
            builder.append(token.replace('\\', '/'));
        }

        String logicalPath = builder.toString();
        if (logicalPath.startsWith("../") || logicalPath.equals("..")) {
            throw new PackagingException("Logical paths must not escape their declared root: " + relativePath);
        }
        return logicalPath;
    }
}
