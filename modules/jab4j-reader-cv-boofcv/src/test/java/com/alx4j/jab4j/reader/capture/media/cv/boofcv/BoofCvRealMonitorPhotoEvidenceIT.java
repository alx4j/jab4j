package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnostic;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaDiagnosticCode;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverRequest;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverResult;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaReceiverService;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSummary;
import com.alx4j.jab4j.reader.capture.media.cv.CaptureMediaCvBackends;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionResult;
import com.alx4j.jab4j.reader.capture.media.cv.CvDetectionStatus;
import com.alx4j.jab4j.reader.capture.media.input.CaptureMediaInputIntake;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.input.MediaIntakeResult;

/**
 * Manual opt-in evidence capture for approved local real monitor-photo still images.
 */
@DisplayName("Opt-in BoofCV real monitor-photo evidence")
final class BoofCvRealMonitorPhotoEvidenceIT {

    private static final String REAL_MONITOR_PHOTO_INPUT_PROPERTY = "jab4j.boofcv.realMonitorPhotoInput";
    private static final Path EVIDENCE_ROOT = Path.of("target", "boofcv-real-monitor-evidence");
    private static final Set<String> SAMPLE_EXTENSIONS = Set.of("jpg", "jpeg", "heic", "heif");
    private static final Set<String> MVP6_TARGET_SAMPLE_STEMS = Set.of("IMG_5865", "IMG_5867");

    /**
     * Evaluates local monitor-photo samples only when the manual input property is supplied.
     *
     * @throws Exception when the opt-in evidence run cannot write local target output
     */
    @Test
    @DisplayName("Manual real monitor-photo run writes local non-gating evidence")
    void manualRealMonitorPhotoRunWritesLocalNonGatingEvidence() throws Exception {
        String rawInput = System.getProperty(REAL_MONITOR_PHOTO_INPUT_PROPERTY);
        assumeTrue(
                rawInput != null && !rawInput.isBlank(),
                "Set -D" + REAL_MONITOR_PHOTO_INPUT_PROPERTY + "=<approved-sample-file-or-directory> "
                        + "to run manual real monitor-photo evidence capture."
        );

        List<Path> samples = discoverSamples(rawInput);
        assertFalse(samples.isEmpty(),
                () -> "No approved .jpg, .jpeg, .heic, or .heif samples found under " + rawInput);

        String previousBackend = System.getProperty(CaptureMediaCvBackends.BACKEND_PROPERTY);
        try {
            System.setProperty(CaptureMediaCvBackends.BACKEND_PROPERTY, "boofcv");
            Path runDirectory = Files.createTempDirectory(Files.createDirectories(EVIDENCE_ROOT), "run-");
            CaptureMediaReceiverService receiverService = new CaptureMediaReceiverService();
            List<EvidenceRow> rows = samples.stream()
                    .map(sample -> evaluateSample(receiverService, sample, runDirectory))
                    .toList();

            writeEvidence(runDirectory.resolve("real-monitor-photo-evidence.tsv"), rows);
            writeEvidence(EVIDENCE_ROOT.resolve("latest-real-monitor-photo-evidence.tsv"), rows);

            assertAll(rows.stream()
                    .map(row -> (Executable) () -> assertApprovedClassification(row))
                    .toList());
        } finally {
            restoreBackendProperty(previousBackend);
        }
    }

    private EvidenceRow evaluateSample(
            CaptureMediaReceiverService receiverService,
            Path sample,
            Path runDirectory
    ) {
        Path debugOutput = runDirectory.resolve("debug").resolve(debugDirectoryName(sample));
        BoofCvProbe boofCvProbe = probeBoofCv(sample);
        CaptureMediaReceiverResult result = receiverService.evaluate(
                CaptureMediaReceiverRequest.evaluateStillImages(List.of(sample))
                        .withDebugOutputDirectory(debugOutput)
        );
        return EvidenceRow.from(sample, debugOutput, result, boofCvProbe);
    }

