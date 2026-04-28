package com.alx4j.jab4j.transfer;

import java.util.Objects;
import com.alx4j.jab4j.api.model.TransferSession;
import com.alx4j.jab4j.catalog.PackagingResult;

/**
 * Domain-oriented entry point for turning a packaged source catalog into a deterministic transfer plan.
 */
public final class TransferPlanner {

    private final TransportSessionPlanner delegate;

    /**
     * Creates a transfer planner that uses the standard deterministic transport planning implementation.
     */
    public TransferPlanner() {
        this(new TransportSessionPlanner());
    }

    TransferPlanner(TransportSessionPlanner delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    /**
     * Plans the deterministic transfer session from one prepared source catalog and session profile.
     *
     * @param draftSession transfer session with manifest-level records and profile metadata
     * @param packagingResult packaged source catalog aligned with the manifest
     * @return deterministic transfer plan
     */
    public TransportSessionPlan plan(TransferSession draftSession, PackagingResult packagingResult) {
        return delegate.plan(draftSession, packagingResult);
    }
}
