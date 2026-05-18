package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.cv.CaptureMediaCvBackend;
import com.alx4j.jab4j.reader.capture.media.cv.CaptureMediaCvBackends;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionResult;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionStatus;
import com.alx4j.jab4j.reader.capture.media.cv.CvGridPhase;
import com.alx4j.jab4j.reader.capture.media.cv.CvNormalizedFrame;
import com.alx4j.jab4j.reader.capture.media.cv.CvSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.normalize.CaptureMediaFrameNormalizer;

@DisplayName("Optional BoofCV capture-media backend")
class BoofCvCaptureMediaCvBackendTest {

    private static final Pattern IMPORT_DECLARATION = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([^;]+);\\s*$");
    private static final List<String> FORBIDDEN_PROTOCOL_IMPORTS = List.of(
            "com.alx4j.jab4j.api.model.TilePayload",
            "com.alx4j.jab4j.tile.TileDecoder",
            "com.alx4j.jab4j.transfer.TilePayloadEnvelopeCodec",
            "com.alx4j.jab4j.reader.capture.decode.",
            "com.alx4j.jab4j.reader.capture.media.decode.",
            "com.alx4j.jab4j.reader.capture.media.sample.CaptureMediaTilePayloadSampler",
            "com.alx4j.jab4j.reader.capture.restore.",
            "com.alx4j.jab4j.reader.restore."
    );

