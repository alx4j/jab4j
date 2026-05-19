package com.alx4j.jab4j.reader.capture.media.sample;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.CRC32C;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.TileIndex;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaCandidateId;
import com.alx4j.jab4j.reader.capture.media.evidence.CaptureMediaEvidenceReasonCode;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryCandidateEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitModelType;
import com.alx4j.jab4j.reader.capture.media.evidence.GeometryFitStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.LocalRefinementStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSampleStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSamplingEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.ModuleSamplingStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.ObservedPaletteCenterSource;
import com.alx4j.jab4j.reader.capture.media.evidence.ObservedPaletteClassificationMode;
import com.alx4j.jab4j.reader.capture.media.evidence.ObservedPaletteColorMethod;
import com.alx4j.jab4j.reader.capture.media.evidence.ObservedPaletteStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternEvidence;
import com.alx4j.jab4j.reader.capture.media.evidence.PatternEvidenceStatus;
import com.alx4j.jab4j.reader.capture.media.evidence.ReprojectionMetrics;
import com.alx4j.jab4j.reader.capture.media.geometry.CaptureMediaGeometryFitter;
import com.alx4j.jab4j.reader.capture.media.geometry.CaptureMediaLocalLatticeRefiner;
import com.alx4j.jab4j.reader.capture.media.geometry.ModuleLatticeProjector;
import com.alx4j.jab4j.reader.capture.media.geometry.SupportedTileFinderEvaluator;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;
import com.alx4j.jab4j.reader.capture.qualify.CaptureRenderedLayoutCatalog;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecProfiles;
import com.alx4j.jab4j.tile.TileCodecs;
import com.alx4j.jab4j.transfer.TilePayloadEnvelopeCodec;

@DisplayName("Capture media source-space module sampler")
class CaptureMediaSourceSpaceModuleSamplerTest {

    private static final String SOURCE_ID = "source-space-sampler-test.png";
    private static final String PIXEL_SHA256 =
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
    private static final int WIDTH = 100;
    private static final int HEIGHT = 100;
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int LOW_CONFIDENCE_BLACK = 0xFF000020;
    private static final int VALIDATION_MODULE_SIZE = 10;
    private static final SessionId SESSION_ID =
            new SessionId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
    private static final TilePayloadEnvelopeCodec ENVELOPE_CODEC = new TilePayloadEnvelopeCodec();

    private final CaptureMediaSourceSpaceModuleSampler sampler = new CaptureMediaSourceSpaceModuleSampler(
            new CaptureRenderedLayoutCatalog(List.of(layoutProfile())),
            TileCodecProfiles.balancedV1(),
            new ModuleLatticeProjector(),
            new CaptureMediaPaletteSampler()
    );

