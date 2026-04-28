package pro.alx4j.jab4j.support;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Small immutable-collection helpers used by shared model code.
 */
public final class ImmutableCollections {

    private ImmutableCollections() {
    }

    /**
     * Returns an immutable list copy of the source.
     *
     * @param source source list
     * @param <T> element type
     * @return immutable list copy
     */
    public static <T> List<T> listCopyOf(List<T> source) {
        return List.copyOf(source == null ? List.of() : source);
    }

    /**
     * Returns an immutable set copy of the source.
     *
     * @param source source set
     * @param <T> element type
     * @return immutable set copy
     */
    public static <T> Set<T> setCopyOf(Set<T> source) {
        return Set.copyOf(source == null ? Set.of() : source);
    }

    /**
     * Returns an immutable map copy of the source.
     *
     * @param source source map
     * @param <K> key type
     * @param <V> value type
     * @return immutable map copy
     */
    public static <K, V> Map<K, V> mapCopyOf(Map<K, V> source) {
        return Map.copyOf(source == null ? Map.of() : source);
    }
}
