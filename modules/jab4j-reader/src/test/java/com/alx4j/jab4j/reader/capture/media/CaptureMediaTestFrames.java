package com.alx4j.jab4j.reader.capture.media;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.CRC32C;
import javax.imageio.ImageIO;
import com.alx4j.jab4j.api.model.FrameDescriptor;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.LayoutProfile;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.TileIndex;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.reader.capture.media.normalize.FrameCorners;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.reader.capture.media.quality.CaptureMediaQualityMetrics;
import com.alx4j.jab4j.render.frame.FrameRasterRenderer;
import com.alx4j.jab4j.render.frame.RenderedFrame;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.render.layout.TilePlacement;
import com.alx4j.jab4j.render.tile.RenderedTile;
import com.alx4j.jab4j.render.tile.TileRasterRenderer;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecProfiles;
import com.alx4j.jab4j.tile.TileCodecs;
import com.alx4j.jab4j.transfer.TilePayloadEnvelopeCodec;

/**
 * Test-only factory for deterministic capture-media frames carrying real encoded tile envelopes.
 */
final class CaptureMediaTestFrames {

    private static final LayoutProfile CAPTURE_LAYOUT = new LayoutProfile(
            "debug-low-density",
            1,
            2,
            1280,
            720,
            16,
            40,
            "solidWhite",
            48,
            24,
            "black",
            "preserveAspect"
    );
    private static final SessionId DEFAULT_SESSION_ID =
            new SessionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
    private static final FixedLayoutPlan LAYOUT_PLAN = new FixedLayoutPlanner().plan(CAPTURE_LAYOUT);
    private static final TilePayloadEnvelopeCodec ENVELOPE_CODEC = new TilePayloadEnvelopeCodec();
    private static final TileRasterRenderer TILE_RENDERER = new TileRasterRenderer();
    private static final FrameRasterRenderer FRAME_RENDERER = new FrameRasterRenderer();

    private CaptureMediaTestFrames() {
    }

    /**
     * Returns the rendered layout profile id used by media safety fixtures.
     *
     * @return fixture layout profile id
     */
    static String layoutProfileId() {
        return CAPTURE_LAYOUT.profileId();
    }

    /**
     * Returns the default session id used by media safety fixtures.
     *
     * @return fixture session id
     */
    static SessionId defaultSessionId() {
        return DEFAULT_SESSION_ID;
    }

    /**
     * Creates a tile payload for the fixed capture-media test layout.
     *
     * @param sessionId owning session id
     * @param frameType owning frame type
     * @param frameIndex zero-based frame index
     * @param tileIndex zero-based tile index
     * @param payloadKind payload kind
     * @param body payload bytes
     * @return validated tile payload
     */
    static TilePayload payload(
            SessionId sessionId,
            FrameType frameType,
            long frameIndex,
            int tileIndex,
            PayloadKind payloadKind,
            byte[] body
    ) {
        byte[] retainedBody = body == null ? new byte[0] : body.clone();
        return new TilePayload(
                1,
                Objects.requireNonNull(sessionId, "sessionId must not be null"),
                Objects.requireNonNull(frameType, "frameType must not be null"),
                frameIndex,
                new TileIndex(tileIndex),
                CAPTURE_LAYOUT.rows() * CAPTURE_LAYOUT.cols(),
                CAPTURE_LAYOUT.profileId(),
                Objects.requireNonNull(payloadKind, "payloadKind must not be null"),
                tileIndex,
                retainedBody.length,
                crc32c(retainedBody),
                0,
                retainedBody
        );
    }

