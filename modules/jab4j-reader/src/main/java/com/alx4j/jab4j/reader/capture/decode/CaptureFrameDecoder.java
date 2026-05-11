package com.alx4j.jab4j.reader.capture.decode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.reader.capture.CaptureDiagnosticCode;
import com.alx4j.jab4j.reader.capture.CaptureFrameDiagnostic;
import com.alx4j.jab4j.reader.capture.input.CaptureInputFrame;
import com.alx4j.jab4j.reader.capture.qualify.CaptureRenderedLayoutCatalog;
import com.alx4j.jab4j.reader.capture.qualify.CaptureTileSlotSampler;
import com.alx4j.jab4j.reader.capture.qualify.CaptureTileSlotSampler.SampledSlot;
import com.alx4j.jab4j.reader.capture.qualify.CaptureTileSlotSampler.SampledSlotStatus;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.render.layout.TilePlacement;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecException;
import com.alx4j.jab4j.tile.TileCodecProfile;
import com.alx4j.jab4j.tile.TileCodecProfiles;
import com.alx4j.jab4j.tile.TileCodecs;
import com.alx4j.jab4j.tile.TileDecoder;
import com.alx4j.jab4j.transfer.TilePayloadEnvelopeCodec;
import com.alx4j.jab4j.transfer.TransportException;

/**
 * Qualifies supported capture frame layouts and decodes exact clean rendered PNG tile payloads.
 */
public final class CaptureFrameDecoder {

    private static final int SUPPORTED_PROTOCOL_COMPATIBILITY_VERSION = 1;

    private final CaptureRenderedLayoutCatalog layoutCatalog;
    private final FixedLayoutPlanner layoutPlanner;
    private final CaptureTileSlotSampler slotSampler;
    private final TileDecoder tileDecoder;
    private final TileCodecProfile tileCodecProfile;
    private final TilePayloadEnvelopeCodec envelopeCodec;

    /**
     * Creates a decoder with current supported rendered layouts and balanced-v1 tile decoding.
     */
    public CaptureFrameDecoder() {
        this(
                new CaptureRenderedLayoutCatalog(),
                new FixedLayoutPlanner(),
                new CaptureTileSlotSampler(),
                TileCodecs.defaultDecoder(),
                TileCodecProfiles.balancedV1(),
                new TilePayloadEnvelopeCodec()
        );
    }

    /**
     * Creates a decoder with explicit collaborators for focused tests.
     *
     * @param layoutCatalog supported rendered layout catalog
     * @param layoutPlanner fixed layout planner
     * @param slotSampler capture tile slot sampler
     * @param tileDecoder logical tile decoder
     * @param tileCodecProfile logical tile codec profile
     * @param envelopeCodec tile payload envelope codec
     */
    public CaptureFrameDecoder(
            CaptureRenderedLayoutCatalog layoutCatalog,
            FixedLayoutPlanner layoutPlanner,
            CaptureTileSlotSampler slotSampler,
            TileDecoder tileDecoder,
            TileCodecProfile tileCodecProfile,
            TilePayloadEnvelopeCodec envelopeCodec
    ) {
        this.layoutCatalog = Objects.requireNonNull(layoutCatalog, "layoutCatalog must not be null");
        this.layoutPlanner = Objects.requireNonNull(layoutPlanner, "layoutPlanner must not be null");
        this.slotSampler = Objects.requireNonNull(slotSampler, "slotSampler must not be null");
        this.tileDecoder = Objects.requireNonNull(tileDecoder, "tileDecoder must not be null");
        this.tileCodecProfile = Objects.requireNonNull(tileCodecProfile, "tileCodecProfile must not be null");
        this.envelopeCodec = Objects.requireNonNull(envelopeCodec, "envelopeCodec must not be null");
    }

    /**
     * Decodes readable capture frames and returns stable diagnostics for unsupported or unusable frames.
     *
     * @param frames readable PNG frames from intake
     * @return decoded frames and diagnostics
     */
    public CaptureFrameDecodeResult decode(List<CaptureInputFrame> frames) {
        Objects.requireNonNull(frames, "frames must not be null");
        List<DecodedCaptureFrame> decodedFrames = new ArrayList<>();
        List<CaptureFrameDiagnostic> diagnostics = new ArrayList<>();
        for (CaptureInputFrame frame : frames) {
            Objects.requireNonNull(frame, "frames must not contain null values");
            layoutCatalog.resolve(frame.widthPixels(), frame.heightPixels())
                    .ifPresentOrElse(
                            layoutProfile -> decodeSupportedFrame(frame, layoutProfile, decodedFrames, diagnostics),
                            () -> diagnostics.add(CaptureFrameDiagnostic.forSource(
                                    CaptureDiagnosticCode.UNSUPPORTED_DIMENSIONS,
                                    frame.sourceId(),
                                    frame.callerOrder(),
                                    "Frame dimensions do not match a supported rendered capture layout"
                            ))
                    );
        }
        return new CaptureFrameDecodeResult(decodedFrames, diagnostics);
    }