    @Test
    @DisplayName("Samples readable modules deterministically across three central-scale variants")
    void samplesReadableModulesDeterministicallyAcrossThreeCentralScaleVariants() {
        MediaInputFrame sourceFrame = sourceFrame(fill(BLACK));
        NormalizedCaptureFrame normalizedFrame = normalizedFrame();
        GeometryFitEvidence geometry = acceptedGeometry(identityTransform());

        List<ModuleSamplingEvidence> first = sampler.sample(sourceFrame, normalizedFrame, geometry);
        List<ModuleSamplingEvidence> second = sampler.sample(sourceFrame, normalizedFrame, geometry);

        assertAll(
                () -> assertEquals(3, first.size()),
                () -> assertEquals(List.of(0.50d, 0.40d, 0.60d),
                        first.stream().map(ModuleSamplingEvidence::centralScale).toList()),
                () -> assertEquals(List.of(441, 441, 441),
                        first.stream().map(ModuleSamplingEvidence::totalModuleCount).toList()),
                () -> assertEquals(List.of(441, 441, 441),
                        first.stream().map(ModuleSamplingEvidence::readableModuleCount).toList()),
                () -> assertEquals(List.of(ModuleSamplingStatus.SAMPLED, ModuleSamplingStatus.SAMPLED,
                                ModuleSamplingStatus.SAMPLED),
                        first.stream().map(ModuleSamplingEvidence::status).toList()),
                () -> assertTrue(first.get(0).tileDecodeAttempted()),
                () -> assertEquals(1, first.get(0).tileDecodeAttemptCount()),
                () -> assertEquals(0, first.get(0).acceptedPayloadCount()),
                () -> assertEquals(0, first.get(0).weakModuleCount()),
                () -> assertEquals(0, first.get(0).poorFootprintModuleCount()),
                () -> assertTrue(first.get(0).moduleConfidenceSummary().get("min") >= 0.20d),
                () -> assertTrue(first.get(0).geometryFootprintQualitySummary().get("min") >= 0.25d),
                () -> assertTrue(first.get(0).tileDecodeFailureStages().contains("TILE_DECODE")),
                () -> assertEquals(
                        first.stream().map(ModuleSamplingEvidence::readableModuleCount).toList(),
                        second.stream().map(ModuleSamplingEvidence::readableModuleCount).toList()
                ),
                () -> assertTrue(first.get(0).reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.SIDE_VERSION_INFERRED_FROM_LAYOUT)),
                () -> assertTrue(first.get(0).reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.DOWNSTREAM_TILE_VALIDATION_FAILED))
        );
    }

    @Test
    @DisplayName("Marks source-boundary clipping and out-of-bounds modules explicitly")
    void marksSourceBoundaryClippingAndOutOfBoundsModulesExplicitly() {
        List<ModuleSamplingEvidence> evidence = sampler.sample(
                sourceFrame(fill(BLACK)),
                normalizedFrame(),
                acceptedGeometry(List.of(
                        1.0d, 0.0d, -30.0d,
                        0.0d, 1.0d, -30.0d,
                        0.0d, 0.0d, 1.0d
                ))
        );

        ModuleSamplingEvidence firstVariant = evidence.get(0);
        assertAll(
                () -> assertTrue(firstVariant.clippedModuleCount() > 0),
                () -> assertTrue(firstVariant.outOfBoundsModuleCount() > 0),
                () -> assertTrue(firstVariant.modules().stream()
                        .anyMatch(module -> module.status() == ModuleSampleStatus.CLIPPED
                                && module.reasonCodes()
                                .contains(CaptureMediaEvidenceReasonCode.MODULE_REGION_CLIPPED))),
                () -> assertTrue(firstVariant.modules().stream()
                        .anyMatch(module -> module.status() == ModuleSampleStatus.OUT_OF_BOUNDS
                                && module.reasonCodes()
                                .contains(CaptureMediaEvidenceReasonCode.MODULE_REGION_OUT_OF_BOUNDS)))
        );
    }

    @Test
    @DisplayName("Marks collapsed footprints unreadable when fewer than nine unique source samples remain")
    void marksCollapsedFootprintsUnreadableWhenFewerThanNineUniqueSourceSamplesRemain() {
        List<ModuleSamplingEvidence> evidence = sampler.sample(
                sourceFrame(fill(BLACK)),
                normalizedFrame(),
                acceptedGeometry(List.of(
                        0.10d, 0.0d, 10.0d,
                        0.0d, 0.10d, 10.0d,
                        0.0d, 0.0d, 1.0d
                ))
        );

        ModuleEvidence unreadable = evidence.get(0).modules().stream()
                .filter(module -> module.status() == ModuleSampleStatus.UNREADABLE)
                .findFirst()
                .orElseThrow();
        assertAll(
                () -> assertTrue(unreadable.sampleCount() < 9),
                () -> assertTrue(unreadable.geometryFootprintQuality() < 0.25d),
                () -> assertTrue(unreadable.moduleConfidence() < 0.25d),
                () -> assertTrue(unreadable.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.INSUFFICIENT_SAMPLE_COUNT)),
                () -> assertTrue(evidence.get(0).poorFootprintModuleCount() > 0),
                () -> assertTrue(evidence.get(0).unreadableModuleCount() > 0)
        );
    }

    @Test
    @DisplayName("Marks high-variance module colors ambiguous")
    void marksHighVarianceModuleColorsAmbiguous() {
        int[] pixels = fill(BLACK);
        for (int row = 9; row <= 11; row++) {
            for (int col = 9; col <= 11; col++) {
                pixels[(row * WIDTH) + col] = ((row + col) % 2 == 0) ? BLACK : WHITE;
            }
        }

        ModuleEvidence firstModule = firstModule(sampler.sample(
                sourceFrame(pixels),
                normalizedFrame(),
                acceptedGeometry(identityTransform())
        ));

        assertAll(
                () -> assertEquals(ModuleSampleStatus.AMBIGUOUS, firstModule.status()),
                () -> assertTrue(firstModule.colorVariance() > 900.0d),
                () -> assertTrue(firstModule.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.HIGH_COLOR_VARIANCE))
        );
    }

    @Test
    @DisplayName("Marks low-confidence palette margins ambiguous without assigning a module value")
    void marksLowConfidencePaletteMarginsAmbiguousWithoutAssigningModuleValue() {
        int[] pixels = fill(BLACK);
        for (int row = 9; row <= 11; row++) {
            for (int col = 9; col <= 11; col++) {
                pixels[(row * WIDTH) + col] = LOW_CONFIDENCE_BLACK;
            }
        }

        ModuleEvidence firstModule = firstModule(sampler.sample(
                sourceFrame(pixels),
                normalizedFrame(),
                acceptedGeometry(identityTransform())
        ));

        assertAll(
                () -> assertEquals(ModuleSampleStatus.AMBIGUOUS, firstModule.status()),
                () -> assertTrue(firstModule.assignedPaletteIndex().isEmpty()),
                () -> assertTrue(firstModule.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.LOW_COLOR_MARGIN))
        );
    }

    @Test
    @DisplayName("Caps sampling evidence at three variants for each retained accepted geometry candidate")
    void capsSamplingEvidenceAtThreeVariantsForEachRetainedAcceptedGeometryCandidate() {
        GeometryFitEvidence geometry = new GeometryFitEvidence(
                1,
                patternCandidateId(),
                GeometryFitStatus.ACCEPTED,
                List.of(
                        geometryCandidate(1, identityTransform(), true),
                        geometryCandidate(2, List.of(
                                1.0d, 0.0d, 1.0d,
                                0.0d, 1.0d, 0.0d,
                                0.0d, 0.0d, 1.0d
                        ), true),
                        geometryCandidate(3, List.of(
                                1.0d, 0.0d, 0.0d,
                                0.0d, 1.0d, 1.0d,
                                0.0d, 0.0d, 1.0d
                        ), true)
                ),
                Optional.empty(),
                Optional.empty(),
                List.of()
        );

        List<ModuleSamplingEvidence> evidence = sampler.sample(sourceFrame(fill(BLACK)), normalizedFrame(), geometry);

        assertAll(
                () -> assertEquals(9, evidence.size()),
                () -> assertEquals(3, evidence.stream()
                        .map(ModuleSamplingEvidence::geometryCandidateId)
                        .distinct()
                        .count()),
                () -> assertTrue(evidence.stream()
                        .allMatch(ModuleSamplingEvidence::tileDecodeAttempted))
        );
    }

    @Test
    @DisplayName("Returns not-available evidence when retained source pixels have been released")
    void returnsNotAvailableEvidenceWhenRetainedSourcePixelsHaveBeenReleased() {
        MediaInputFrame sourceFrame = sourceFrame(fill(BLACK));
        sourceFrame.releaseArgbPixels();

        List<ModuleSamplingEvidence> evidence = sampler.sample(
                sourceFrame,
                normalizedFrame(),
                acceptedGeometry(identityTransform())
        );

        assertAll(
                () -> assertEquals(1, evidence.size()),
                () -> assertEquals(ModuleSamplingStatus.NOT_AVAILABLE, evidence.get(0).status()),
                () -> assertTrue(evidence.get(0).reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.SOURCE_PIXELS_UNAVAILABLE)),
                () -> assertFalse(evidence.get(0).tileDecodeAttempted())
        );
    }

    @Test
    @DisplayName("Withholds sampling evidence when geometry has no accepted candidate")
    void withholdsSamplingEvidenceWhenGeometryHasNoAcceptedCandidate() {
        GeometryFitEvidence geometry = new GeometryFitEvidence(
                1,
                patternCandidateId(),
                GeometryFitStatus.WITHHELD,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                List.of(CaptureMediaEvidenceReasonCode.NO_ACCEPTED_GEOMETRY)
        );

        List<ModuleSamplingEvidence> evidence = sampler.sample(
                sourceFrame(fill(BLACK)),
                normalizedFrame(),
                geometry
        );

        assertAll(
                () -> assertEquals(1, evidence.size()),
                () -> assertEquals(ModuleSamplingStatus.WITHHELD, evidence.get(0).status()),
                () -> assertTrue(evidence.get(0).reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.NO_ACCEPTED_GEOMETRY)),
                () -> assertTrue(evidence.get(0).modules().isEmpty())
        );
    }

    @Test
    @DisplayName("Withholds source-space sampling when the internal rollback switch is disabled")
    void withholdsSourceSpaceSamplingWhenInternalRollbackSwitchIsDisabled() {
        List<ModuleSamplingEvidence> evidence = new CaptureMediaSourceSpaceModuleSampler(false).sample(
                sourceFrame(fill(BLACK)),
                normalizedFrame(),
                acceptedGeometry(identityTransform())
        );

        assertAll(
                () -> assertEquals(1, evidence.size()),
                () -> assertEquals(ModuleSamplingStatus.NOT_AVAILABLE, evidence.get(0).status()),
                () -> assertTrue(evidence.get(0).reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.SOURCE_SPACE_SAMPLING_DISABLED)),
                () -> assertTrue(evidence.get(0).reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.TILE_DECODE_NOT_ATTEMPTED)),
                () -> assertFalse(evidence.get(0).tileDecodeAttempted())
        );
    }

    @Test
    @DisplayName("Disables local refinement independently while source-space sampling still runs")
    void disablesLocalRefinementIndependentlyWhileSourceSpaceSamplingStillRuns() {
        CaptureMediaSourceSpaceModuleSampler.SourceSpaceValidationSample sample =
                disabledLocalRefinementSampler().sampleAndValidate(
                        sourceFrame(fill(BLACK)),
                        normalizedFrame(),
                        acceptedGeometry(identityTransform())
                );

        assertAll(
                () -> assertEquals(3, sample.evidence().size()),
                () -> assertTrue(sample.evidence().stream()
                        .allMatch(evidence -> evidence.status() == ModuleSamplingStatus.SAMPLED)),
                () -> assertEquals(1, sample.localRefinementEvidence().size()),
                () -> assertEquals(LocalRefinementStatus.NOT_AVAILABLE,
                        sample.localRefinementEvidence().get(0).status()),
                () -> assertTrue(sample.localRefinementEvidence().get(0).reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.LOCAL_REFINEMENT_DISABLED)),
                () -> assertFalse(sample.localRefinementEvidence().get(0).appliedToSampling())
        );
    }

    @Test
    @DisplayName("Readable source-space logical tile candidates record tile decode failure")
    void readableSourceSpaceLogicalTileCandidatesRecordTileDecodeFailure() {
        ValidationFixture fixture = validationFixture(fill(
                BLACK,
                validationLayoutProfile().frameWidthPx(),
                validationLayoutProfile().frameHeightPx()
        ));

        CaptureMediaSourceSpaceModuleSampler.SourceSpaceValidationSample sample =
                validationSampler().sampleAndValidate(
                        fixture.sourceFrame(),
                        fixture.normalizedFrame(),
                        acceptedGeometryFor(validationLayoutProfile(), identityTransform())
                );
        ModuleSamplingEvidence evidence = sample.evidence().get(0);

        assertAll(
                () -> assertTrue(sample.acceptedPayloads().isEmpty()),
                () -> assertTrue(evidence.tileDecodeAttempted()),
                () -> assertEquals(1, evidence.tileDecodeAttemptCount()),
                () -> assertEquals(0, evidence.acceptedPayloadCount()),
                () -> assertEquals(List.of("TILE_DECODE"), evidence.tileDecodeFailureStages()),
                () -> assertTrue(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.DOWNSTREAM_TILE_VALIDATION_FAILED))
        );
    }

    @Test
    @DisplayName("Readable source-space logical tile candidates record envelope CRC failure")
    void readableSourceSpaceLogicalTileCandidatesRecordEnvelopeCrcFailure() {
        TilePayload payload = validationPayload(0, 1, validationLayoutProfile().profileId(), "crc-source-space");
        byte[] envelope = ENVELOPE_CODEC.serialize(payload);
        envelope[envelope.length - 1] = (byte) (envelope[envelope.length - 1] ^ 0x01);
        ValidationFixture fixture = validationFixture(pixelsForLogicalTile(TileCodecs.defaultEncoder()
                .encode(envelope, TileCodecProfiles.balancedV1())));

        CaptureMediaSourceSpaceModuleSampler.SourceSpaceValidationSample sample =
                validationSampler().sampleAndValidate(
                        fixture.sourceFrame(),
                        fixture.normalizedFrame(),
                        acceptedGeometryFor(validationLayoutProfile(), identityTransform())
                );
        ModuleSamplingEvidence evidence = sample.evidence().get(0);

        assertAll(
                () -> assertTrue(sample.acceptedPayloads().isEmpty()),
                () -> assertTrue(evidence.tileDecodeAttempted()),
                () -> assertEquals(1, evidence.tileDecodeAttemptCount()),
                () -> assertEquals(0, evidence.acceptedPayloadCount()),
                () -> assertEquals(List.of("ENVELOPE_VALIDATION"), evidence.tileDecodeFailureStages()),
                () -> assertTrue(evidence.reasonCodes().contains(CaptureMediaEvidenceReasonCode.PAYLOAD_CRC_FAILED))
        );
    }

    @Test
    @DisplayName("Provisional CV corner geometry samples source pixels but keeps envelope CRC validation authoritative")
    void provisionalCvCornerGeometrySamplesSourcePixelsButKeepsEnvelopeCrcValidationAuthoritative() {
        TilePayload payload = validationPayload(0, 1, validationLayoutProfile().profileId(), "crc-provisional-cv");
        byte[] envelope = ENVELOPE_CODEC.serialize(payload);
        envelope[envelope.length - 1] = (byte) (envelope[envelope.length - 1] ^ 0x01);
        ValidationFixture fixture = validationFixtureWithGeometrySource(
                pixelsForLogicalTile(TileCodecs.defaultEncoder().encode(envelope, TileCodecProfiles.balancedV1())),
                "boofcv-fitted-quadrilateral"
        );
        GeometryFitEvidence geometry = provisionalCvGeometryFor(fixture.normalizedFrame());

        CaptureMediaSourceSpaceModuleSampler.SourceSpaceValidationSample sample =
                validationSampler().sampleAndValidate(
                        fixture.sourceFrame(),
                        fixture.normalizedFrame(),
                        geometry
                );
        ModuleSamplingEvidence evidence = sample.evidence().get(0);

        assertAll(
                () -> assertEquals(GeometryFitStatus.ACCEPTED, geometry.status()),
                () -> assertTrue(geometry.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.PROVISIONAL_CV_GEOMETRY)),
                () -> assertTrue(evidence.sampledModuleCount() > 0),
                () -> assertTrue(evidence.tileDecodeAttempted()),
                () -> assertEquals(1, evidence.tileDecodeAttemptCount()),
                () -> assertEquals(0, evidence.acceptedPayloadCount()),
                () -> assertTrue(sample.acceptedPayloads().isEmpty()),
                () -> assertEquals(List.of("ENVELOPE_VALIDATION"), evidence.tileDecodeFailureStages()),
                () -> assertTrue(evidence.reasonCodes().contains(CaptureMediaEvidenceReasonCode.PAYLOAD_CRC_FAILED))
        );
    }

    @Test
    @DisplayName("Readable source-space logical tile candidates record slot identity failure")
    void readableSourceSpaceLogicalTileCandidatesRecordSlotIdentityFailure() {
        TilePayload wrongSlotPayload =
                validationPayload(1, 1, validationLayoutProfile().profileId(), "wrong-slot-source-space");
        ValidationFixture fixture = validationFixture(pixelsForLogicalTile(TileCodecs.defaultEncoder()
                .encode(ENVELOPE_CODEC.serialize(wrongSlotPayload), TileCodecProfiles.balancedV1())));

        CaptureMediaSourceSpaceModuleSampler.SourceSpaceValidationSample sample =
                validationSampler().sampleAndValidate(
                        fixture.sourceFrame(),
                        fixture.normalizedFrame(),
                        acceptedGeometryFor(validationLayoutProfile(), identityTransform())
                );
        ModuleSamplingEvidence evidence = sample.evidence().get(0);

        assertAll(
                () -> assertTrue(sample.acceptedPayloads().isEmpty()),
                () -> assertTrue(evidence.tileDecodeAttempted()),
                () -> assertEquals(1, evidence.tileDecodeAttemptCount()),
                () -> assertEquals(0, evidence.acceptedPayloadCount()),
                () -> assertEquals(List.of("SLOT_VALIDATION"), evidence.tileDecodeFailureStages()),
                () -> assertTrue(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.DOWNSTREAM_TILE_VALIDATION_FAILED))
        );
    }

    @Test
    @DisplayName("Readable source-space logical tile candidates accept payloads only after all gates pass")
    void readableSourceSpaceLogicalTileCandidatesAcceptPayloadsOnlyAfterAllGatesPass() {
        TilePayload payload = validationPayload(0, 1, validationLayoutProfile().profileId(), "valid-source-space");
        ValidationFixture fixture = validationFixture(pixelsForLogicalTile(TileCodecs.defaultEncoder()
                .encode(ENVELOPE_CODEC.serialize(payload), TileCodecProfiles.balancedV1())));

        CaptureMediaSourceSpaceModuleSampler.SourceSpaceValidationSample sample =
                validationSampler().sampleAndValidate(
                        fixture.sourceFrame(),
                        fixture.normalizedFrame(),
                        acceptedGeometryFor(validationLayoutProfile(), identityTransform())
                );
        ModuleSamplingEvidence evidence = sample.evidence().get(0);

        assertAll(
                () -> assertEquals(List.of(payload), sample.acceptedPayloads()),
                () -> assertTrue(evidence.tileDecodeAttempted()),
                () -> assertEquals(1, evidence.tileDecodeAttemptCount()),
                () -> assertEquals(1, evidence.acceptedPayloadCount()),
                () -> assertEquals(ObservedPaletteStatus.SAFE_FOR_CLASSIFICATION,
                        evidence.observedPaletteEvidence().status()),
                () -> assertEquals(ObservedPaletteColorMethod.LINEAR_RGB_V1,
                        evidence.observedPaletteEvidence().colorMethod()),
                () -> assertEquals(ObservedPaletteClassificationMode.HYBRID_OBSERVED_EXACT,
                        evidence.observedPaletteEvidence().classificationMode()),
                () -> assertTrue(evidence.observedPaletteEvidence().colors().stream()
                        .anyMatch(color -> color.centerSource() == ObservedPaletteCenterSource.OBSERVED)),
                () -> assertTrue(evidence.modules().stream()
                        .filter(module -> module.status() == ModuleSampleStatus.READABLE)
                        .allMatch(module -> module.classificationMode()
                                == ObservedPaletteClassificationMode.HYBRID_OBSERVED_EXACT)),
                () -> assertTrue(evidence.tileDecodeFailureStages().isEmpty()),
                () -> assertFalse(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.TILE_DECODE_NOT_ATTEMPTED))
        );
    }

    @Test
    @DisplayName("Observed palette classification can be withheld while exact fallback remains available")
    void observedPaletteClassificationCanBeWithheldWhileExactFallbackRemainsAvailable() {
        TilePayload payload = validationPayload(0, 1, validationLayoutProfile().profileId(), "rollback-source-space");
        ValidationFixture fixture = validationFixture(pixelsForLogicalTile(TileCodecs.defaultEncoder()
                .encode(ENVELOPE_CODEC.serialize(payload), TileCodecProfiles.balancedV1())));

        CaptureMediaSourceSpaceModuleSampler.SourceSpaceValidationSample sample =
                validationSampler(false).sampleAndValidate(
                        fixture.sourceFrame(),
                        fixture.normalizedFrame(),
                        acceptedGeometryFor(validationLayoutProfile(), identityTransform())
                );
        ModuleSamplingEvidence evidence = sample.evidence().get(0);

        assertAll(
                () -> assertEquals(List.of(payload), sample.acceptedPayloads()),
                () -> assertEquals(ObservedPaletteStatus.WITHHELD, evidence.observedPaletteEvidence().status()),
                () -> assertEquals(ObservedPaletteClassificationMode.EXACT,
                        evidence.observedPaletteEvidence().classificationMode()),
                () -> assertTrue(evidence.reasonCodes()
                        .contains(CaptureMediaEvidenceReasonCode.OBSERVED_PALETTE_WITHHELD)),
                () -> assertTrue(evidence.modules().stream()
                        .filter(module -> module.status() == ModuleSampleStatus.READABLE)
                        .allMatch(module -> module.classificationMode() == ObservedPaletteClassificationMode.EXACT))
        );
    }

    private ModuleEvidence firstModule(List<ModuleSamplingEvidence> evidence) {
        return evidence.get(0).modules().stream()
                .filter(module -> module.moduleX() == 0 && module.moduleY() == 0)
                .findFirst()
                .orElseThrow();
    }

    private GeometryFitEvidence acceptedGeometry(List<Double> transformParameters) {
        return acceptedGeometryFor(layoutProfile(), transformParameters);
    }

    private GeometryFitEvidence acceptedGeometryFor(LayoutProfile layoutProfile, List<Double> transformParameters) {
        GeometryCandidateEvidence candidate = geometryCandidate(1, transformParameters, true, layoutProfile);
        return new GeometryFitEvidence(
                1,
                patternCandidateId(),
                GeometryFitStatus.ACCEPTED,
                List.of(candidate),
                candidate.candidateId().geometryCandidateId(),
                Optional.empty(),
                List.of()
        );
    }

    private GeometryCandidateEvidence geometryCandidate(
            int rank,
            List<Double> transformParameters,
            boolean retainedForSampling
    ) {
        return geometryCandidate(rank, transformParameters, retainedForSampling, layoutProfile());
    }

    private GeometryCandidateEvidence geometryCandidate(
            int rank,
            List<Double> transformParameters,
            boolean retainedForSampling,
            LayoutProfile layoutProfile
    ) {
        return new GeometryCandidateEvidence(
                CaptureMediaCandidateId.geometryCandidate(patternCandidateId(), rank),
                rank,
                GeometryFitStatus.ACCEPTED,
                GeometryFitModelType.HOMOGRAPHY,
                "pattern-feature-canonical-pixels:" + layoutProfile.profileId(),
                "source-image-pixels",
                transformParameters,
                1.0d,
                List.of(),
                true,
                4,
                4,
                4,
                4,
                0,
                0,
                reprojectionMetrics(),
                1.0d,
                1.0d,
                retainedForSampling,
                false,
                List.of()
        );
    }

    private List<Double> identityTransform() {
        return List.of(
                1.0d, 0.0d, 0.0d,
                0.0d, 1.0d, 0.0d,
                0.0d, 0.0d, 1.0d
        );
    }

    private MediaInputFrame sourceFrame(int[] pixels) {
        return sourceFrame(pixels, WIDTH, HEIGHT);
    }

    private MediaInputFrame sourceFrame(int[] pixels, int widthPixels, int heightPixels) {
        return new MediaInputFrame(
                SOURCE_ID,
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                widthPixels,
                heightPixels,
                "png",
                PIXEL_SHA256,
                pixels
        );
    }

    private NormalizedCaptureFrame normalizedFrame() {
        return normalizedFrame(fill(BLACK), layoutProfile());
    }

    private NormalizedCaptureFrame normalizedFrame(int[] pixels, LayoutProfile layoutProfile) {
        return new NormalizedCaptureFrame(
                SOURCE_ID,
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                layoutProfile.frameWidthPx(),
                layoutProfile.frameHeightPx(),
                layoutProfile.frameWidthPx(),
                layoutProfile.frameHeightPx(),
                "png",
                PIXEL_SHA256,
                layoutProfile.profileId(),
                FrameCorners.exactFrame(layoutProfile.frameWidthPx(), layoutProfile.frameHeightPx()),
                CaptureMediaQualityMetrics.exactRenderedFrame(),
                pixels
        );
    }

    private CaptureMediaCandidateId patternCandidateId() {
        return CaptureMediaCandidateId.patternEvidence(CaptureMediaCandidateId.proposalCandidate(
                "STILL_IMAGE_FILE",
                0,
                SOURCE_ID,
                PIXEL_SHA256,
                1,
                1,
                1
        ));
    }

    private ReprojectionMetrics reprojectionMetrics() {
        return new ReprojectionMetrics(
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                List.of(0.0d),
                List.of(0.0d)
        );
    }

    private int[] fill(int argb) {
        return fill(argb, WIDTH, HEIGHT);
    }

    private int[] fill(int argb, int widthPixels, int heightPixels) {
        int[] pixels = new int[widthPixels * heightPixels];
        java.util.Arrays.fill(pixels, argb);
        return pixels;
    }

    private CaptureMediaSourceSpaceModuleSampler validationSampler() {
        return validationSampler(true);
    }

    private CaptureMediaSourceSpaceModuleSampler validationSampler(boolean observedPaletteClassificationEnabled) {
        return new CaptureMediaSourceSpaceModuleSampler(
                new CaptureRenderedLayoutCatalog(List.of(validationLayoutProfile())),
                TileCodecProfiles.balancedV1(),
                new ModuleLatticeProjector(),
                new CaptureMediaPaletteSampler(),
                new FixedLayoutPlanner(),
                new CaptureMediaLogicalTileValidator(),
                true,
                true,
                new CaptureMediaLocalLatticeRefiner(),
                new SupportedTileFinderEvaluator(),
                observedPaletteClassificationEnabled
        );
    }

    private CaptureMediaSourceSpaceModuleSampler disabledLocalRefinementSampler() {
        return new CaptureMediaSourceSpaceModuleSampler(
                new CaptureRenderedLayoutCatalog(List.of(layoutProfile())),
                TileCodecProfiles.balancedV1(),
                new ModuleLatticeProjector(),
                new CaptureMediaPaletteSampler(),
                new FixedLayoutPlanner(),
                new CaptureMediaLogicalTileValidator(),
                true,
                false,
                new CaptureMediaLocalLatticeRefiner()
        );
    }

    private ValidationFixture validationFixture(int[] sourcePixels) {
        LayoutProfile layoutProfile = validationLayoutProfile();
        return new ValidationFixture(
                sourceFrame(sourcePixels, layoutProfile.frameWidthPx(), layoutProfile.frameHeightPx()),
                normalizedFrame(fill(BLACK, layoutProfile.frameWidthPx(), layoutProfile.frameHeightPx()), layoutProfile)
        );
    }

    private ValidationFixture validationFixtureWithGeometrySource(int[] sourcePixels, String geometrySource) {
        LayoutProfile layoutProfile = validationLayoutProfile();
        return new ValidationFixture(
                sourceFrame(sourcePixels, layoutProfile.frameWidthPx(), layoutProfile.frameHeightPx()),
                normalizedFrameWithGeometrySource(
                        fill(BLACK, layoutProfile.frameWidthPx(), layoutProfile.frameHeightPx()),
                        layoutProfile,
                        geometrySource
                )
        );
    }

    private GeometryFitEvidence provisionalCvGeometryFor(NormalizedCaptureFrame normalizedFrame) {
        return new CaptureMediaGeometryFitter().fit(
                normalizedFrame,
                weakPatternEvidence(normalizedFrame.layoutProfileId())
        );
    }

    private PatternEvidence weakPatternEvidence(String layoutProfileId) {
        return new PatternEvidence(
                1,
                patternCandidateId(),
                layoutProfileId,
                PatternEvidenceStatus.NOT_FOUND,
                List.of(),
                Map.of(),
                Map.of(),
                false,
                List.of(
                        CaptureMediaEvidenceReasonCode.NO_DIRECT_FINDER_EVIDENCE,
                        CaptureMediaEvidenceReasonCode.NO_FEATURE_EVIDENCE
                ),
                List.of(),
                0.0d,
                0.0d
        );
    }

    private NormalizedCaptureFrame normalizedFrameWithGeometrySource(
            int[] pixels,
            LayoutProfile layoutProfile,
            String geometrySource
    ) {
        return new NormalizedCaptureFrame(
                SOURCE_ID,
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                layoutProfile.frameWidthPx(),
                layoutProfile.frameHeightPx(),
                layoutProfile.frameWidthPx(),
                layoutProfile.frameHeightPx(),
                "png",
                PIXEL_SHA256,
                layoutProfile.profileId(),
                Optional.empty(),
                Optional.empty(),
                FrameCorners.exactFrame(layoutProfile.frameWidthPx(), layoutProfile.frameHeightPx()),
                CaptureMediaQualityMetrics.perspectiveCorrected(0.72d, 0.0d),
                pixels,
                Optional.empty(),
                Optional.of(geometrySource),
                1,
                1,
                1
        );
    }

    private int[] pixelsForLogicalTile(LogicalTile logicalTile) {
        LayoutProfile layoutProfile = validationLayoutProfile();
        FixedLayoutPlan layoutPlan = new FixedLayoutPlanner().plan(layoutProfile);
        int[] pixels = fill(WHITE, layoutProfile.frameWidthPx(), layoutProfile.frameHeightPx());
        List<Integer> palette = new CaptureMediaPaletteSampler().paletteArgb();
        int border = layoutPlan.separatorThicknessPx();
        int innerWidth = layoutPlan.tileSlotWidthPx() - (2 * border);
        int innerHeight = layoutPlan.tileSlotHeightPx() - (2 * border);
        int logicalSide = logicalTile.widthModules() + (2 * logicalTile.quietZoneModules());
        int moduleSize = Math.min(innerWidth / logicalSide, innerHeight / logicalSide);
        int contentWidth = logicalSide * moduleSize;
        int contentHeight = logicalSide * moduleSize;
        int offsetX = border + ((innerWidth - contentWidth) / 2);
        int offsetY = border + ((innerHeight - contentHeight) / 2);
        int quietZonePixels = logicalTile.quietZoneModules() * moduleSize;
        for (int moduleY = 0; moduleY < logicalTile.heightModules(); moduleY++) {
            for (int moduleX = 0; moduleX < logicalTile.widthModules(); moduleX++) {
                int color = palette.get(logicalTile.moduleColorAt(moduleY, moduleX));
                for (int y = offsetY + quietZonePixels + (moduleY * moduleSize);
                        y < offsetY + quietZonePixels + ((moduleY + 1) * moduleSize);
                        y++) {
                    for (int x = offsetX + quietZonePixels + (moduleX * moduleSize);
                            x < offsetX + quietZonePixels + ((moduleX + 1) * moduleSize);
                            x++) {
                        pixels[(y * layoutProfile.frameWidthPx()) + x] = color;
                    }
                }
            }
        }
        return pixels;
    }

    private TilePayload validationPayload(int tileIndex, int totalTiles, String layoutProfileId, String body) {
        byte[] bodyBytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new TilePayload(
                1,
                SESSION_ID,
                FrameType.DATA,
                0L,
                new TileIndex(tileIndex),
                totalTiles,
                layoutProfileId,
                PayloadKind.FILE_CHUNK,
                tileIndex,
                bodyBytes.length,
                crc32c(bodyBytes),
                0,
                bodyBytes
        );
    }

    private int crc32c(byte[] body) {
        CRC32C crc32c = new CRC32C();
        crc32c.update(body, 0, body.length);
        return (int) crc32c.getValue();
    }

    private LayoutProfile layoutProfile() {
        return new LayoutProfile(
                "source-space-test-layout",
                1,
                1,
                WIDTH,
                HEIGHT,
                0,
                0,
                "solidWhite",
                0,
                0,
                "black",
                "preserveAspect"
        );
    }

    private LayoutProfile validationLayoutProfile() {
        int dimension = TileCodecProfiles.balancedV1()
                .dimensionForSideVersion(TileCodecProfiles.balancedV1().minSideVersion());
        int logicalSide = dimension + (2 * TileCodecProfiles.balancedV1().quietZoneModules());
        int sidePixels = (logicalSide * VALIDATION_MODULE_SIZE) + 2;
        return new LayoutProfile(
                "source-space-validation-layout",
                1,
                1,
                sidePixels,
                sidePixels,
                0,
                0,
                "solidWhite",
                0,
                0,
                "black",
                "preserveAspect"
        );
    }

    private record ValidationFixture(MediaInputFrame sourceFrame, NormalizedCaptureFrame normalizedFrame) {
    }
}