    /**
     * Creates a UTF-8 tile payload for the fixed capture-media test layout.
     *
     * @param frameType owning frame type
     * @param frameIndex zero-based frame index
     * @param tileIndex zero-based tile index
     * @param payloadKind payload kind
     * @param body payload text
     * @return validated tile payload
     */
    static TilePayload payload(
            FrameType frameType,
            long frameIndex,
            int tileIndex,
            PayloadKind payloadKind,
            String body
    ) {
        return payload(
                DEFAULT_SESSION_ID,
                frameType,
                frameIndex,
                tileIndex,
                payloadKind,
                Objects.requireNonNull(body, "body must not be null").getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Renders a normalized frame by placing each payload in its declared tile slot.
     *
     * @param sourceId caller-visible source id
     * @param callerOrder zero-based caller order
     * @param payloads tile payloads to render
     * @return normalized frame for decoder tests
     */
    static NormalizedCaptureFrame normalizedFrame(String sourceId, int callerOrder, TilePayload... payloads) {
        return normalizedFrame(sourceId, callerOrder, renderManualPixels(tiles(payloads)));
    }

    /**
     * Renders a normalized frame with one payload deliberately placed in a caller-selected slot.
     *
     * @param sourceId caller-visible source id
     * @param callerOrder zero-based caller order
     * @param slotIndex physical rendered tile slot
     * @param payload payload to render
     * @return normalized frame for slot-identity tests
     */
    static NormalizedCaptureFrame normalizedFrameWithPayloadInSlot(
            String sourceId,
            int callerOrder,
            int slotIndex,
            TilePayload payload
    ) {
        return normalizedFrame(sourceId, callerOrder, renderManualPixels(List.of(new TileRender(slotIndex, payload, false))));
    }

    /**
     * Renders a normalized frame whose tile envelope bytes fail envelope CRC validation.
     *
     * @param sourceId caller-visible source id
     * @param callerOrder zero-based caller order
     * @param slotIndex physical rendered tile slot
     * @param payload source payload before envelope corruption
     * @return normalized frame for envelope validation tests
     */
    static NormalizedCaptureFrame normalizedFrameWithCorruptedEnvelope(
            String sourceId,
            int callerOrder,
            int slotIndex,
            TilePayload payload
    ) {
        return normalizedFrame(sourceId, callerOrder, renderManualPixels(List.of(new TileRender(slotIndex, payload, true))));
    }

    /**
     * Writes a full rendered media PNG with wrapper bands and the supplied payload tiles.
     *
     * @param output output PNG path
     * @param payloads payload tiles for one frame
     * @throws IOException when the PNG cannot be written
     */
    static void writeRenderedPng(Path output, TilePayload... payloads) throws IOException {
        BufferedImage image = fullFrameImage(payloads);
        if (!ImageIO.write(image, "png", output.toFile())) {
            throw new IOException("No PNG ImageIO writer is available");
        }
    }

    private static NormalizedCaptureFrame normalizedFrame(String sourceId, int callerOrder, int[] pixels) {
        return new NormalizedCaptureFrame(
                sourceId,
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                callerOrder,
                CAPTURE_LAYOUT.frameWidthPx(),
                CAPTURE_LAYOUT.frameHeightPx(),
                CAPTURE_LAYOUT.frameWidthPx(),
                CAPTURE_LAYOUT.frameHeightPx(),
                "png",
                "abc123",
                CAPTURE_LAYOUT.profileId(),
                FrameCorners.exactFrame(CAPTURE_LAYOUT.frameWidthPx(), CAPTURE_LAYOUT.frameHeightPx()),
                CaptureMediaQualityMetrics.exactRenderedFrame(),
                pixels
        );
    }

    private static List<TileRender> tiles(TilePayload... payloads) {
        Objects.requireNonNull(payloads, "payloads must not be null");
        return Arrays.stream(payloads)
                .map(payload -> new TileRender(payload.tileIndex().value(), payload, false))
                .toList();
    }

    private static int[] renderManualPixels(List<TileRender> tiles) {
        int[] framePixels = new int[CAPTURE_LAYOUT.frameWidthPx() * CAPTURE_LAYOUT.frameHeightPx()];
        Arrays.fill(framePixels, 0xFF000000);
        for (TileRender tile : tiles) {
            pasteTile(framePixels, tile.slotIndex(), renderTile(tile.payload(), tile.corruptEnvelope()));
        }
        return framePixels;
    }

    private static BufferedImage fullFrameImage(TilePayload... payloads) {
        List<TilePayload> payloadList = List.of(payloads);
        if (payloadList.isEmpty()) {
            throw new IllegalArgumentException("payloads must not be empty");
        }
        TilePayload firstPayload = payloadList.get(0);
        List<RenderedTile> renderedTiles = new ArrayList<>(payloadList.size());
        for (TilePayload payload : payloadList) {
            renderedTiles.add(renderTile(payload, false));
        }
        RenderedFrame frame = FRAME_RENDERER.render(
                new FrameDescriptor(firstPayload.frameIndex(), firstPayload.frameType(), CAPTURE_LAYOUT, payloadList),
                renderedTiles
        );
        return imageFrom(frame.argbPixels().stream().mapToInt(Integer::intValue).toArray());
    }

    private static RenderedTile renderTile(TilePayload payload, boolean corruptEnvelope) {
        byte[] envelope = ENVELOPE_CODEC.serialize(payload);
        if (corruptEnvelope) {
            envelope[envelope.length - 1] = (byte) (envelope[envelope.length - 1] ^ 0x01);
        }
        LogicalTile logicalTile = TileCodecs.defaultEncoder().encode(envelope, TileCodecProfiles.balancedV1());
        return TILE_RENDERER.render(logicalTile, LAYOUT_PLAN);
    }

    private static void pasteTile(int[] framePixels, int slotIndex, RenderedTile renderedTile) {
        TilePlacement placement = LAYOUT_PLAN.tilePlacements().get(slotIndex);
        for (int row = 0; row < renderedTile.heightPixels(); row++) {
            for (int col = 0; col < renderedTile.widthPixels(); col++) {
                int source = renderedTile.argbPixels().get((row * renderedTile.widthPixels()) + col);
                framePixels[((placement.yPx() + row) * CAPTURE_LAYOUT.frameWidthPx()) + placement.xPx() + col] = source;
            }
        }
    }

    private static BufferedImage imageFrom(int[] pixels) {
        BufferedImage image = new BufferedImage(
                CAPTURE_LAYOUT.frameWidthPx(),
                CAPTURE_LAYOUT.frameHeightPx(),
                BufferedImage.TYPE_INT_ARGB
        );
        image.setRGB(
                0,
                0,
                CAPTURE_LAYOUT.frameWidthPx(),
                CAPTURE_LAYOUT.frameHeightPx(),
                pixels,
                0,
                CAPTURE_LAYOUT.frameWidthPx()
        );
        return image;
    }

    private static int crc32c(byte[] body) {
        CRC32C crc32c = new CRC32C();
        crc32c.update(body, 0, body.length);
        return (int) crc32c.getValue();
    }

    private record TileRender(int slotIndex, TilePayload payload, boolean corruptEnvelope) {

        private TileRender {
            if (slotIndex < 0 || slotIndex >= CAPTURE_LAYOUT.rows() * CAPTURE_LAYOUT.cols()) {
                throw new IllegalArgumentException("slotIndex must stay within the fixed layout capacity");
            }
            Objects.requireNonNull(payload, "payload must not be null");
        }
    }
}