    @Test
    @DisplayName("Service loader exposes the optional BoofCV backend")
    void serviceLoaderExposesOptionalBoofCvBackend() {
        Optional<CaptureMediaCvBackend> backend = ServiceLoader.load(CaptureMediaCvBackend.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(candidate -> "boofcv".equals(candidate.identity().backendId()))
                .findFirst();

        assertAll(
                () -> assertTrue(backend.isPresent()),
                () -> assertEquals(
                        "com.alx4j:jab4j-reader-cv-boofcv",
                        backend.orElseThrow().identity().implementationArtifact().orElseThrow()
                ),
                () -> assertTrue(backend.orElseThrow().identity().featureFlags().contains("optional-shaded-adapter"))
        );
    }

    @Test
    @DisplayName("Reader selector uses optional BoofCV only after explicit developer selection")
    void readerSelectorUsesOptionalBoofCvOnlyAfterExplicitDeveloperSelection() {
        String previousBackend = System.getProperty(CaptureMediaCvBackends.BACKEND_PROPERTY);
        try {
            System.clearProperty(CaptureMediaCvBackends.BACKEND_PROPERTY);
            CaptureMediaFrameNormalizer defaultNormalizer = new CaptureMediaFrameNormalizer();
            System.setProperty(CaptureMediaCvBackends.BACKEND_PROPERTY, "legacy");
            CaptureMediaFrameNormalizer explicitlyLegacyNormalizer = new CaptureMediaFrameNormalizer();
            Optional<CaptureMediaCvBackend> backend = CaptureMediaCvBackends.findExplicit("boofcv");
            System.setProperty(CaptureMediaCvBackends.BACKEND_PROPERTY, "boofcv");
            CaptureMediaFrameNormalizer explicitlyBoofCvNormalizer = new CaptureMediaFrameNormalizer();

            assertAll(
                () -> assertTrue(backend.isPresent()),
                () -> assertEquals("boofcv", backend.orElseThrow().identity().backendId()),
                () -> assertEquals("legacy", defaultNormalizer.normalizationBackendId()),
                () -> assertEquals("legacy", explicitlyLegacyNormalizer.normalizationBackendId()),
                () -> assertEquals("boofcv", explicitlyBoofCvNormalizer.normalizationBackendId()),
                () -> assertFalse(CaptureMediaCvBackends.findExplicit("missing-backend").isPresent())
            );
        } finally {
            restoreBackendProperty(previousBackend);
        }
    }

    @Test
    @DisplayName("BoofCV backend runs a stable image probe through backend-neutral diagnostics")
    void boofCvBackendRunsStableImageProbeThroughBackendNeutralDiagnostics() {
        CvDetectionResult result = new BoofCvCaptureMediaCvBackend().detect(mediaFrame(8, 6));

        assertAll(
                () -> assertEquals(CvDetectionStatus.REJECTED, result.status()),
                () -> assertEquals(CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND,
                        result.diagnosticCode().orElseThrow()),
                () -> assertEquals(8.0d, result.metrics().get("boofCvInputWidthPixels")),
                () -> assertEquals(6.0d, result.metrics().get("boofCvInputHeightPixels")),
                () -> assertEquals(1.0d, result.metrics().get("boofCvConversionCopyCount")),
                () -> assertEquals(1.0d, result.metrics().get("boofCvGrayscaleConversionCount")),
                () -> assertTrue(result.metrics().containsKey("boofCvExternalContourCount")),
                () -> assertEquals(1.0d, result.metrics().get("boofCvSelectedBackendCode")),
                () -> assertEquals(0.0d, result.metrics().get("boofCvPreprocessingModeCode")),
                () -> assertEquals(0.0d, result.metrics().get("boofCvStrictEvidenceCandidateCount")),
                () -> assertEquals(0.0d, result.metrics().get("boofCvPlausibleValidationCandidateCount")),
                () -> assertEquals(0.0d, result.metrics().get("boofCvRejectedScoredCandidateCount")),
                () -> assertEquals(0.0d, result.metrics().get("boofCvSelectedAdmissionBandCode")),
                () -> assertEquals(80.0d, result.metrics().get("boofCvSelectedRejectionReasonCode"))
        );
    }

    @Test
    @DisplayName("Default production backend emits bounded normalized candidates for fitted generated monitor frames")
    void defaultProductionBackendEmitsBoundedNormalizedCandidatesForFittedGeneratedMonitorFrames() {
        CvDetectionResult result = new BoofCvCaptureMediaCvBackend()
                .detect(BoofCvGeneratedFixtureFactory.cameraLikeMonitorPng());
        CvNormalizedFrame normalizedFrame = result.normalizedFrames().get(0);
        CvSamplingEvidence evidence = normalizedFrame.samplingEvidence().orElseThrow();

        assertAll(
                () -> assertEquals(CvDetectionStatus.ACCEPTED, result.status()),
                () -> assertTrue(result.candidates().isEmpty()),
                () -> assertEquals(3, result.normalizedFrames().size()),
                () -> assertTrue(result.diagnosticCode().isEmpty()),
                () -> assertEquals(3.0d, result.metrics().get("boofCvAcceptedCandidateCount")),
                () -> assertEquals(3.0d, result.metrics().get("boofCvNormalizedFrameCount")),
                () -> assertEquals(3.0d, result.metrics().get("boofCvAcceptedSourceCandidateCount")),
                () -> assertEquals(1.0d, result.metrics().get("boofCvSelectedBackendCode")),
                () -> assertEquals(1.0d, result.metrics().get("boofCvPreprocessingModeCode")),
                () -> assertEquals(
                        result.metrics().get("boofCvAcceptedSourceCandidateCount"),
                        result.metrics().get("boofCvStrictEvidenceCandidateCount")
                ),
                () -> assertEquals(0.0d, result.metrics().get("boofCvPlausibleValidationCandidateCount")),
                () -> assertEquals(1.0d, result.metrics().get("boofCvSelectedAdmissionBandCode")),
                () -> assertEquals(0.0d, result.metrics().get("boofCvSelectedRejectionReasonCode")),
                () -> assertEquals("debug-low-density", normalizedFrame.layoutProfile().profileId()),
                () -> assertEquals(
                        "boofcv-fitted-quadrilateral",
                        normalizedFrame.geometrySource().orElseThrow()
                ),
                () -> assertEquals(1, normalizedFrame.sourceRegionRank()),
                () -> assertEquals(1, normalizedFrame.profileAlternativeRank()),
                () -> assertEquals(3, normalizedFrame.profileAlternativeCount()),
                () -> assertEquals(
                        "boofcv",
                        evidence.backendId()
                ),
                () -> assertEquals(CvNormalizedFrame.class, normalizedFrame.getClass()),
                () -> assertEquals(CvSamplingEvidence.class, evidence.getClass()),
                () -> assertEquals(CvGridPhase.class, evidence.gridPhase().orElseThrow().getClass()),
                () -> assertEquals(1.0d, evidence.metrics().get("boofCvSelectedBackendCode")),
                () -> assertEquals(1.0d, evidence.metrics().get("boofCvPreprocessingModeCode")),
                () -> assertTrue(evidence.metrics().containsKey("boofCvGeometrySourceCode")),
                () -> assertTrue(evidence.metrics().get("boofCvSamplingEvidenceConfidence") > 0.0d)
        );
    }

    @Test
    @DisplayName("Near-miss generated monitor evidence is admitted as bounded plausible validation")
    void nearMissGeneratedMonitorEvidenceIsAdmittedAsBoundedPlausibleValidation() {
        CvDetectionResult first = new BoofCvCaptureMediaCvBackend()
                .detect(syncWeakenedGeneratedMonitorPng());
        CvDetectionResult second = new BoofCvCaptureMediaCvBackend()
                .detect(syncWeakenedGeneratedMonitorPng());

        assertAll(
                () -> assertEquals(CvDetectionStatus.ACCEPTED, first.status()),
                () -> assertTrue(first.candidates().isEmpty()),
                () -> assertEquals(2, first.normalizedFrames().size()),
                () -> assertTrue(first.diagnosticCode().isEmpty()),
                () -> assertEquals(first.status(), second.status()),
                () -> assertEquals(first.metrics(), second.metrics()),
                () -> assertEquals(first.normalizedFrames().get(0).layoutProfile(),
                        second.normalizedFrames().get(0).layoutProfile()),
                () -> assertEquals(first.normalizedFrames().get(0).frameCorners(),
                        second.normalizedFrames().get(0).frameCorners()),
                () -> assertEquals(
                        0.0d,
                        first.metrics().get("boofCvStrictEvidenceCandidateCount"),
                        first.metrics().toString()
                ),
                () -> assertEquals(
                        0.0d,
                        first.metrics().get("boofCvJabEvidenceCandidateCount"),
                        first.metrics().toString()
                ),
                () -> assertTrue(
                        first.metrics().get("boofCvPlausibleValidationCandidateCount") >= 1.0d
                                && first.metrics().get("boofCvPlausibleValidationCandidateCount") <= 2.0d
                ),
                () -> assertEquals(
                        2.0d,
                        first.metrics().get("boofCvAcceptedCandidateCount")
                ),
                () -> assertEquals(2.0d, first.metrics().get("boofCvAcceptedSourceCandidateCount")),
                () -> assertEquals(2.0d, first.metrics().get("boofCvNormalizedFrameCount")),
                () -> assertEquals(1.0d, first.metrics().get("boofCvSelectedBackendCode")),
                () -> assertEquals(1.0d, first.metrics().get("boofCvPreprocessingModeCode")),
                () -> assertEquals(2.0d, first.metrics().get("boofCvSelectedAdmissionBandCode")),
                () -> assertEquals(0.0d, first.metrics().get("boofCvSelectedRejectionReasonCode")),
                () -> assertTrue(first.metrics().get("boofCvSelectedSyncBandScore") < 0.395d),
                () -> assertEquals(
                        "boofcv-fitted-quadrilateral",
                        first.normalizedFrames().get(0).geometrySource().orElseThrow()
                )
        );
    }

    @Test
    @DisplayName("Opt-in backend path remains the same normalized top-candidate path")
    void optInBackendPathRemainsTheSameNormalizedTopCandidatePath() {
        MediaInputFrame frame = BoofCvGeneratedFixtureFactory.cameraLikeMonitorPng();
        CvDetectionResult defaultResult = new BoofCvCaptureMediaCvBackend().detect(frame);
        CvDetectionResult optInResult = BoofCvCaptureMediaCvBackend.withPerspectiveCorrection().detect(frame);
        CvNormalizedFrame normalizedFrame = optInResult.normalizedFrames().get(0);
        int[] normalizedPixels = normalizedFrame.argbPixels();

        assertAll(
                () -> assertEquals(CvDetectionStatus.ACCEPTED, optInResult.status()),
                () -> assertTrue(optInResult.candidates().isEmpty()),
                () -> assertEquals(defaultResult.normalizedFrames().size(), optInResult.normalizedFrames().size()),
                () -> assertTrue(optInResult.diagnosticCode().isEmpty()),
                () -> assertEquals(defaultResult.metrics(), optInResult.metrics()),
                () -> assertEquals(
                        (double) optInResult.normalizedFrames().size(),
                        optInResult.metrics().get("boofCvAcceptedCandidateCount")
                ),
                () -> assertEquals(defaultResult.normalizedFrames().get(0).layoutProfile(), normalizedFrame.layoutProfile()),
                () -> assertEquals(defaultResult.normalizedFrames().get(0).frameCorners(), normalizedFrame.frameCorners()),
                () -> assertEquals(
                        defaultResult.normalizedFrames().get(0).qualityMetrics(),
                        normalizedFrame.qualityMetrics()
                ),
                () -> assertEquals(
                        "boofcv",
                        normalizedFrame.samplingEvidence().orElseThrow().backendId()
                ),
                () -> assertEquals(
                        normalizedFrame.layoutProfile().frameWidthPx()
                                * normalizedFrame.layoutProfile().frameHeightPx(),
                        normalizedPixels.length
                ),
                () -> assertEquals(0xFF000000, normalizedPixels[0] & 0xFF000000),
                () -> assertEquals(
                        0xFF000000,
                        normalizedPixels[normalizedPixels.length - 1] & 0xFF000000
                )
        );
    }

    @Test
    @DisplayName("Production BoofCV code does not import reader protocol, assembly, or restore decisions")
    void productionBoofCvCodeDoesNotImportReaderProtocolAssemblyOrRestoreDecisions() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> sourceFiles = Files.walk(mainSourceRoot())) {
            sourceFiles
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> recordForbiddenImportViolation(path, violations));
        }

        assertTrue(violations.isEmpty(),
                () -> "Production BoofCV boundary imports forbidden reader decisions: " + violations);
    }

    private MediaInputFrame syncWeakenedGeneratedMonitorPng() {
        MediaInputFrame source = BoofCvGeneratedFixtureFactory.cameraLikeMonitorPng();
        int[] pixels = source.copyArgbPixels();
        int sourceFrameLeft = 210;
        int sourceFrameTop = 140;
        int outerMargin = 40;
        int topSyncBandHeight = 48;
        int syncCellWidth = 16;
        int weakenedLightArgb = 0xFFC8C8C8;
        int weakenedDarkArgb = 0xFF737373;
        for (int y = sourceFrameTop + outerMargin; y < sourceFrameTop + outerMargin + topSyncBandHeight; y++) {
            for (int x = sourceFrameLeft + outerMargin;
                    x < sourceFrameLeft + 1280 - outerMargin;
                    x++) {
                int cellIndex = (x - sourceFrameLeft - outerMargin) / syncCellWidth;
                pixels[(y * source.widthPixels()) + x] = cellIndex % 2 == 0 ? weakenedLightArgb : weakenedDarkArgb;
            }
        }
        return new MediaInputFrame(
                "CM-MVP7-GENERATED-WEAK-SYNC-MONITOR-PNG.png",
                source.sourceKind(),
                source.callerOrder(),
                source.widthPixels(),
                source.heightPixels(),
                source.formatName(),
                source.pixelSha256() + "-weak-sync",
                pixels
        );
    }

    private MediaInputFrame mediaFrame(int widthPixels, int heightPixels) {
        int[] pixels = new int[widthPixels * heightPixels];
        for (int index = 0; index < pixels.length; index++) {
            pixels[index] = index % 2 == 0 ? 0xFFFFFFFF : 0xFF000000;
        }
        return new MediaInputFrame(
                "boofcv-probe.png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                widthPixels,
                heightPixels,
                "png",
                "probe-hash",
                pixels
        );
    }

    private Path mainSourceRoot() {
        Path moduleSourceRoot = Path.of("src", "main", "java");
        if (Files.isDirectory(moduleSourceRoot)) {
            return moduleSourceRoot;
        }
        return Path.of("modules", "jab4j-reader-cv-boofcv", "src", "main", "java");
    }

    private void recordForbiddenImportViolation(Path path, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (int index = 0; index < lines.size(); index++) {
                Matcher matcher = IMPORT_DECLARATION.matcher(lines.get(index));
                if (!matcher.matches()) {
                    continue;
                }
                String importedType = matcher.group(1);
                if (matchesForbiddenImport(importedType)) {
                    violations.add(path + ":" + (index + 1) + " imports " + importedType);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect " + path, exception);
        }
    }

    private boolean matchesForbiddenImport(String importedType) {
        return FORBIDDEN_PROTOCOL_IMPORTS.stream()
                .anyMatch(forbiddenImport -> matchesForbiddenImport(importedType, forbiddenImport));
    }

    private boolean matchesForbiddenImport(String importedType, String forbiddenImport) {
        if (forbiddenImport.endsWith(".")) {
            return importedType.startsWith(forbiddenImport);
        }
        if (importedType.equals(forbiddenImport)) {
            return true;
        }
        if (importedType.endsWith(".*")) {
            String importedPackage = importedType.substring(0, importedType.length() - 1);
            return forbiddenImport.startsWith(importedPackage);
        }
        return false;
    }

    private void restoreBackendProperty(String previousBackend) {
        if (previousBackend == null) {
            System.clearProperty(CaptureMediaCvBackends.BACKEND_PROPERTY);
            return;
        }
        System.setProperty(CaptureMediaCvBackends.BACKEND_PROPERTY, previousBackend);
    }
}
