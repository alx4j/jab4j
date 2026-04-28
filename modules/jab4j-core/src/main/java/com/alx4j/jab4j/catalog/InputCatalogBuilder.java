package com.alx4j.jab4j.catalog;

import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.api.model.Manifest;

/**
 * Domain-oriented entry point for building the deterministic source catalog from declared input roots.
 */
public final class InputCatalogBuilder {

    private final DeterministicPackager delegate;

    /**
     * Creates a source-catalog builder that uses the standard deterministic packaging implementation.
     */
    public InputCatalogBuilder() {
        this(new DeterministicPackager());
    }

    InputCatalogBuilder(DeterministicPackager delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    /**
     * Builds the manifest-only source catalog view for the declared roots.
     *
     * @param declaredRoots ordered declared roots
     * @return deterministic manifest snapshot
     */
    public Manifest buildManifest(List<DeclaredInputRoot> declaredRoots) {
        return delegate.buildManifest(declaredRoots);
    }

    /**
     * Builds the full source catalog including ordered source-path metadata used by later transfer planning.
     *
     * @param declaredRoots ordered declared roots
     * @return deterministic source catalog
     */
    public PackagingResult buildPackagingResult(List<DeclaredInputRoot> declaredRoots) {
        return delegate.buildPackagingResult(declaredRoots);
    }
}