    private BoofCvProbe probeBoofCv(Path sample) {
        MediaIntakeResult intakeResult = new CaptureMediaInputIntake().read(
                CaptureMediaReceiverRequest.evaluateStillImages(List.of(sample))
        );
        try {
            Optional<MediaInputFrame> frame = intakeResult.readableFrames().stream().findFirst();
            if (frame.isEmpty()) {
                return BoofCvProbe.noReadableFrame();
            }
            return BoofCvProbe.from(new BoofCvCaptureMediaCvBackend().detect(frame.orElseThrow()));
        } catch (RuntimeException exception) {
            return BoofCvProbe.failed(exception.getClass().getSimpleName());
        } finally {
            intakeResult.readableFrames().forEach(MediaInputFrame::releaseArgbPixels);
        }
    }

    private List<Path> discoverSamples(String rawInput) throws IOException {
        List<Path> roots = Arrays.stream(rawInput.split(Pattern.quote(File.pathSeparator)))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Path::of)
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
        for (Path root : roots) {
            assertTrue(Files.exists(root), () -> "Real monitor-photo input path does not exist: " + root);
        }
        try (Stream<Path> stream = roots.stream().flatMap(this::sampleFiles)) {
            return stream
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(this::sampleExtension)
                    .distinct()
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private Stream<Path> sampleFiles(Path root) {
        if (Files.isDirectory(root)) {
            try {
                return Files.walk(root)
                        .filter(Files::isRegularFile);
            } catch (IOException exception) {
                throw new IllegalStateException("Real monitor-photo input directory could not be listed: " + root,
                        exception);
            }
        }
        return Stream.of(root);
    }

    private boolean sampleExtension(Path path) {
        return SAMPLE_EXTENSIONS.contains(extension(path));
    }

    private void assertApprovedClassification(EvidenceRow row) {
        if (row.heicUnsupportedBeforeReadablePixels()) {
            assertAll(
                    () -> assertEquals(
                            CaptureMediaDiagnosticCode.UNSUPPORTED_IMAGE_FORMAT.name(),
                            row.primaryDiagnosticCode,
                            () -> "HEIC/HEIF input without readable pixels must remain an unsupported-image-format "
                                    + "intake result: " + row.sample
                    ),
                    () -> assertEquals(0, row.acceptedCandidateCount,
                            () -> "Unsupported HEIC/HEIF intake must not claim BoofCV candidate evidence: "
                                    + row.sample)
            );
            return;
        }
        if (row.approvedClassification == SampleClassification.CANDIDATE_POSITIVE) {
            assertTrue(row.acceptedCandidateCount > 0,
                    () -> "Approved candidate-positive sample did not produce accepted BoofCV candidate evidence: "
                            + row.sample);
        }
        if (row.approvedClassification == SampleClassification.CANDIDATE_NEGATIVE) {
            assertEquals(0, row.acceptedCandidateCount,
                    () -> "Approved candidate-negative sample produced accepted BoofCV candidate evidence: "
                            + row.sample);
        }
    }

    private void writeEvidence(Path output, List<EvidenceRow> rows) throws IOException {
        Files.createDirectories(Objects.requireNonNull(output.getParent(), "output parent must not be null"));
        StringBuilder builder = new StringBuilder();
        builder.append(String.join("\t", List.of(
                "sample",
                "extension",
                "approvedClassification",
                "observedCandidateClassification",
                "mvp6TargetSample",
                "status",
                "submitted",
                "readable",
                "backendSelectable",
                "backendUnavailable",
                "boofCvDetectionStatus",
                "boofCvProposedRegions",
                "boofCvScoredCandidates",
                "boofCvEvidenceCandidates",
                "boofCvAcceptedCandidates",
                "boofCvSelectedCandidateScore",
                "boofCvSelectedSyncBandScore",
                "boofCvSelectedGridScore",
                "boofCvSelectedBorderContrastScore",
                "boofCvSelectedFrameCoverageRatio",
                "acceptedCandidates",
                "rejectedCandidates",
                "decodedTiles",
                "recoveredUniqueFrames",
                "duplicates",
                "restoredFiles",
                "boofCvCandidateEvidence",
                "samplerCandidateAttempts",
                "samplerNoFinderAttempts",
                "samplerPaletteRejectedAttempts",
                "samplerDecodedPayloads",
                "samplerTileDecodeAttempts",
                "samplerEnvelopeAcceptedPayloads",
                "samplerEnvelopeRejectedAttempts",
                "samplerSelectedPublicDiagnostic",
                "primaryDiagnostic",
                "primaryDiagnosticMessage",
                "diagnostics",
                "debugOutputPath",
                "message"
        ))).append(System.lineSeparator());
        for (EvidenceRow row : rows) {
            builder.append(String.join("\t", List.of(
                    tsvValue(row.sample),
                    row.extension,
                    row.approvedClassification.label,
                    row.observedCandidateClassification.label,
                    Boolean.toString(row.mvp6TargetSample),
                    row.status,
                    Integer.toString(row.submittedMediaCount),
                    Integer.toString(row.readableMediaCount),
                    Boolean.toString(row.backendSelectable),
                    Boolean.toString(row.backendUnavailable),
                    row.boofCvDetectionStatus,
                    Integer.toString(row.boofCvProposedRegionCount),
                    Integer.toString(row.boofCvScoredCandidateCount),
                    Integer.toString(row.boofCvEvidenceCandidateCount),
                    Integer.toString(row.boofCvAcceptedCandidateCount),
                    row.boofCvSelectedCandidateScore,
                    row.boofCvSelectedSyncBandScore,
                    row.boofCvSelectedGridScore,
                    row.boofCvSelectedBorderContrastScore,
                    row.boofCvSelectedFrameCoverageRatio,
                    Integer.toString(row.acceptedCandidateCount),
                    Integer.toString(row.rejectedCandidateCount),
                    Integer.toString(row.decodedTileCount),
                    Integer.toString(row.recoveredUniqueFrameCount),
                    Integer.toString(row.duplicateMediaFrameCount),
                    Long.toString(row.restoredFileCount),
                    Boolean.toString(row.boofCvCandidateEvidence),
                    Integer.toString(row.samplerEvidence.candidateAttemptCount()),
                    Integer.toString(row.samplerEvidence.noFinderAttemptCount()),
                    Integer.toString(row.samplerEvidence.paletteRejectedAttemptCount()),
                    Integer.toString(row.samplerEvidence.decodedPayloadCount()),
                    Integer.toString(row.samplerEvidence.tileDecodeAttemptCount()),
                    Integer.toString(row.samplerEvidence.envelopeAcceptedPayloadCount()),
                    Integer.toString(row.samplerEvidence.envelopeRejectedAttemptCount()),
                    row.samplerEvidence.selectedPublicDiagnostic(),
                    row.primaryDiagnosticCode,
                    tsvValue(row.primaryDiagnosticMessage),
                    row.diagnosticCodes,
                    tsvValue(row.debugOutputPath),
                    tsvValue(row.message)
            ))).append(System.lineSeparator());
        }
        Files.writeString(output, builder.toString(), StandardCharsets.UTF_8);
    }

    private String debugDirectoryName(Path sample) {
        String fileName = sample.getFileName().toString();
        String safeName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        return safeName.isBlank() ? "sample" : safeName;
    }

    private String tsvValue(Object value) {
        return Objects.toString(value, "")
                .replace('\t', ' ')
                .replace('\r', ' ')
                .replace('\n', ' ');
    }

    private String extension(Path path) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void restoreBackendProperty(String previousBackend) {
        if (previousBackend == null) {
            System.clearProperty(CaptureMediaCvBackends.BACKEND_PROPERTY);
            return;
        }
        System.setProperty(CaptureMediaCvBackends.BACKEND_PROPERTY, previousBackend);
    }

    private enum SampleClassification {
        CANDIDATE_POSITIVE("candidate-positive"),
        CANDIDATE_NEGATIVE("candidate-negative"),
        EXPLORATORY("exploratory");

        private final String label;

        SampleClassification(String label) {
            this.label = label;
        }

        private static SampleClassification approvedFrom(Path sample) {
            for (Path segment : sample) {
                String name = segment.toString().toLowerCase(Locale.ROOT).replace('_', '-');
                if (name.contains(CANDIDATE_POSITIVE.label)) {
                    return CANDIDATE_POSITIVE;
                }
                if (name.contains(CANDIDATE_NEGATIVE.label)) {
                    return CANDIDATE_NEGATIVE;
                }
            }
            return EXPLORATORY;
        }

        private static SampleClassification observedFrom(
                String extension,
                CaptureMediaSummary summary,
                String primaryDiagnosticCode
        ) {
            if (heicExtension(extension)
                    && summary.readableMediaCount() == 0
                    && CaptureMediaDiagnosticCode.UNSUPPORTED_IMAGE_FORMAT.name().equals(primaryDiagnosticCode)) {
                return EXPLORATORY;
            }
            return summary.acceptedCandidateCount() > 0 ? CANDIDATE_POSITIVE : CANDIDATE_NEGATIVE;
        }
    }

    private record EvidenceRow(
            Path sample,
            String extension,
            SampleClassification approvedClassification,
            SampleClassification observedCandidateClassification,
            boolean mvp6TargetSample,
            String status,
            int submittedMediaCount,
            int readableMediaCount,
            boolean backendSelectable,
            boolean backendUnavailable,
            String boofCvDetectionStatus,
            int boofCvProposedRegionCount,
            int boofCvScoredCandidateCount,
            int boofCvEvidenceCandidateCount,
            int boofCvAcceptedCandidateCount,
            String boofCvSelectedCandidateScore,
            String boofCvSelectedSyncBandScore,
            String boofCvSelectedGridScore,
            String boofCvSelectedBorderContrastScore,
            String boofCvSelectedFrameCoverageRatio,
            int acceptedCandidateCount,
            int rejectedCandidateCount,
            int decodedTileCount,
            int recoveredUniqueFrameCount,
            int duplicateMediaFrameCount,
            long restoredFileCount,
            boolean boofCvCandidateEvidence,
            SamplerEvidence samplerEvidence,
            String primaryDiagnosticCode,
            String primaryDiagnosticMessage,
            String diagnosticCodes,
            Path debugOutputPath,
            String message
    ) {

        private static EvidenceRow from(
                Path sample,
                Path debugOutputPath,
                CaptureMediaReceiverResult result,
                BoofCvProbe boofCvProbe
        ) {
            CaptureMediaSummary summary = result.summary();
            String extension = fileExtension(sample);
            Optional<CaptureMediaDiagnostic> primaryDiagnostic = primaryDiagnostic(result.diagnostics());
            SamplerEvidence samplerEvidence = SamplerEvidence.from(debugOutputPath);
            String primaryDiagnosticCode = primaryDiagnostic
                    .map(diagnostic -> diagnostic.code().name())
                    .orElse("none");
            return new EvidenceRow(
                    sample,
                    extension,
                    SampleClassification.approvedFrom(sample),
                    SampleClassification.observedFrom(extension, summary, primaryDiagnosticCode),
                    mvp6TargetSample(sample),
                    result.status().name(),
                    summary.submittedMediaCount(),
                    summary.readableMediaCount(),
                    CaptureMediaCvBackends.findExplicit("boofcv").isPresent(),
                    backendUnavailable(result.diagnostics()),
                    boofCvProbe.detectionStatus(),
                    boofCvProbe.proposedRegionCount(),
                    boofCvProbe.scoredCandidateCount(),
                    boofCvProbe.evidenceCandidateCount(),
                    boofCvProbe.acceptedCandidateCount(),
                    boofCvProbe.selectedCandidateScore(),
                    boofCvProbe.selectedSyncBandScore(),
                    boofCvProbe.selectedGridScore(),
                    boofCvProbe.selectedBorderContrastScore(),
                    boofCvProbe.selectedFrameCoverageRatio(),
                    summary.acceptedCandidateCount(),
                    summary.rejectedCandidateCount(),
                    summary.decodedTileCount(),
                    summary.recoveredUniqueFrameCount(),
                    summary.duplicateMediaFrameCount(),
                    summary.restoredFileCount(),
                    boofCvProbe.evidenceCandidateCount() > 0,
                    samplerEvidence,
                    primaryDiagnosticCode,
                    primaryDiagnostic.map(CaptureMediaDiagnostic::message).orElse("none"),
                    diagnosticCodes(result.diagnostics()),
                    debugOutputPath,
                    result.message()
            );
        }

        private boolean heicUnsupportedBeforeReadablePixels() {
            return heicExtension(extension)
                    && readableMediaCount == 0
                    && CaptureMediaDiagnosticCode.UNSUPPORTED_IMAGE_FORMAT.name().equals(primaryDiagnosticCode);
        }

        private static String fileExtension(Path path) {
            String fileName = path.getFileName().toString();
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
                return "";
            }
            return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        }

        private static boolean mvp6TargetSample(Path sample) {
            return MVP6_TARGET_SAMPLE_STEMS.contains(fileStem(sample).toUpperCase(Locale.ROOT));
        }

        private static String fileStem(Path path) {
            String fileName = path.getFileName().toString();
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex <= 0) {
                return fileName;
            }
            return fileName.substring(0, dotIndex);
        }

