package com.alx4j.jab4j.reader.capture.media.cv;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import com.alx4j.jab4j.reader.capture.media.cv.legacy.LegacyCaptureMediaCvBackend;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.qualify.CaptureRenderedLayoutCatalog;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;

/**
 * Selects capture-media CV backends while keeping legacy behavior as the baseline.
 */
public final class CaptureMediaCvBackends {

    /**
     * JVM property used for developer/manual backend selection.
     */
    public static final String BACKEND_PROPERTY = "jab4j.capture.media.cv.backend";

    /**
     * Environment variable used for developer/manual backend selection.
     */
    public static final String BACKEND_ENVIRONMENT_VARIABLE = "JAB4J_CAPTURE_MEDIA_CV_BACKEND";

    private static final String LEGACY_BACKEND_ID = "legacy";

    private CaptureMediaCvBackends() {
    }

    /**
     * Returns the configured backend, or the legacy backend when no explicit selection is present.
     *
     * @param layoutCatalog supported rendered layout catalog
     * @param layoutPlanner fixed layout planner used for JAB geometry
     * @return selected backend
     */
    public static CaptureMediaCvBackend configuredOrLegacy(
            CaptureRenderedLayoutCatalog layoutCatalog,
            FixedLayoutPlanner layoutPlanner
    ) {
        Objects.requireNonNull(layoutCatalog, "layoutCatalog must not be null");
        Objects.requireNonNull(layoutPlanner, "layoutPlanner must not be null");
        return selectedBackendId()
                .map(backendId -> selectedBackend(
                        backendId,
                        () -> legacy(layoutCatalog, layoutPlanner)
                ))
                .orElseGet(() -> legacy(layoutCatalog, layoutPlanner));
    }

    /**
     * Creates the baseline legacy backend with default layout collaborators.
     *
     * @return legacy backend
     */
    public static CaptureMediaCvBackend legacy() {
        return new LegacyCaptureMediaCvBackend();
    }

    /**
     * Creates the baseline legacy backend.
     *
     * @param layoutCatalog supported rendered layout catalog
     * @param layoutPlanner fixed layout planner used for JAB geometry
     * @return legacy backend
     */
    public static CaptureMediaCvBackend legacy(
            CaptureRenderedLayoutCatalog layoutCatalog,
            FixedLayoutPlanner layoutPlanner
    ) {
        return new LegacyCaptureMediaCvBackend(layoutCatalog, layoutPlanner);
    }

    /**
     * Finds an explicitly selected backend from local legacy support or visible optional backend modules.
     *
     * @param backendId backend id to find
     * @return selected backend when available
     */
    public static Optional<CaptureMediaCvBackend> findExplicit(String backendId) {
        Objects.requireNonNull(backendId, "backendId must not be null");
        String normalizedBackendId = normalize(backendId);
        if (LEGACY_BACKEND_ID.equals(normalizedBackendId)) {
            return Optional.of(legacy());
        }
        for (CaptureMediaCvBackend backend : ServiceLoader.load(CaptureMediaCvBackend.class)) {
            if (normalizedBackendId.equals(normalize(backend.identity().backendId()))) {
                return Optional.of(backend);
            }
        }
        return Optional.empty();
    }

    private static CaptureMediaCvBackend selectedBackend(String backendId, Supplier<CaptureMediaCvBackend> legacy) {
        if (LEGACY_BACKEND_ID.equals(backendId)) {
            return legacy.get();
        }
        return findExplicit(backendId).orElseGet(() -> new UnavailableCaptureMediaCvBackend(backendId));
    }

    private static Optional<String> selectedBackendId() {
        return Optional.ofNullable(firstNonBlank(
                        System.getProperty(BACKEND_PROPERTY),
                        System.getenv(BACKEND_ENVIRONMENT_VARIABLE)
                ))
                .map(CaptureMediaCvBackends::normalize);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static String normalize(String backendId) {
        return backendId.trim().toLowerCase(Locale.ROOT);
    }

    private static final class UnavailableCaptureMediaCvBackend implements CaptureMediaCvBackend {

        private final String backendId;

        private UnavailableCaptureMediaCvBackend(String backendId) {
            this.backendId = Objects.requireNonNull(backendId, "backendId must not be null");
        }

        @Override
        public CvBackendIdentity identity() {
            return CvBackendIdentity.unspecified(backendId);
        }

        @Override
        public CvDetectionResult detect(MediaInputFrame frame) {
            return CvDetectionResult.backendFailure(
                    Map.of("unavailableBackendSelection", 1.0d),
                    "Capture-media CV backend '" + backendId
                            + "' is not available; add the optional backend adapter or select legacy"
            );
        }
    }
}
