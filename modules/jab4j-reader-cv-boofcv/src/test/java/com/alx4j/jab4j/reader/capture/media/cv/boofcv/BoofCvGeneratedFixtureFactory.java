package com.alx4j.jab4j.reader.capture.media.cv.boofcv;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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
import com.alx4j.jab4j.reader.capture.media.CaptureMediaSourceKind;
import com.alx4j.jab4j.reader.capture.media.input.MediaInputFrame;
import com.alx4j.jab4j.render.frame.FrameRasterRenderer;
import com.alx4j.jab4j.render.frame.RenderedFrame;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;
import com.alx4j.jab4j.render.layout.FixedLayoutPlanner;
import com.alx4j.jab4j.render.tile.RenderedTile;
import com.alx4j.jab4j.render.tile.TileRasterRenderer;
import com.alx4j.jab4j.tile.LogicalTile;
import com.alx4j.jab4j.tile.TileCodecProfiles;
import com.alx4j.jab4j.tile.TileCodecs;
import com.alx4j.jab4j.transfer.TilePayloadEnvelopeCodec;

/**
 * Generates local camera-like BoofCV backend fixtures without committing binary media.
 */
final class BoofCvGeneratedFixtureFactory {

    static final String CAMERA_LIKE_MONITOR_PNG = "CM-MVP3-GENERATED-CAMERA-LIKE-MONITOR-PNG";
    static final String CAMERA_LIKE_MONITOR_JPEG = "CM-MVP3-GENERATED-CAMERA-LIKE-MONITOR-JPEG";
    static final String SKEWED_BLURRED_MONITOR_PNG = "CM-MVP7-GENERATED-SKEWED-BLURRED-MONITOR-PNG";
    static final String BRIGHT_MONITOR_WITHOUT_JAB = "CM-MVP4-GENERATED-BRIGHT-MONITOR-WITHOUT-JAB";
    static final String UI_CHROME_WITHOUT_JAB = "CM-MVP4-GENERATED-UI-CHROME-WITHOUT-JAB";
    static final String REPEATED_STRIPES_WITHOUT_JAB = "CM-MVP4-GENERATED-REPEATED-STRIPES-WITHOUT-JAB";
    static final String PARTIAL_CROPPED_FRAME = "CM-MVP4-GENERATED-PARTIAL-CROPPED-FRAME";

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
    private static final SessionId SESSION_ID =
            new SessionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
    private static final TilePayloadEnvelopeCodec ENVELOPE_CODEC = new TilePayloadEnvelopeCodec();

    private BoofCvGeneratedFixtureFactory() {
    }

    /**
     * Returns a generated camera-like monitor PNG fixture with rendered JAB evidence.
     *
     * @return decoded media input frame
     */
    static MediaInputFrame cameraLikeMonitorPng() {
        return pngFrame(CAMERA_LIKE_MONITOR_PNG, cameraLikeMonitorImage(renderJabFrame()));
    }

    /**
     * Returns a generated camera-like monitor fixture after JPEG encode/decode loss.
     *
     * @return decoded media input frame
     */
    static MediaInputFrame cameraLikeMonitorJpegDecoded() {
        return jpegFrame(CAMERA_LIKE_MONITOR_JPEG, cameraLikeMonitorImage(renderJabFrame()));
    }

    /**
     * Returns a generated monitor fixture with affine camera skew and mild blur.
     *
     * @return decoded media input frame
     */
    static MediaInputFrame skewedBlurredMonitorPng() {
        return pngFrame(SKEWED_BLURRED_MONITOR_PNG, skewedBlurredMonitorImage(renderJabFrame()));
    }

