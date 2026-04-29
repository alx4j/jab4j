package com.alx4j.jab4j.reader.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.reader.content.DecodedFrameContent;
import com.alx4j.jab4j.reader.content.DecodedFrameSetContent;
import com.alx4j.jab4j.reader.frame.ReaderFrame;
import com.alx4j.jab4j.reader.frame.ReaderFrameSet;
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
 * Decodes accepted reader frames into parsed tile payload envelopes for the supported writer-rendered PNG subset.
 */
final class ReaderContentDecoder {

    private static final int SUPPORTED_PROTOCOL_COMPATIBILITY_VERSION = 1;

    private final SupportedRenderedLayoutCatalog layoutCatalog;
    private final FixedLayoutPlanner layoutPlanner;
    private final RenderedTileSlotSampler slotSampler;
    private final TileDecoder tileDecoder;
    private final TileCodecProfile tileCodecProfile;
    private final TilePayloadEnvelopeCodec envelopeCodec;

    /**
     * Creates a content decoder with the supported rendered-layout catalog and balanced-v1 logical tile decoder.
     */
    ReaderContentDecoder() {
        this(
                new SupportedRenderedLayoutCatalog(),
                new FixedLayoutPlanner(),
                new RenderedTileSlotSampler(),
                TileCodecs.defaultDecoder(),
                TileCodecProfiles.balancedV1(),
                new TilePayloadEnvelopeCodec()
        );
    }

