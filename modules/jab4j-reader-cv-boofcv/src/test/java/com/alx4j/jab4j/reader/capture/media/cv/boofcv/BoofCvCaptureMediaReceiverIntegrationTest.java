package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverRequest;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverResult;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverService;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverStatus;
import com.alx4j.jab4j.reader.capture.media.cv.CaptureMediaCvBackends;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionResult;
import com.alx4j.jab4j.reader.capture.media.cv.CvNormalizedFrame;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.normalize.CaptureMediaFrameNormalizer;
import com.alx4j.jab4j.reader.capture.media.normalize.MediaNormalizationResult;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;

@DisplayName("Explicit BoofCV capture-media receiver integration")
class BoofCvCaptureMediaReceiverIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Explicit BoofCV normalizer preserves generated monitor-photo source and candidate evidence")
    void explicitBoofCvNormalizerPreservesGeneratedMonitorPhotoSourceAndCandidateEvidence() {
        String previousBackend = System.getProperty(CaptureMediaCvBackends.BACKEND_PROPERTY);
        try {
            System.setProperty(CaptureMediaCvBackends.BACKEND_PROPERTY, "boofcv");
            CaptureMediaFrameNormalizer normalizer = new CaptureMediaFrameNormalizer();
            MediaInputFrame inputFrame = BoofCvGeneratedFixtureFactory.cameraLikeMonitorPng();
            CvDetectionResult backendResult = new BoofCvCaptureMediaCvBackend().detect(inputFrame);
            CvNormalizedFrame selectedFrame = backendResult.normalizedFrames().get(0);

            MediaNormalizationResult normalizationResult = normalizer.normalize(inputFrame);
            NormalizedCaptureFrame normalizedFrame = normalizationResult.frame().orElseThrow();
            CaptureMediaQualityMetrics qualityMetrics = normalizedFrame.qualityMetrics();

            assertAll(
                    () -> assertEquals("boofcv", normalizer.normalizationBackendId()),
                    () -> assertTrue(normalizationResult.diagnostics().isEmpty()),
                    () -> assertEquals(inputFrame.sourceId(), normalizedFrame.sourceId()),
                    () -> assertEquals(inputFrame.sourceKind(), normalizedFrame.sourceKind()),
                    () -> assertEquals(inputFrame.callerOrder(), normalizedFrame.callerOrder()),
                    () -> assertEquals(inputFrame.widthPixels(), normalizedFrame.originalWidthPixels()),
                    () -> assertEquals(inputFrame.heightPixels(), normalizedFrame.originalHeightPixels()),
                    () -> assertEquals(inputFrame.formatName(), normalizedFrame.formatName()),
                    () -> assertEquals(inputFrame.pixelSha256(), normalizedFrame.pixelSha256()),
                    () -> assertEquals("debug-low-density", normalizedFrame.layoutProfileId()),
                    () -> assertEquals(
                            selectedFrame.layoutProfile().frameWidthPx(),
                            normalizedFrame.normalizedWidthPixels()
                    ),
                    () -> assertEquals(
                            selectedFrame.layoutProfile().frameHeightPx(),
                            normalizedFrame.normalizedHeightPixels()
                    ),
                    () -> assertEquals(selectedFrame.frameCorners(), normalizedFrame.frameCorners()),
                    () -> assertEquals(
                            selectedFrame.qualityMetrics().frameCoverageRatio(),
                            qualityMetrics.frameCoverageRatio()
                    ),
                    () -> assertEquals(selectedFrame.qualityMetrics().skewScore(), qualityMetrics.skewScore()),
                    () -> assertEquals(CaptureMediaQualityMetrics.NOT_MEASURED, qualityMetrics.blurScore()),
                    () -> assertEquals(CaptureMediaQualityMetrics.NOT_MEASURED, qualityMetrics.glareScore()),
                    () -> assertEquals(selectedFrame.geometrySource(), normalizedFrame.geometrySource()),
                    () -> assertEquals(
                            selectedFrame.samplingEvidence().orElseThrow().backendId(),
                            normalizedFrame.samplingEvidence().orElseThrow().backendId()
                    ),
                    () -> assertEquals(selectedFrame.sourceRegionRank(), normalizedFrame.sourceRegionRank()),
                    () -> assertEquals(selectedFrame.profileAlternativeRank(), normalizedFrame.profileAlternativeRank()),
                    () -> assertEquals(selectedFrame.profileAlternativeCount(), normalizedFrame.profileAlternativeCount()),
                    () -> assertEquals(
                            normalizedFrame.normalizedWidthPixels() * normalizedFrame.normalizedHeightPixels(),
                            normalizedFrame.copyArgbPixels().length
                    )
            );
        } finally {
            restoreBackendProperty(previousBackend);
        }
    }

    @Test
    @DisplayName("Explicit BoofCV receiver separates candidate evidence from decode and restore outcome")
    void explicitBoofCvReceiverSeparatesCandidateEvidenceFromDecodeAndRestoreOutcome() throws Exception {
        String previousBackend = System.getProperty(CaptureMediaCvBackends.BACKEND_PROPERTY);
        try {
            System.setProperty(CaptureMediaCvBackends.BACKEND_PROPERTY, "boofcv");
            Path image = writePng("generated-monitor-photo.png", BoofCvGeneratedFixtureFactory.cameraLikeMonitorPng());

            CaptureMediaReceiverResult result = new CaptureMediaReceiverService().evaluate(
                    CaptureMediaReceiverRequest.evaluateStillImages(List.of(image))
            );

            assertAll(
                    () -> assertEquals(CaptureMediaReceiverStatus.INCOMPLETE, result.status()),
                    () -> assertTrue(result.failed()),
                    () -> assertFalse(result.eligibleForRestore()),
                    () -> assertFalse(result.restoreAttempted()),
                    () -> assertEquals(1, result.summary().submittedMediaCount()),
                    () -> assertEquals(1, result.summary().readableMediaCount()),
                    () -> assertEquals(3, result.summary().acceptedCandidateCount()),
                    () -> assertEquals(0, result.summary().rejectedCandidateCount()),
                    () -> assertEquals(2, result.summary().duplicateMediaFrameCount()),
                    () -> assertEquals(1, result.summary().recoveredUniqueFrameCount()),
                    () -> assertEquals(2, result.summary().decodedTileCount()),
                    () -> assertEquals(0L, result.summary().restoredFileCount()),
                    () -> assertTrue(result.diagnostics().stream()
                            .anyMatch(diagnostic -> diagnostic.code() == CaptureMediaDiagnosticCode.MISSING_UNIQUE_FRAME))
            );
        } finally {
            restoreBackendProperty(previousBackend);
        }
    }

    @Test
    @DisplayName("Explicit BoofCV debug output includes backend, candidate, sampler, and diagnostic evidence")
    void explicitBoofCvDebugOutputIncludesBackendCandidateSamplerAndDiagnosticEvidence() throws Exception {
        String previousBackend = System.getProperty(CaptureMediaCvBackends.BACKEND_PROPERTY);
        try {
            System.setProperty(CaptureMediaCvBackends.BACKEND_PROPERTY, "boofcv");
            Path image = writePng("generated-monitor-photo.png", BoofCvGeneratedFixtureFactory.cameraLikeMonitorPng());
            Path debugOutput = tempDir.resolve("boofcv-debug-output");

            CaptureMediaReceiverResult result = new CaptureMediaReceiverService().evaluate(
                    CaptureMediaReceiverRequest.evaluateStillImages(List.of(image))
                            .withDebugOutputDirectory(debugOutput)
            );

            Path candidateImage = debugOutput.resolve("candidate-0000.png");
            Path candidateMetadata = debugOutput.resolve("candidate-0000.txt");
            String metadata = Files.readString(candidateMetadata);
            assertAll(
                    () -> assertEquals(CaptureMediaReceiverStatus.INCOMPLETE, result.status()),
                    () -> assertTrue(Files.isRegularFile(candidateImage)),
                    () -> assertTrue(Files.isRegularFile(candidateMetadata)),
                    () -> assertTrue(metadata.contains("sourceId=" + image.toAbsolutePath().normalize())),
                    () -> assertTrue(metadata.contains("cv.backendId=boofcv")),
                    () -> assertTrue(metadata.contains("candidate.rank=1")),
                    () -> assertTrue(metadata.contains("candidate.normalizationLayoutProfileId=debug-low-density")),
                    () -> assertTrue(metadata.contains("candidate.geometrySource=boofcv-fitted-quadrilateral")),
                    () -> assertTrue(metadata.contains("candidate.profileAlternativeCount=3")),
                    () -> assertTrue(metadata.contains("candidate.sourceBounds.leftPx=")),
                    () -> assertTrue(metadata.contains("candidate.sourceBounds.rightExclusivePx=")),
                    () -> assertTrue(metadata.contains("candidate.corners.topLeftX=")),
                    () -> assertTrue(metadata.contains("candidate.corners.bottomRightY=")),
                    () -> assertTrue(metadata.contains("quality.frameCoverageRatio=")),
                    () -> assertTrue(metadata.contains("quality.skewScore=")),
                    () -> assertTrue(metadata.contains("sampler.candidateAttemptCount=")),
                    () -> assertTrue(metadata.contains("sampler.decodedPayloadCount=")),
                    () -> assertTrue(metadata.contains("sampler.slotCount=")),
                    () -> assertTrue(metadata.contains("sampler.tileDecode.attemptCount=")),
                    () -> assertTrue(metadata.contains("sampler.profileAttemptCount=")),
                    () -> assertTrue(metadata.contains("sampler.evidence.backendId=boofcv")),
                    () -> assertTrue(metadata.contains("sampler.gridPhase.available=true")),
                    () -> assertTrue(metadata.contains("sampler.evidence.metric.boofCvGeometrySourceCode=")),
                    () -> assertTrue(metadata.contains("sampler.selectedLayoutProfileId=debug-low-density")),
                    () -> assertTrue(metadata.contains("sampler.profileSelectionSource=decodedPayload")),
                    () -> assertTrue(metadata.contains("sampler.envelope.acceptedPayloadCount=")),
                    () -> assertTrue(metadata.contains("sampler.envelope.rejectedAttemptCount=")),
                    () -> assertTrue(metadata.contains("diagnostic.selectedPublicCode="))
            );
        } finally {
            restoreBackendProperty(previousBackend);
        }
    }

    @Test
    @DisplayName("Generated false positive has no accepted normalized candidates or restore-eligible content")
    void generatedFalsePositiveHasNoAcceptedNormalizedCandidatesOrRestoreEligibleContent() throws Exception {
        String previousBackend = System.getProperty(CaptureMediaCvBackends.BACKEND_PROPERTY);
        try {
            System.setProperty(CaptureMediaCvBackends.BACKEND_PROPERTY, "boofcv");
            MediaInputFrame falsePositive = BoofCvGeneratedFixtureFactory.brightMonitorWithoutJab();
            MediaNormalizationResult normalizationResult = new CaptureMediaFrameNormalizer().normalize(falsePositive);
            Path image = writePng("bright-monitor-without-jab.png", falsePositive);

            CaptureMediaReceiverResult result = new CaptureMediaReceiverService().evaluate(
                    CaptureMediaReceiverRequest.evaluateStillImages(List.of(image))
            );

            assertAll(
                    () -> assertFalse(normalizationResult.accepted()),
                    () -> assertEquals(CaptureMediaReceiverStatus.REJECTED, result.status()),
                    () -> assertTrue(result.failed()),
                    () -> assertFalse(result.eligibleForRestore()),
                    () -> assertEquals(1, result.summary().submittedMediaCount()),
                    () -> assertEquals(1, result.summary().readableMediaCount()),
                    () -> assertEquals(0, result.summary().acceptedCandidateCount()),
                    () -> assertEquals(1, result.summary().rejectedCandidateCount()),
                    () -> assertEquals(0, result.summary().recoveredUniqueFrameCount()),
                    () -> assertEquals(0, result.summary().decodedTileCount()),
                    () -> assertEquals(0L, result.summary().restoredFileCount()),
                    () -> assertEquals(CaptureMediaDiagnosticCode.SCREEN_OR_FRAME_NOT_FOUND,
                            result.diagnostics().get(0).code())
            );
        } finally {
            restoreBackendProperty(previousBackend);
        }
    }

    private Path writePng(String fileName, MediaInputFrame frame) throws Exception {
        Path output = tempDir.resolve(fileName);
        BufferedImage image = new BufferedImage(
                frame.widthPixels(),
                frame.heightPixels(),
                BufferedImage.TYPE_INT_ARGB
        );
        image.setRGB(
                0,
                0,
                frame.widthPixels(),
                frame.heightPixels(),
                frame.copyArgbPixels(),
                0,
                frame.widthPixels()
        );
        if (!ImageIO.write(image, "png", output.toFile())) {
            throw new IllegalStateException("No PNG ImageIO writer is available");
        }
        return output;
    }

    private void restoreBackendProperty(String previousBackend) {
        if (previousBackend == null) {
            System.clearProperty(CaptureMediaCvBackends.BACKEND_PROPERTY);
            return;
        }
        System.setProperty(CaptureMediaCvBackends.BACKEND_PROPERTY, previousBackend);
    }
}