    /**
     * Returns a bright monitor fixture that intentionally has no JAB frame.
     *
     * @return decoded media input frame
     */
    static MediaInputFrame brightMonitorWithoutJab() {
        BufferedImage image = monitorCanvas();
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0xFFF4F7FA, true));
            graphics.fillRect(290, 220, 1100, 520);
            graphics.setColor(new Color(0xFFDCE3EA, true));
            graphics.fillRect(340, 280, 1000, 72);
            graphics.setColor(new Color(0xFFCAD3DC, true));
            graphics.fillRect(340, 410, 460, 220);
            graphics.fillRect(870, 410, 460, 220);
            graphics.setColor(new Color(0xFFEEF2F5, true));
            graphics.fillRect(390, 460, 360, 42);
            graphics.fillRect(920, 460, 360, 42);
        } finally {
            graphics.dispose();
        }
        return pngFrame(BRIGHT_MONITOR_WITHOUT_JAB, image);
    }

    /**
     * Returns a UI chrome fixture that intentionally has no JAB frame.
     *
     * @return decoded media input frame
     */
    static MediaInputFrame uiChromeWithoutJab() {
        BufferedImage image = monitorCanvas();
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0xFFEEF2F5, true));
            graphics.fillRect(210, 140, 1280, 720);
            graphics.setColor(new Color(0xFF26313A, true));
            graphics.fillRect(210, 140, 1280, 56);
            graphics.setColor(new Color(0xFF52606D, true));
            graphics.fillRect(250, 158, 180, 20);
            graphics.fillRect(470, 158, 120, 20);
            graphics.setColor(new Color(0xFFB9C4CE, true));
            for (int row = 0; row < 4; row++) {
                int top = 240 + (row * 128);
                graphics.fillRect(270, top, 1040, 54);
                graphics.fillRect(270, top + 76, 760, 22);
            }
            graphics.setColor(new Color(0xFFE4E9EE, true));
            graphics.fillRect(1330, 240, 96, 560);
        } finally {
            graphics.dispose();
        }
        return pngFrame(UI_CHROME_WITHOUT_JAB, image);
    }

    /**
     * Returns a repeated-stripes fixture that intentionally has no JAB frame.
     *
     * @return decoded media input frame
     */
    static MediaInputFrame repeatedStripesWithoutJab() {
        int canvasWidth = 900;
        int canvasHeight = 600;
        BufferedImage image = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0xFF303840, true));
            graphics.fillRect(0, 0, canvasWidth, canvasHeight);
            graphics.setColor(new Color(0xFF15191D, true));
            graphics.fillRect(40, 36, canvasWidth - 80, canvasHeight - 72);
            graphics.setColor(new Color(0xFF11161B, true));
            graphics.fillRect(96, 92, 700, 380);
            for (int col = 0; col < 42; col++) {
                graphics.setColor(new Color(col % 2 == 0 ? 0xFF9EA7B0 : 0xFF69727C, true));
                graphics.fillRect(128 + (col * 14), 136, 9, 280);
            }
            graphics.setColor(new Color(0xFF87909A, true));
            for (int row = 0; row < 5; row++) {
                graphics.fillRect(128, 146 + (row * 52), 590, 6);
            }
        } finally {
            graphics.dispose();
        }
        return pngFrame(REPEATED_STRIPES_WITHOUT_JAB, image);
    }

    /**
     * Returns a partial rendered-frame evidence fixture that is intentionally cropped.
     *
     * @return decoded media input frame
     */
    static MediaInputFrame partialCroppedFrameEvidence() {
        RenderedFrame frame = renderJabFrame();
        BufferedImage frameImage = toImage(frame);
        int canvasWidth = 900;
        int canvasHeight = frame.heightPixels() + 120;
        BufferedImage image = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0xFF303840, true));
            graphics.fillRect(0, 0, canvasWidth, canvasHeight);
            graphics.drawImage(frameImage, -900, 60, null);
        } finally {
            graphics.dispose();
        }
        return pngFrame(PARTIAL_CROPPED_FRAME, image);
    }

    private static RenderedFrame renderJabFrame() {
        FixedLayoutPlan layoutPlan = new FixedLayoutPlanner().plan(CAPTURE_LAYOUT);
        List<TilePayload> payloads = List.of(tilePayload(0), tilePayload(1));
        FrameDescriptor frameDescriptor = new FrameDescriptor(0L, FrameType.DATA, CAPTURE_LAYOUT, payloads);
        List<RenderedTile> tiles = new ArrayList<>(payloads.size());
        for (TilePayload payload : payloads) {
            byte[] envelope = ENVELOPE_CODEC.serialize(payload);
            LogicalTile logicalTile = TileCodecs.defaultEncoder().encode(envelope, TileCodecProfiles.balancedV1());
            tiles.add(new TileRasterRenderer().render(logicalTile, layoutPlan));
        }
        return new FrameRasterRenderer().render(frameDescriptor, tiles);
    }

    private static TilePayload tilePayload(int tileIndex) {
        byte[] body = ("boofcv-generated-fixture-" + tileIndex).getBytes(StandardCharsets.UTF_8);
        return new TilePayload(
                1,
                SESSION_ID,
                FrameType.DATA,
                0L,
                new TileIndex(tileIndex),
                CAPTURE_LAYOUT.rows() * CAPTURE_LAYOUT.cols(),
                CAPTURE_LAYOUT.profileId(),
                PayloadKind.FILE_CHUNK,
                tileIndex,
                body.length,
                crc32c(body),
                0,
                body
        );
    }

    private static int crc32c(byte[] body) {
        CRC32C crc32c = new CRC32C();
        crc32c.update(body, 0, body.length);
        return (int) crc32c.getValue();
    }

    private static BufferedImage cameraLikeMonitorImage(RenderedFrame frame) {
        BufferedImage frameImage = colorShiftedImage(frame, 18);
        int canvasWidth = 1700;
        int canvasHeight = 1000;
        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setColor(new Color(0xFF303840, true));
            graphics.fillRect(0, 0, canvasWidth, canvasHeight);
            graphics.setColor(new Color(0xFF15191D, true));
            graphics.fillRect(48, 42, canvasWidth - 96, canvasHeight - 84);
            graphics.setColor(new Color(0xFF242A31, true));
            graphics.fillRect(78, 70, canvasWidth - 156, canvasHeight - 140);
            graphics.setColor(new Color(0xFF3E4650, true));
            graphics.fillRect(78, 70, canvasWidth - 156, 48);
            graphics.drawImage(frameImage, 210, 140, null);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }

    private static BufferedImage skewedBlurredMonitorImage(RenderedFrame frame) {
        BufferedImage frameImage = boxBlurredImage(colorShiftedImage(frame, 22));
        BufferedImage canvas = monitorCanvas();
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(frameImage, new AffineTransform(
                    1.0d,
                    0.035d,
                    -0.075d,
                    1.0d,
                    260.0d,
                    150.0d
            ), null);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }

    private static BufferedImage monitorCanvas() {
        int canvasWidth = 1700;
        int canvasHeight = 1000;
        BufferedImage image = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0xFF303840, true));
            graphics.fillRect(0, 0, canvasWidth, canvasHeight);
            graphics.setColor(new Color(0xFF15191D, true));
            graphics.fillRect(48, 42, canvasWidth - 96, canvasHeight - 84);
            graphics.setColor(new Color(0xFF242A31, true));
            graphics.fillRect(78, 70, canvasWidth - 156, canvasHeight - 140);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage colorShiftedImage(RenderedFrame frame, int colorShift) {
        BufferedImage image = toImage(frame);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, shiftPaletteColor(image.getRGB(x, y), colorShift));
            }
        }
        return image;
    }

    private static BufferedImage boxBlurredImage(BufferedImage image) {
        float weight = 1.0f / 9.0f;
        float[] weights = {
                weight, weight, weight,
                weight, weight, weight,
                weight, weight, weight
        };
        BufferedImage blurred = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        return new ConvolveOp(new Kernel(3, 3, weights), ConvolveOp.EDGE_NO_OP, null).filter(image, blurred);
    }

    private static int shiftPaletteColor(int argb, int colorShift) {
        int red = shiftedChannel((argb >>> 16) & 0xFF, colorShift);
        int green = shiftedChannel((argb >>> 8) & 0xFF, colorShift);
        int blue = shiftedChannel(argb & 0xFF, colorShift);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int shiftedChannel(int value, int colorShift) {
        return value < 128
                ? Math.min(255, value + colorShift)
                : Math.max(0, value - colorShift);
    }

    private static MediaInputFrame pngFrame(String scenarioId, BufferedImage image) {
        int[] pixels = pixels(image);
        return new MediaInputFrame(
                scenarioId + ".png",
                CaptureMediaSourceKind.STILL_IMAGE_FILE,
                0,
                image.getWidth(),
                image.getHeight(),
                "png",
                sha256(pixels),
                pixels
        );
    }

    private static MediaInputFrame jpegFrame(String scenarioId, BufferedImage image) {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(rgb, "jpeg", output)) {
                throw new IllegalStateException("No JPEG ImageIO writer is available");
            }
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(output.toByteArray()));
            int[] pixels = pixels(decoded);
            return new MediaInputFrame(
                    scenarioId + ".jpeg",
                    CaptureMediaSourceKind.STILL_IMAGE_FILE,
                    0,
                    decoded.getWidth(),
                    decoded.getHeight(),
                    "jpeg",
                    sha256(pixels),
                    pixels
            );
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Generated JPEG fixture could not be decoded", exception);
        }
    }

    private static BufferedImage toImage(RenderedFrame frame) {
        BufferedImage image = new BufferedImage(frame.widthPixels(), frame.heightPixels(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < frame.heightPixels(); y++) {
            for (int x = 0; x < frame.widthPixels(); x++) {
                image.setRGB(x, y, frame.argbPixels().get((y * frame.widthPixels()) + x));
            }
        }
        return image;
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static String sha256(int[] pixels) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
            for (int pixel : pixels) {
                buffer.clear();
                buffer.putInt(pixel);
                digest.update(buffer.array());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }
}
