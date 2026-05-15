package com.alx4j.jab4j.reader.capture.media.cv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.cv.legacy.LegacyCaptureMediaCvBackend;
import com.alx4j.jab4j.reader.capture.media.normalize.CaptureMediaFrameNormalizer;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.normalize.MediaNormalizationResult;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;

@DisplayName("Capture-media CV backend boundary")
class CaptureMediaCvBackendContractTest {

    private static final String CV_PACKAGE = "com.alx4j.jab4j.reader.capture.media.cv";
    private static final Pattern EXPORTED_PACKAGE = Pattern.compile("exports\\s+([a-zA-Z0-9_.]+)");
    private static final LayoutProfile TEST_PROFILE = new LayoutProfile(
            "fake-cv-layout",
            1,
            1,
            4,
            3,
            0,
            0,
            "solidWhite",
            0,
            0,
            "black",
            "preserveAspect"
    );

    @Test
    @DisplayName("Explicit backend is not invoked for exact supported rendered dimensions")
    void explicitBackendIsNotInvokedForExactSupportedRenderedDimensions() {
        FakeBackend backend = new FakeBackend(CvDetectionResult.backendFailure(
                Map.of("shouldNotRun", 1.0d),
                "Fake backend should not be called"
        ));
        CaptureMediaFrameNormalizer normalizer = new CaptureMediaFrameNormalizer(backend);
        MediaInputFrame inputFrame = mediaFrame("exact-rendered.png", 1280, 720, 0xFF000000);

        MediaNormalizationResult result = normalizer.normalize(inputFrame);

        NormalizedCaptureFrame accepted = result.frame().orElseThrow();
        assertAll(
                () -> assertEquals(0, backend.calls.get()),
                () -> assertTrue(result.accepted()),
                () -> assertTrue(result.diagnostics().isEmpty()),
                () -> assertEquals("debug-low-density", accepted.layoutProfileId()),
                () -> assertEquals(1280, accepted.normalizedWidthPixels()),
                () -> assertEquals(720, accepted.normalizedHeightPixels())
        );
    }

    @Test
    @DisplayName("Explicit backend accepted normalized frames are converted to normalizer output")
    void explicitBackendAcceptedNormalizedFramesAreConvertedToNormalizerOutput() {
        int[] normalizedPixels = {
                0xFF000000, 0xFF000001, 0xFF000002, 0xFF000003,
                0xFF000004, 0xFF000005, 0xFF000006, 0xFF000007,
                0xFF000008, 0xFF000009, 0xFF00000A, 0xFF00000B
        };
        CvNormalizedFrame normalizedFrame = new CvNormalizedFrame(
                TEST_PROFILE,
                new FrameCorners(1.0d, 1.0d, 5.0d, 1.0d, 5.0d, 4.0d, 1.0d, 4.0d),
                CaptureMediaQualityMetrics.perspectiveCorrected(0.25d, 0.10d),
                normalizedPixels
        );
        FakeBackend backend = new FakeBackend(CvDetectionResult.acceptedNormalizedFrames(List.of(normalizedFrame)));
        CaptureMediaFrameNormalizer normalizer = new CaptureMediaFrameNormalizer(backend);
        MediaInputFrame inputFrame = mediaFrame("backend-normalized.png", 8, 6, 0xFF222222);

        MediaNormalizationResult result = normalizer.normalize(inputFrame);

        NormalizedCaptureFrame accepted = result.frame().orElseThrow();
        normalizedPixels[0] = 0xFFFFFFFF;
        assertAll(
                () -> assertEquals(1, backend.calls.get()),
                () -> assertSame(inputFrame, backend.lastFrame),
                () -> assertTrue(result.accepted()),
                () -> assertEquals(1, result.frames().size()),
                () -> assertTrue(result.diagnostics().isEmpty()),
                () -> assertEquals("backend-normalized.png", accepted.sourceId()),
                () -> assertEquals(CaptureMediaSourceKind.STILL_IMAGE_FILE, accepted.sourceKind()),
                () -> assertEquals(8, accepted.originalWidthPixels()),
                () -> assertEquals(6, accepted.originalHeightPixels()),
                () -> assertEquals(4, accepted.normalizedWidthPixels()),
                () -> assertEquals(3, accepted.normalizedHeightPixels()),
                () -> assertEquals("fake-cv-layout", accepted.layoutProfileId()),
                () -> assertEquals(0.25d, accepted.qualityMetrics().frameCoverageRatio()),
                () -> assertEquals(0.10d, accepted.qualityMetrics().skewScore()),
                () -> assertEquals(0xFF000000, accepted.argbPixelAt(0, 0)),
                () -> assertEquals(0xFF00000B, accepted.argbPixelAt(2, 3))
        );
    }