    private ReaderContentDecoder(
            SupportedRenderedLayoutCatalog layoutCatalog,
            FixedLayoutPlanner layoutPlanner,
            RenderedTileSlotSampler slotSampler,
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
     * Decodes all accepted frames in one frame set and validates decoded payload identity against that set.
     *
     * @param frameSet accepted source-neutral frame set
     * @return decoded frame-set content
     */
    DecodedFrameSetContent decode(ReaderFrameSet frameSet) {
        Objects.requireNonNull(frameSet, "frameSet must not be null");
        List<DecodedFrameContent> decodedFrames = new ArrayList<>(frameSet.frames().size());
        String setLayoutProfileId = null;
        for (ReaderFrame frame : frameSet.frames()) {
            LayoutProfile layoutProfile = layoutCatalog.resolve(frame.widthPixels(), frame.heightPixels());
            if (setLayoutProfileId == null) {
                setLayoutProfileId = layoutProfile.profileId();
            } else if (!setLayoutProfileId.equals(layoutProfile.profileId())) {
                throw new ReaderContentDecodeException(
                        ReaderDecodeStatus.INCONSISTENT_CONTENT,
                        "Frame "
                                + frame.frameIndex()
                                + " resolved layout profile "
                                + layoutProfile.profileId()
                                + " but the decoded input set started with "
                                + setLayoutProfileId
                );
            }

            FixedLayoutPlan layoutPlan = layoutPlanner.plan(layoutProfile);
            DecodedFrameContent decodedFrame = decodeFrame(frameSet.sessionId(), frame, layoutPlan);
            decodedFrames.add(decodedFrame);
        }
        return new DecodedFrameSetContent(frameSet.sessionId(), setLayoutProfileId, decodedFrames);
    }

    private DecodedFrameContent decodeFrame(SessionId expectedSessionId, ReaderFrame frame, FixedLayoutPlan layoutPlan) {
        List<TilePayload> payloads = new ArrayList<>();
        List<TilePlacement> placements = layoutPlan.tilePlacements();
        for (int tileIndex = 0; tileIndex < placements.size(); tileIndex++) {
            RenderedTileSlotSampler.SampledSlot sampledSlot =
                    slotSampler.sample(frame, layoutPlan, placements.get(tileIndex), tileIndex, tileCodecProfile);
            if (sampledSlot.empty()) {
                continue;
            }
            TilePayload payload = decodeTileSlot(expectedSessionId, frame, layoutPlan, tileIndex, sampledSlot.candidates());
            payloads.add(payload);
        }
        if (payloads.isEmpty()) {
            throw new ReaderContentDecodeException(
                    ReaderDecodeStatus.CONTENT_CORRUPTED,
                    "Frame " + frame.frameIndex() + " contains no decodable tile payloads"
            );
        }
        return new DecodedFrameContent(
                frame.frameIndex(),
                frame.frameType(),
                layoutPlan.profile().profileId(),
                payloads
        );
    }

    private TilePayload decodeTileSlot(
            SessionId expectedSessionId,
            ReaderFrame frame,
            FixedLayoutPlan layoutPlan,
            int tileIndex,
            List<LogicalTile> candidates
    ) {
        String lastFailure = "no candidate logical tiles were sampled";
        String envelopeFailure = null;
        for (LogicalTile candidate : candidates) {
            try {
                byte[] envelope = tileDecoder.decode(candidate, tileCodecProfile);
                TilePayload payload = parseEnvelope(frame, tileIndex, envelope);
                validatePayload(expectedSessionId, frame, layoutPlan, tileIndex, payload);
                return payload;
            } catch (ReaderContentDecodeException exception) {
                if (exception.status() == ReaderDecodeStatus.UNSUPPORTED_VERSION
                        || exception.status() == ReaderDecodeStatus.INCONSISTENT_CONTENT) {
                    throw exception;
                }
                if (exception.getMessage().contains("Envelope payload CRC32C")) {
                    throw exception;
                }
                envelopeFailure = exception.getMessage();
                lastFailure = exception.getMessage();
            } catch (TileCodecException exception) {
                lastFailure = exception.getMessage();
            }
        }

        throw new ReaderContentDecodeException(
                ReaderDecodeStatus.CONTENT_CORRUPTED,
                "Tile slot "
                        + tileIndex
                        + " in frame "
                        + frame.frameIndex()
                        + " could not be decoded as a supported J4TL payload: "
                        + (envelopeFailure == null ? lastFailure : envelopeFailure)
        );
    }

    private TilePayload parseEnvelope(ReaderFrame frame, int tileIndex, byte[] envelope) {
        try {
            return envelopeCodec.parse(envelope, SUPPORTED_PROTOCOL_COMPATIBILITY_VERSION);
        } catch (TransportException exception) {
            ReaderDecodeStatus status = exception.getMessage().startsWith("Unsupported protocol compatibility version")
                    ? ReaderDecodeStatus.UNSUPPORTED_VERSION
                    : ReaderDecodeStatus.CONTENT_CORRUPTED;
            throw new ReaderContentDecodeException(
                    status,
                    "Tile slot "
                            + tileIndex
                            + " in frame "
                            + frame.frameIndex()
                            + " contains an invalid J4TL envelope: "
                            + exception.getMessage()
            );
        } catch (RuntimeException exception) {
            throw new ReaderContentDecodeException(
                    ReaderDecodeStatus.CONTENT_CORRUPTED,
                    "Tile slot "
                            + tileIndex
                            + " in frame "
                            + frame.frameIndex()
                            + " contains an invalid J4TL envelope: "
                            + exception.getMessage()
            );
        }
    }

    private void validatePayload(
            SessionId expectedSessionId,
            ReaderFrame frame,
            FixedLayoutPlan layoutPlan,
            int tileIndex,
            TilePayload payload
    ) {
        if (!expectedSessionId.equals(payload.sessionId())) {
            throw inconsistent(frame, tileIndex, "session id", expectedSessionId.toString(), payload.sessionId().toString());
        }
        if (payload.frameIndex() != frame.frameIndex()) {
            throw inconsistent(frame, tileIndex, "frame index", Long.toString(frame.frameIndex()), Long.toString(payload.frameIndex()));
        }
        if (payload.frameType() != frame.frameType()) {
            throw inconsistent(frame, tileIndex, "frame type", frame.frameType().name(), payload.frameType().name());
        }
        if (!layoutPlan.profile().profileId().equals(payload.layoutProfileId())) {
            throw inconsistent(
                    frame,
                    tileIndex,
                    "layout profile id",
                    layoutPlan.profile().profileId(),
                    payload.layoutProfileId()
            );
        }
        if (payload.tileIndex().value() != tileIndex) {
            throw inconsistent(
                    frame,
                    tileIndex,
                    "tile index",
                    Integer.toString(tileIndex),
                    Integer.toString(payload.tileIndex().value())
            );
        }
        int totalTiles = layoutPlan.profile().rows() * layoutPlan.profile().cols();
        if (payload.totalTilesInFrame() != totalTiles) {
            throw inconsistent(
                    frame,
                    tileIndex,
                    "total tiles",
                    Integer.toString(totalTiles),
                    Integer.toString(payload.totalTilesInFrame())
            );
        }
    }

    private ReaderContentDecodeException inconsistent(
            ReaderFrame frame,
            int tileIndex,
            String field,
            String expected,
            String actual
    ) {
        return new ReaderContentDecodeException(
                ReaderDecodeStatus.INCONSISTENT_CONTENT,
                "Tile slot "
                        + tileIndex
                        + " in frame "
                        + frame.frameIndex()
                        + " has inconsistent "
                        + field
                        + ": expected "
                        + expected
                        + " but decoded "
                        + actual
        );
    }
}