        private static Optional<CaptureMediaDiagnostic> primaryDiagnostic(List<CaptureMediaDiagnostic> diagnostics) {
            Optional<CaptureMediaDiagnostic> blockingCode = diagnostics.stream()
                    .filter(CaptureMediaDiagnostic::blocking)
                    .findFirst();
            return blockingCode.or(() -> diagnostics.stream()
                    .findFirst());
        }

        private static boolean backendUnavailable(List<CaptureMediaDiagnostic> diagnostics) {
            return diagnostics.stream()
                    .anyMatch(diagnostic -> diagnostic.metrics().getOrDefault(
                                    "unavailableBackendSelection",
                                    0.0d
                            ) > 0.0d
                            || diagnostic.message().contains("is not available"));
        }

        private static String diagnosticCodes(List<CaptureMediaDiagnostic> diagnostics) {
            if (diagnostics.isEmpty()) {
                return "none";
            }
            return String.join(",", diagnostics.stream()
                    .map(diagnostic -> diagnostic.code().name())
                    .toList());
        }
    }

    private record SamplerEvidence(
            int candidateAttemptCount,
            int noFinderAttemptCount,
            int paletteRejectedAttemptCount,
            int decodedPayloadCount,
            int tileDecodeAttemptCount,
            int envelopeAcceptedPayloadCount,
            int envelopeRejectedAttemptCount,
            String selectedPublicDiagnostic
    ) {

        private static SamplerEvidence empty() {
            return new SamplerEvidence(0, 0, 0, 0, 0, 0, 0, "");
        }

        private static SamplerEvidence from(Path debugOutputPath) {
            if (!Files.isDirectory(debugOutputPath)) {
                return empty();
            }
            List<Path> sidecars;
            try (Stream<Path> stream = Files.walk(debugOutputPath)) {
                sidecars = stream
                        .filter(Files::isRegularFile)
                        .filter(SamplerEvidence::candidateSidecar)
                        .sorted(Comparator.comparing(Path::toString))
                        .toList();
            } catch (IOException exception) {
                return empty();
            }
            int candidateAttempts = 0;
            int noFinderAttempts = 0;
            int paletteRejectedAttempts = 0;
            int decodedPayloads = 0;
            int tileDecodeAttempts = 0;
            int envelopeAcceptedPayloads = 0;
            int envelopeRejectedAttempts = 0;
            List<String> selectedDiagnostics = new ArrayList<>();
            for (Path sidecar : sidecars) {
                Map<String, String> fields = sidecarFields(sidecar);
                candidateAttempts += intField(fields, "sampler.candidateAttemptCount");
                noFinderAttempts += intField(fields, "sampler.noFinderAttemptCount");
                paletteRejectedAttempts += intField(fields, "sampler.paletteRejectedAttemptCount");
                decodedPayloads += intField(fields, "sampler.decodedPayloadCount");
                tileDecodeAttempts += intField(fields, "sampler.tileDecode.attemptCount");
                envelopeAcceptedPayloads += intField(fields, "sampler.envelope.acceptedPayloadCount");
                envelopeRejectedAttempts += intField(fields, "sampler.envelope.rejectedAttemptCount");
                String selectedDiagnostic = fields.getOrDefault("diagnostic.selectedPublicCode", "");
                if (!selectedDiagnostic.isBlank()) {
                    selectedDiagnostics.add(selectedDiagnostic);
                }
            }
            return new SamplerEvidence(
                    candidateAttempts,
                    noFinderAttempts,
                    paletteRejectedAttempts,
                    decodedPayloads,
                    tileDecodeAttempts,
                    envelopeAcceptedPayloads,
                    envelopeRejectedAttempts,
                    selectedDiagnostics.isEmpty() ? "" : String.join(",", selectedDiagnostics)
            );
        }

        private static boolean candidateSidecar(Path path) {
            String fileName = path.getFileName().toString();
            return fileName.startsWith("candidate-") && fileName.endsWith(".txt");
        }

        private static Map<String, String> sidecarFields(Path sidecar) {
            try {
                Map<String, String> fields = new LinkedHashMap<>();
                for (String line : Files.readAllLines(sidecar, StandardCharsets.UTF_8)) {
                    int separator = line.indexOf('=');
                    if (separator > 0) {
                        fields.put(line.substring(0, separator), line.substring(separator + 1));
                    }
                }
                return fields;
            } catch (IOException exception) {
                return Map.of();
            }
        }

        private static int intField(Map<String, String> fields, String key) {
            try {
                return Integer.parseInt(fields.getOrDefault(key, "0"));
            } catch (NumberFormatException exception) {
                return 0;
            }
        }
    }

    private record BoofCvProbe(
            String detectionStatus,
            int proposedRegionCount,
            int scoredCandidateCount,
            int evidenceCandidateCount,
            int acceptedCandidateCount,
            String selectedCandidateScore,
            String selectedSyncBandScore,
            String selectedGridScore,
            String selectedBorderContrastScore,
            String selectedFrameCoverageRatio
    ) {

        private static BoofCvProbe noReadableFrame() {
            return new BoofCvProbe("NO_READABLE_FRAME", 0, 0, 0, 0, "", "", "", "", "");
        }

        private static BoofCvProbe failed(String failureKind) {
            return new BoofCvProbe("PROBE_FAILED:" + failureKind, 0, 0, 0, 0, "", "", "", "", "");
        }

        private static BoofCvProbe from(CvDetectionResult result) {
            Map<String, Double> metrics = result.metrics();
            int acceptedCandidateCount = intMetric(metrics, "boofCvAcceptedCandidateCount");
            if (result.status() == CvDetectionStatus.ACCEPTED && acceptedCandidateCount == 0) {
                acceptedCandidateCount = result.candidates().size() + result.normalizedFrames().size();
            }
            return new BoofCvProbe(
                    result.status().name(),
                    intMetric(metrics, "boofCvComponentCandidateCount"),
                    intMetric(metrics, "boofCvScoredCandidateCount"),
                    intMetric(metrics, "boofCvJabEvidenceCandidateCount"),
                    acceptedCandidateCount,
                    metric(metrics, "boofCvSelectedCandidateScore"),
                    metric(metrics, "boofCvSelectedSyncBandScore"),
                    metric(metrics, "boofCvSelectedGridScore"),
                    metric(metrics, "boofCvSelectedBorderContrastScore"),
                    metric(metrics, "boofCvSelectedFrameCoverageRatio")
            );
        }

        private static int intMetric(Map<String, Double> metrics, String name) {
            return (int) Math.round(metrics.getOrDefault(name, 0.0d));
        }

        private static String metric(Map<String, Double> metrics, String name) {
            return Optional.ofNullable(metrics.get(name))
                    .map(Object::toString)
                    .orElse("");
        }
    }

    private static boolean heicExtension(String extension) {
        return "heic".equals(extension) || "heif".equals(extension);
    }
}
