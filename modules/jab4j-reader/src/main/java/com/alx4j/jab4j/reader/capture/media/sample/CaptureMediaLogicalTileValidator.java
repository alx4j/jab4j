package com.alx4j.jab4j.reader.capture.media.sample;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecException;
import com.alx4j.jab4j.tile.TileCodecProfile;
import com.alx4j.jab4j.tile.TileCodecProfiles;
import com.alx4j.jab4j.tile.TileCodecs;
import com.alx4j.jab4j.tile.TileDecoder;
import com.alx4j.jab4j.transfer.TilePayloadEnvelopeCodec;
import com.alx4j.jab4j.transfer.TransportException;

/**
 * Internal validator for logical tile candidates that must pass tile decode, envelope validation, and slot identity.
 */
final class CaptureMediaLogicalTileValidator {

    private static final int SUPPORTED_PROTOCOL_COMPATIBILITY_VERSION = 1;

    private final TileDecoder tileDecoder;
    private final TileCodecProfile tileCodecProfile;
    private final TilePayloadEnvelopeCodec envelopeCodec;

    /**
     * Creates a validator with the current default tile decoder, balanced-v1 profile, and envelope codec.
     */
    CaptureMediaLogicalTileValidator() {
        this(TileCodecs.defaultDecoder(), TileCodecProfiles.balancedV1(), new TilePayloadEnvelopeCodec());
    }

    /**
     * Creates a validator with explicit collaborators.
     *
     * @param tileDecoder logical tile decoder
     * @param tileCodecProfile tile codec profile
     * @param envelopeCodec payload envelope codec
     */
    CaptureMediaLogicalTileValidator(
            TileDecoder tileDecoder,
            TileCodecProfile tileCodecProfile,
            TilePayloadEnvelopeCodec envelopeCodec
    ) {
        this.tileDecoder = Objects.requireNonNull(tileDecoder, "tileDecoder must not be null");
        this.tileCodecProfile = Objects.requireNonNull(tileCodecProfile, "tileCodecProfile must not be null");
        this.envelopeCodec = Objects.requireNonNull(envelopeCodec, "envelopeCodec must not be null");
    }

    /**
     * Validates one logical tile candidate against the existing reader protocol gates.
     *
     * @param layoutPlan expected rendered layout plan
     * @param tileIndex expected zero-based slot index
     * @param candidate logical tile candidate
     * @return accepted payload or rejection evidence
     */
    ValidationAttempt validate(FixedLayoutPlan layoutPlan, int tileIndex, LogicalTile candidate) {
        Objects.requireNonNull(layoutPlan, "layoutPlan must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        try {
            byte[] envelope = tileDecoder.decode(candidate, tileCodecProfile);
            TilePayload payload = envelopeCodec.parse(envelope, SUPPORTED_PROTOCOL_COMPATIBILITY_VERSION);
            SlotValidationResult slotValidation = slotValidationResult(layoutPlan, tileIndex, payload);
            return slotValidation.rejectionReason()
                    .map(reason -> ValidationAttempt.rejected(
                            FailureStage.SLOT_VALIDATION,
                            reason,
                            slotValidation.diagnostics()
                    ))
                    .orElseGet(() -> ValidationAttempt.accepted(payload));
        } catch (TileCodecException exception) {
            String reason = "tileDecode: " + exception.getMessage();
            return ValidationAttempt.rejected(
                    FailureStage.TILE_DECODE,
                    reason,
                    CaptureMediaTileDecodeDiagnostics.inspect(candidate, tileCodecProfile, Optional.of(reason))
            );
        } catch (TransportException exception) {
            return ValidationAttempt.rejected(
                    FailureStage.ENVELOPE_VALIDATION,
                    "envelopeValidation: " + exception.getMessage(),
                    Map.of(
                            "envelopeValidation.stage", "ENVELOPE_VALIDATION",
                            "envelopeValidation.reason", exception.getMessage()
                    )
            );
        } catch (RuntimeException exception) {
            return ValidationAttempt.rejected(
                    FailureStage.UNEXPECTED,
                    "unexpected: " + exception.getClass().getSimpleName(),
                    Map.of(
                            "tileDecode.unexpectedStage", "UNEXPECTED",
                            "tileDecode.unexpectedExceptionClass", exception.getClass().getSimpleName()
                    )
            );
        }
    }

    private SlotValidationResult slotValidationResult(FixedLayoutPlan layoutPlan, int tileIndex, TilePayload payload) {
        String expectedLayoutProfileId = layoutPlan.profile().profileId();
        int expectedTotalTiles = layoutPlan.profile().rows() * layoutPlan.profile().cols();
        Map<String, String> diagnostics = slotValidationDiagnostics(
                expectedLayoutProfileId,
                payload.layoutProfileId(),
                tileIndex,
                payload.tileIndex().value(),
                expectedTotalTiles,
                payload.totalTilesInFrame()
        );
        if (!expectedLayoutProfileId.equals(payload.layoutProfileId())) {
            return SlotValidationResult.rejected(
                    "slotValidation: layout profile mismatch expected "
                            + expectedLayoutProfileId
                            + " actual "
                            + payload.layoutProfileId(),
                    diagnostics
            );
        }
        if (payload.tileIndex().value() != tileIndex) {
            return SlotValidationResult.rejected(
                    "slotValidation: tile index mismatch expected "
                            + tileIndex
                            + " actual "
                            + payload.tileIndex().value(),
                    diagnostics
            );
        }
        if (payload.totalTilesInFrame() != expectedTotalTiles) {
            return SlotValidationResult.rejected(
                    "slotValidation: total tile count mismatch expected "
                            + expectedTotalTiles
                            + " actual "
                            + payload.totalTilesInFrame(),
                    diagnostics
            );
        }
        if (payload.payloadKind() == PayloadKind.SESSION_END && payload.body().length == 0) {
            return SlotValidationResult.rejected("slotValidation: empty session-end payload body", diagnostics);
        }
        return SlotValidationResult.accepted();
    }

