package pro.alx4j.jab4j.catalog;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One declared input root for deterministic packaging.
 *
 * @param path filesystem path to a root directory
 * @param alias optional explicit logical root alias
 */
public record DeclaredInputRoot(Path path, String alias) {

    /**
     * Creates a validated input-root declaration.
     *
     * @param path filesystem root path
     * @param alias optional explicit alias
     */
    public DeclaredInputRoot {
        Objects.requireNonNull(path, "path must not be null");
        if (alias != null && alias.isBlank()) {
            throw new PackagingException("Declared root alias must not be blank when provided");
        }
    }
}