    private void decodeSupportedFrame(
            CaptureInputFrame frame,
            LayoutProfile layoutProfile,
            List<DecodedCaptureFrame> decodedFrames,
            List<CaptureFrameDiagnostic> diagnostics
    ) {
        FixedLayoutPlan layoutPlan = layoutPlanner.plan(layoutProfile);
        List<TilePayload> payloads = new ArrayList<>();
        List<TilePlacement> placements = layoutPlan.tilePlacements();
        for (int tileIndex = 0; tileIndex < placements.size(); tileIndex++) {
            SampledSlot sampledSlot = slotSampler.sample(frame, layoutPlan, placements.get(tileIndex), tileIndex, tileCodecProfile);
            if (sampledSlot.status() == SampledSlotStatus.EMPTY
                    || sampledSlot.status() == SampledSlotStatus.NO_SIGNATURE_CONTENT) {
                continue;
            }
            if (sampledSlot.status() == SampledSlotStatus.UNSAMPLED_CONTENT) {
                diagnostics.add(corruptedTileDiagnostic(frame));
                return;
            }
            TilePayload payload = decodeTileSlot(frame, layoutPlan, tileIndex, sampledSlot.candidates());
            if (payload == null) {
                diagnostics.add(corruptedTileDiagnostic(frame));
                return;
            }
            payloads.add(payload);
        }

        if (payloads.isEmpty()) {
            diagnostics.add(CaptureFrameDiagnostic.forSource(
                    CaptureDiagnosticCode.NO_CANDIDATE_BARCODE_CONTENT,
                    frame.sourceId(),
                    frame.callerOrder(),
                    "Frame does not contain supported JAB barcode content"
            ));
            return;
        }
        try {
            decodedFrames.add(createDecodedFrame(frame, payloads));
        } catch (IllegalArgumentException exception) {
            diagnostics.add(CaptureFrameDiagnostic.forSource(
                    CaptureDiagnosticCode.INCONSISTENT_SESSION_CONTENT,
                    frame.sourceId(),
                    frame.callerOrder(),
                    "Decoded tile payload identity is inconsistent within one frame"
            ));
        }
    }

    private TilePayload decodeTileSlot(
            CaptureInputFrame frame,
            FixedLayoutPlan layoutPlan,
            int tileIndex,
            List<LogicalTile> candidates
    ) {
        for (LogicalTile candidate : candidates) {
            try {
                byte[] envelope = tileDecoder.decode(candidate, tileCodecProfile);
                TilePayload payload = envelopeCodec.parse(envelope, SUPPORTED_PROTOCOL_COMPATIBILITY_VERSION);
                if (validPayloadForSlot(layoutPlan, tileIndex, payload)) {
                    return payload;
                }
            } catch (TileCodecException | TransportException exception) {
                // Try the next sampled side-version candidate before rejecting the frame.
            } catch (RuntimeException exception) {
                return null;
            }
        }
        return null;
    }

    private boolean validPayloadForSlot(FixedLayoutPlan layoutPlan, int tileIndex, TilePayload payload) {
        return layoutPlan.profile().profileId().equals(payload.layoutProfileId())
                && payload.tileIndex().value() == tileIndex
                && payload.totalTilesInFrame() == layoutPlan.profile().rows() * layoutPlan.profile().cols();
    }

    private DecodedCaptureFrame createDecodedFrame(CaptureInputFrame frame, List<TilePayload> payloads) {
        TilePayload firstPayload = payloads.get(0);
        SessionId sessionId = firstPayload.sessionId();
        long frameIndex = firstPayload.frameIndex();
        String layoutProfileId = firstPayload.layoutProfileId();
        for (TilePayload payload : payloads) {
            if (!sessionId.equals(payload.sessionId())
                    || payload.frameIndex() != frameIndex
                    || payload.frameType() != firstPayload.frameType()
                    || !layoutProfileId.equals(payload.layoutProfileId())) {
                throw new IllegalArgumentException("decoded payloads disagree on frame identity");
            }
            if (payload.payloadKind() == PayloadKind.SESSION_END && payload.body().length == 0) {
                throw new IllegalArgumentException("SESSION_END payload body must not be empty");
            }
        }
        return new DecodedCaptureFrame(
                frame.sourceId(),
                frame.callerOrder(),
                frame.pixelSha256(),
                sessionId,
                frameIndex,
                firstPayload.frameType(),
                layoutProfileId,
                payloads
        );
    }

    private CaptureFrameDiagnostic corruptedTileDiagnostic(CaptureInputFrame frame) {
        return CaptureFrameDiagnostic.forSource(
                CaptureDiagnosticCode.CORRUPTED_OR_UNREADABLE_TILE_CONTENT,
                frame.sourceId(),
                frame.callerOrder(),
                "Frame contains tile content that could not be decoded as a supported JAB payload"
        );
    }
}