    private Map<String, String> slotValidationDiagnostics(
            String expectedLayoutProfileId,
            String actualLayoutProfileId,
            int expectedTileIndex,
            int actualTileIndex,
            int expectedTotalTiles,
            int actualTotalTiles
    ) {
        Map<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("slotValidation.stage", "SLOT_VALIDATION");
        diagnostics.put("slotValidation.expectedLayoutProfileId", expectedLayoutProfileId);
        diagnostics.put("slotValidation.actualLayoutProfileId", actualLayoutProfileId);
        diagnostics.put("slotValidation.layoutProfileMismatch",
                Boolean.toString(!expectedLayoutProfileId.equals(actualLayoutProfileId)));
        diagnostics.put("slotValidation.expectedTileIndex", Integer.toString(expectedTileIndex));
        diagnostics.put("slotValidation.actualTileIndex", Integer.toString(actualTileIndex));
        diagnostics.put("slotValidation.tileIndexMismatch", Boolean.toString(expectedTileIndex != actualTileIndex));
        diagnostics.put("slotValidation.expectedTotalTiles", Integer.toString(expectedTotalTiles));
        diagnostics.put("slotValidation.actualTotalTiles", Integer.toString(actualTotalTiles));
        diagnostics.put("slotValidation.totalTilesMismatch", Boolean.toString(expectedTotalTiles != actualTotalTiles));
        return Map.copyOf(diagnostics);
    }

    /**
     * Downstream protocol stage that rejected a logical tile candidate.
     */
    enum FailureStage {
        TILE_DECODE,
        ENVELOPE_VALIDATION,
        SLOT_VALIDATION,
        UNEXPECTED
    }

    /**
     * Validation result for one logical tile candidate.
     *
     * @param payload accepted tile payload, when all gates pass
     * @param failureStage downstream failure stage, when rejected
     * @param rejectionReason compact rejection reason
     * @param diagnostics compact validation diagnostics
     */
    record ValidationAttempt(
            Optional<TilePayload> payload,
            Optional<FailureStage> failureStage,
            Optional<String> rejectionReason,
            Map<String, String> diagnostics
    ) {

        /**
         * Creates a validated immutable validation attempt.
         */
        ValidationAttempt {
            Objects.requireNonNull(payload, "payload must not be null");
            Objects.requireNonNull(failureStage, "failureStage must not be null");
            Objects.requireNonNull(rejectionReason, "rejectionReason must not be null");
            diagnostics = Map.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
            if (payload.isPresent() && (failureStage.isPresent() || rejectionReason.isPresent())) {
                throw new IllegalArgumentException("accepted validation attempts must not contain rejection details");
            }
            if (payload.isEmpty() && (failureStage.isEmpty() || rejectionReason.isEmpty())) {
                throw new IllegalArgumentException("rejected validation attempts require stage and reason");
            }
        }

        private static ValidationAttempt accepted(TilePayload payload) {
            Objects.requireNonNull(payload, "payload must not be null");
            return new ValidationAttempt(Optional.of(payload), Optional.empty(), Optional.empty(), Map.of(
                    "decodedPayload.layoutProfileId", payload.layoutProfileId(),
                    "decodedPayload.tileIndex", Integer.toString(payload.tileIndex().value()),
                    "decodedPayload.totalTiles", Integer.toString(payload.totalTilesInFrame())
            ));
        }

        private static ValidationAttempt rejected(
                FailureStage failureStage,
                String reason,
                Map<String, String> diagnostics
        ) {
            Objects.requireNonNull(failureStage, "failureStage must not be null");
            Map<String, String> enrichedDiagnostics = new LinkedHashMap<>(
                    Objects.requireNonNull(diagnostics, "diagnostics must not be null")
            );
            enrichedDiagnostics.put("postPalette.failureStage", failureStage.name());
            return new ValidationAttempt(
                    Optional.empty(),
                    Optional.of(failureStage),
                    Optional.of(reason),
                    enrichedDiagnostics
            );
        }
    }

    private record SlotValidationResult(Optional<String> rejectionReason, Map<String, String> diagnostics) {

        private SlotValidationResult {
            Objects.requireNonNull(rejectionReason, "rejectionReason must not be null");
            diagnostics = Map.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
        }

        private static SlotValidationResult accepted() {
            return new SlotValidationResult(Optional.empty(), Map.of());
        }

        private static SlotValidationResult rejected(String reason, Map<String, String> diagnostics) {
            return new SlotValidationResult(Optional.of(reason), diagnostics);
        }
    }
}