    @Test
    @DisplayName("Explicit backend accepted candidates are perspective resampled by the normalizer")
    void explicitBackendAcceptedCandidatesArePerspectiveResampledByNormalizer() {
        int[] sourcePixels = {
                0xFF000000, 0xFF000001, 0xFF000002, 0xFF000003,
                0xFF000004, 0xFF000005, 0xFF000006, 0xFF000007,
                0xFF000008, 0xFF000009, 0xFF00000A, 0xFF00000B
        };
        CvFrameCandidate candidate = new CvFrameCandidate(
                TEST_PROFILE,
                FrameCorners.exactFrame(4, 3),
                0,
                0,
                4,
                3,
                candidateScore(0.75d, 1.0d, 0.0d)
        );
        FakeBackend backend = new FakeBackend(CvDetectionResult.acceptedCandidates(List.of(candidate)));
        CaptureMediaFrameNormalizer normalizer = new CaptureMediaFrameNormalizer(backend);
        MediaInputFrame inputFrame = new MediaInputFrame(
                "backend-candidate.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                4,
                3,
                "png",
                "source-hash",
                sourcePixels
        );

        MediaNormalizationResult result = normalizer.normalize(inputFrame);

        NormalizedCaptureFrame accepted = result.frame().orElseThrow();
        assertAll(
                () -> assertEquals(1, backend.calls.get()),
                () -> assertTrue(result.accepted()),
                () -> assertEquals("fake-cv-layout", accepted.layoutProfileId()),
                () -> assertEquals(FrameCorners.exactFrame(4, 3), accepted.frameCorners()),
                () -> assertEquals(1.0d, accepted.qualityMetrics().frameCoverageRatio()),
                () -> assertEquals(0.0d, accepted.qualityMetrics().skewScore()),
                () -> assertEquals(0xFF000000, accepted.argbPixelAt(0, 0)),
                () -> assertEquals(0xFF00000B, accepted.argbPixelAt(2, 3))
        );
    }

    @Test
    @DisplayName("Explicit backend rejected result becomes a blocking media diagnostic")
    void explicitBackendRejectedResultBecomesBlockingMediaDiagnostic() {
        FakeBackend backend = new FakeBackend(CvDetectionResult.rejected(
                CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT,
                Map.of("paletteDrift", 0.42d),
                "Fake backend rejected frame"
        ));
        CaptureMediaFrameNormalizer normalizer = new CaptureMediaFrameNormalizer(backend);

        MediaNormalizationResult result = normalizer.normalize(mediaFrame("backend-rejected.png", 8, 6, 0xFF101010));

        CaptureMediaDiagnostic diagnostic = result.diagnostics().get(0);
        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertEquals(1, backend.calls.get()),
                () -> assertEquals(CaptureMediaDiagnosticCode.COLOR_OR_COMPRESSION_SHIFT, diagnostic.code()),
                () -> assertTrue(diagnostic.blocking()),
                () -> assertEquals("backend-rejected.png", diagnostic.sourceId().orElseThrow()),
                () -> assertEquals(0.42d, diagnostic.metrics().get("paletteDrift")),
                () -> assertEquals(0.0d, diagnostic.metrics().get("detectedCandidateCount")),
                () -> assertEquals("Fake backend rejected frame", diagnostic.message())
        );
    }

    @Test
    @DisplayName("Explicit backend ambiguous result becomes ambiguous-sessions diagnostic")
    void explicitBackendAmbiguousResultBecomesAmbiguousSessionsDiagnostic() {
        CvFrameCandidate candidate = new CvFrameCandidate(
                TEST_PROFILE,
                FrameCorners.exactFrame(4, 3),
                0,
                0,
                4,
                3,
                candidateScore(0.62d, 0.70d, 0.05d)
        );
        FakeBackend backend = new FakeBackend(CvDetectionResult.ambiguous(
                List.of(candidate),
                Map.of("ambiguousCandidateRatio", 0.95d),
                "Fake backend found ambiguous candidates"
        ));
        CaptureMediaFrameNormalizer normalizer = new CaptureMediaFrameNormalizer(backend);

        MediaNormalizationResult result = normalizer.normalize(mediaFrame("backend-ambiguous.png", 8, 6, 0xFF101010));

        CaptureMediaDiagnostic diagnostic = result.diagnostics().get(0);
        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertEquals(1, backend.calls.get()),
                () -> assertEquals(CaptureMediaDiagnosticCode.AMBIGUOUS_SESSIONS, diagnostic.code()),
                () -> assertEquals(1.0d, diagnostic.metrics().get("detectedCandidateCount")),
                () -> assertEquals(0.62d, diagnostic.metrics().get("candidateScore")),
                () -> assertEquals(0.95d, diagnostic.metrics().get("ambiguousCandidateRatio")),
                () -> assertEquals("Fake backend found ambiguous candidates", diagnostic.message())
        );
    }

    @Test
    @DisplayName("Explicit backend too-small result becomes monitor-too-small diagnostic")
    void explicitBackendTooSmallResultBecomesMonitorTooSmallDiagnostic() {
        CvFrameCandidate candidate = new CvFrameCandidate(
                TEST_PROFILE,
                FrameCorners.exactFrame(4, 3),
                0,
                0,
                4,
                3,
                candidateScore(0.51d, 0.05d, 0.0d)
        );
        FakeBackend backend = new FakeBackend(CvDetectionResult.tooSmall(
                List.of(candidate),
                Map.of("minimumCoverageRatio", 0.20d),
                "Fake backend frame candidate is too small"
        ));
        CaptureMediaFrameNormalizer normalizer = new CaptureMediaFrameNormalizer(backend);

        MediaNormalizationResult result = normalizer.normalize(mediaFrame("backend-too-small.png", 8, 6, 0xFF101010));

        CaptureMediaDiagnostic diagnostic = result.diagnostics().get(0);
        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertEquals(1, backend.calls.get()),
                () -> assertEquals(CaptureMediaDiagnosticCode.MONITOR_TOO_SMALL, diagnostic.code()),
                () -> assertEquals(1.0d, diagnostic.metrics().get("detectedCandidateCount")),
                () -> assertEquals(0.05d, diagnostic.metrics().get("frameCoverageRatio")),
                () -> assertEquals(0.20d, diagnostic.metrics().get("minimumCoverageRatio")),
                () -> assertEquals("Fake backend frame candidate is too small", diagnostic.message())
        );
    }

    @Test
    @DisplayName("Explicit backend failure result becomes unreadable-media diagnostic")
    void explicitBackendFailureResultBecomesUnreadableMediaDiagnostic() {
        FakeBackend backend = new FakeBackend(CvDetectionResult.backendFailure(
                Map.of("backendFailureCount", 1.0d),
                "Fake backend failed without exposing vendor details"
        ));
        CaptureMediaFrameNormalizer normalizer = new CaptureMediaFrameNormalizer(backend);

        MediaNormalizationResult result = normalizer.normalize(mediaFrame("backend-failure.png", 8, 6, 0xFF101010));

        CaptureMediaDiagnostic diagnostic = result.diagnostics().get(0);
        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertEquals(1, backend.calls.get()),
                () -> assertEquals(CaptureMediaDiagnosticCode.UNREADABLE_MEDIA, diagnostic.code()),
                () -> assertEquals(1.0d, diagnostic.metrics().get("backendFailureCount")),
                () -> assertEquals("Fake backend failed without exposing vendor details", diagnostic.message())
        );
    }

    @Test
    @DisplayName("Explicit backend runtime failure becomes unreadable-media diagnostic")
    void explicitBackendRuntimeFailureBecomesUnreadableMediaDiagnostic() {
        CaptureMediaCvBackend backend = frame -> {
            throw new IllegalStateException("vendor-specific failure");
        };
        CaptureMediaFrameNormalizer normalizer = new CaptureMediaFrameNormalizer(backend);

        MediaNormalizationResult result = normalizer.normalize(mediaFrame("backend-throws.png", 8, 6, 0xFF101010));

        CaptureMediaDiagnostic diagnostic = result.diagnostics().get(0);
        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertEquals(CaptureMediaDiagnosticCode.UNREADABLE_MEDIA, diagnostic.code()),
                () -> assertEquals("Capture-media CV backend failed while evaluating the frame", diagnostic.message()),
                () -> assertTrue(diagnostic.metrics().isEmpty())
        );
    }

    @Test
    @DisplayName("Legacy backend exposes stable identity metadata")
    void legacyBackendExposesStableIdentityMetadata() {
        CvBackendIdentity identity = new LegacyCaptureMediaCvBackend().identity();

        assertAll(
                () -> assertEquals("legacy", identity.backendId()),
                () -> assertEquals(Optional.of("com.alx4j:jab4j-reader"), identity.implementationArtifact()),
                () -> assertTrue(identity.featureFlags().contains("generated-perspective-detection")),
                () -> assertTrue(identity.featureFlags().contains("camera-like-candidate-ranking")),
                () -> assertTrue(identity.featureFlags().contains("legacy-perspective-resampling"))
        );
    }

    @Test
    @DisplayName("Reader module does not export internal CV packages")
    void readerModuleDoesNotExportInternalCvPackages() throws IOException {
        String moduleInfo = Files.readString(mainSourceRoot().resolve("module-info.java"));

        assertAll(
                () -> assertFalse(moduleInfo.contains("exports " + CV_PACKAGE), moduleInfo),
                () -> assertFalse(moduleInfo.contains("exports " + CV_PACKAGE + "."), moduleInfo)
        );
    }

    @Test
    @DisplayName("Production reader code does not import BoofCV")
    void productionReaderCodeDoesNotImportBoofCv() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> sourceFiles = Files.walk(mainSourceRoot())) {
            sourceFiles
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> recordBoofCvImportViolation(path, violations));
        }

        assertTrue(violations.isEmpty(), () -> "Production BoofCV imports found: " + violations);
    }

    @Test
    @DisplayName("Exported reader packages do not reference internal CV DTOs")
    void exportedReaderPackagesDoNotReferenceInternalCvDtos() throws IOException {
        Path mainSourceRoot = mainSourceRoot();
        String moduleInfo = Files.readString(mainSourceRoot.resolve("module-info.java"));
        List<String> violations = new ArrayList<>();
        Matcher matcher = EXPORTED_PACKAGE.matcher(moduleInfo);
        while (matcher.find()) {
            Path packagePath = mainSourceRoot.resolve(matcher.group(1).replace('.', '/'));
            if (!Files.isDirectory(packagePath)) {
                continue;
            }
            try (Stream<Path> packageFiles = Files.list(packagePath)) {
                packageFiles
                        .filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> recordInternalCvReferenceViolation(path, violations));
            }
        }

        assertTrue(violations.isEmpty(), () -> "Exported package references internal CV DTOs: " + violations);
    }

    private static CvCandidateScore candidateScore(double totalScore, double coverageRatio, double skewScore) {
        return new CvCandidateScore(
                totalScore,
                coverageRatio,
                skewScore,
                0.80d,
                0.80d,
                0.80d,
                0.80d,
                CvCandidateScore.NOT_MEASURED,
                CvCandidateScore.NOT_MEASURED,
                CvCandidateScore.NOT_MEASURED
        );
    }

    private static MediaInputFrame mediaFrame(String sourceId, int widthPixels, int heightPixels, int argb) {
        int[] pixels = new int[widthPixels * heightPixels];
        for (int index = 0; index < pixels.length; index++) {
            pixels[index] = argb;
        }
        return new MediaInputFrame(
                sourceId,
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                widthPixels,
                heightPixels,
                "png",
                "source-hash",
                Optional.of(100L),
                Optional.of(2L),
                pixels
        );
    }

    private static Path mainSourceRoot() {
        Path moduleSourceRoot = Path.of("src", "main", "java");
        if (Files.isDirectory(moduleSourceRoot)) {
            return moduleSourceRoot;
        }
        return Path.of("modules", "jab4j-reader", "src", "main", "java");
    }

    private static void recordBoofCvImportViolation(Path path, List<String> violations) {
        try {
            String source = Files.readString(path);
            if (source.contains("import boofcv.")
                    || source.contains("import org.boofcv.")
                    || source.contains("requires boofcv")) {
                violations.add(path.toString());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect " + path, exception);
        }
    }

    private static void recordInternalCvReferenceViolation(Path path, List<String> violations) {
        try {
            String source = Files.readString(path);
            if (source.contains(CV_PACKAGE)) {
                violations.add(path.toString());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect " + path, exception);
        }
    }

    private static final class FakeBackend implements CaptureMediaCvBackend {

        private final CvDetectionResult result;
        private final AtomicInteger calls = new AtomicInteger();
        private MediaInputFrame lastFrame;

        private FakeBackend(CvDetectionResult result) {
            this.result = result;
        }

        @Override
        public CvDetectionResult detect(MediaInputFrame frame) {
            calls.incrementAndGet();
            lastFrame = frame;
            return result;
        }
    }
}
