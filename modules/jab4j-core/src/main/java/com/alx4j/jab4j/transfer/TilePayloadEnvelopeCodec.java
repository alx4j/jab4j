package com.alx4j.jab4j.transfer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alx4j.jab4j.api.model.FrameType;
import com.alx4j.jab4j.api.model.PayloadKind;
import com.alx4j.jab4j.api.model.SessionId;
import com.alx4j.jab4j.api.model.TileIndex;
import com.alx4j.jab4j.api.model.TilePayload;
import com.alx4j.jab4j.support.ByteOrderUtils;
import com.alx4j.jab4j.support.ChecksumUtils;

/**
 * Serializes and parses the milestone-one binary tile payload envelope in network byte order.
 */
public final class TilePayloadEnvelopeCodec {

    private static final Logger LOGGER = LoggerFactory.getLogger(TilePayloadEnvelopeCodec.class);
    private static final byte[] MAGIC = new byte[] {'J', '4', 'T', 'L'};
    private static final int FIXED_HEADER_BYTES = 4 + 4 + 1 + 1 + 2 + 8 + 4 + 4 + 8 + 4 + 4 + 4 + 8 + 8 + 4;

    /**
     * Serializes one validated tile payload into the deterministic milestone-one envelope format.
     *
     * @param payload tile payload to serialize
     * @return serialized envelope bytes
     */
    public byte[] serialize(TilePayload payload) {
        try {
            if (payload == null) {
                throw new TransportException("payload must not be null");
            }
            int actualPayloadCrc32c = ChecksumUtils.crc32c(payload.body());
            if (actualPayloadCrc32c != payload.payloadCrc32c()) {
                throw new TransportException("payload CRC32C does not match the payload body");
            }

            byte[] layoutProfileBytes = payload.layoutProfileId().getBytes(StandardCharsets.UTF_8);
            byte[] body = payload.body();
            byte[] envelope = new byte[FIXED_HEADER_BYTES + layoutProfileBytes.length + body.length];

            System.arraycopy(MAGIC, 0, envelope, 0, MAGIC.length);
            ByteOrderUtils.writeIntBigEndian(envelope, 4, payload.protocolCompatibilityVersion());
            envelope[8] = frameTypeCode(payload.frameType());
            envelope[9] = payloadKindCode(payload.payloadKind());
            envelope[10] = 0;
            envelope[11] = 0;
            ByteOrderUtils.writeLongBigEndian(envelope, 12, payload.frameIndex());
            ByteOrderUtils.writeIntBigEndian(envelope, 20, payload.tileIndex().value());
            ByteOrderUtils.writeIntBigEndian(envelope, 24, payload.totalTilesInFrame());
            ByteOrderUtils.writeLongBigEndian(envelope, 28, payload.payloadSequenceNumber());
            ByteOrderUtils.writeIntBigEndian(envelope, 36, payload.flags());
            ByteOrderUtils.writeIntBigEndian(envelope, 40, payload.payloadByteLength());
            ByteOrderUtils.writeIntBigEndian(envelope, 44, payload.payloadCrc32c());
            UUID sessionUuid = payload.sessionId().value();
            ByteOrderUtils.writeLongBigEndian(envelope, 48, sessionUuid.getMostSignificantBits());
            ByteOrderUtils.writeLongBigEndian(envelope, 56, sessionUuid.getLeastSignificantBits());
            ByteOrderUtils.writeIntBigEndian(envelope, 64, layoutProfileBytes.length);
            System.arraycopy(layoutProfileBytes, 0, envelope, FIXED_HEADER_BYTES, layoutProfileBytes.length);
            System.arraycopy(body, 0, envelope, FIXED_HEADER_BYTES + layoutProfileBytes.length, body.length);
            return envelope;
        } catch (TransportException exception) {
            LOGGER.warn(
                    "Tile payload envelope serialization failed sessionId={} frameIndex={} tileIndex={} payloadKind={} message={}",
                    payload == null ? null : payload.sessionId(),
                    payload == null ? null : payload.frameIndex(),
                    payload == null ? null : payload.tileIndex(),
                    payload == null ? null : payload.payloadKind(),
                    exception.getMessage()
            );
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Tile payload envelope serialization failed unexpectedly sessionId={} frameIndex={} tileIndex={} payloadKind={}",
                    payload == null ? null : payload.sessionId(),
                    payload == null ? null : payload.frameIndex(),
                    payload == null ? null : payload.tileIndex(),
                    payload == null ? null : payload.payloadKind(),
                    exception
            );
            throw exception;
        }
    }

    /**
     * Parses one serialized envelope and validates the supported compatibility version and payload CRC.
     *
     * @param envelope serialized envelope bytes
     * @param supportedCompatibilityVersion supported compatibility version
     * @return parsed tile payload
     */
    public TilePayload parse(byte[] envelope, int supportedCompatibilityVersion) {
        FrameType frameType = null;
        PayloadKind payloadKind = null;
        Long frameIndex = null;
        Integer tileIndex = null;
        UUID sessionId = null;
        Integer envelopeLength = envelope == null ? null : envelope.length;
        try {
            if (supportedCompatibilityVersion <= 0) {
                throw new TransportException("supportedCompatibilityVersion must be positive");
            }
            if (envelope == null) {
                throw new TransportException("envelope must not be null");
            }
            if (envelope.length < FIXED_HEADER_BYTES) {
                throw new TransportException("Envelope is shorter than the fixed header");
            }
            for (int index = 0; index < MAGIC.length; index++) {
                if (envelope[index] != MAGIC[index]) {
                    throw new TransportException("Envelope magic does not match the transport format");
                }
            }

            int compatibilityVersion = ByteOrderUtils.readIntBigEndian(envelope, 4);
            if (compatibilityVersion != supportedCompatibilityVersion) {
                throw new TransportException("Unsupported protocol compatibility version: " + compatibilityVersion);
            }
            frameType = decodeFrameType(envelope[8]);
            payloadKind = decodePayloadKind(envelope[9]);
            frameIndex = ByteOrderUtils.readLongBigEndian(envelope, 12);
            tileIndex = ByteOrderUtils.readIntBigEndian(envelope, 20);
            int totalTilesInFrame = ByteOrderUtils.readIntBigEndian(envelope, 24);
            long payloadSequenceNumber = ByteOrderUtils.readLongBigEndian(envelope, 28);
            int flags = ByteOrderUtils.readIntBigEndian(envelope, 36);
            int payloadByteLength = ByteOrderUtils.readIntBigEndian(envelope, 40);
            int payloadCrc32c = ByteOrderUtils.readIntBigEndian(envelope, 44);
            long sessionMsb = ByteOrderUtils.readLongBigEndian(envelope, 48);
            long sessionLsb = ByteOrderUtils.readLongBigEndian(envelope, 56);
            sessionId = new UUID(sessionMsb, sessionLsb);
            int layoutProfileLength = ByteOrderUtils.readIntBigEndian(envelope, 64);
            if (layoutProfileLength <= 0) {
                throw new TransportException("Envelope layout profile id length must be positive");
            }

            int requiredBytes = FIXED_HEADER_BYTES + layoutProfileLength + payloadByteLength;
            if (payloadByteLength < 0 || requiredBytes != envelope.length) {
                throw new TransportException("Envelope length does not match the declared payload and layout lengths");
            }

            String layoutProfileId = new String(envelope, FIXED_HEADER_BYTES, layoutProfileLength, StandardCharsets.UTF_8);
            byte[] body = new byte[payloadByteLength];
            System.arraycopy(envelope, FIXED_HEADER_BYTES + layoutProfileLength, body, 0, payloadByteLength);
            int actualPayloadCrc32c = ChecksumUtils.crc32c(body);
            if (actualPayloadCrc32c != payloadCrc32c) {
                throw new TransportException("Envelope payload CRC32C does not match the payload bytes");
            }

            return new TilePayload(
                    compatibilityVersion,
                    new SessionId(sessionId),
                    frameType,
                    frameIndex,
                    new TileIndex(tileIndex),
                    totalTilesInFrame,
                    layoutProfileId,
                    payloadKind,
                    payloadSequenceNumber,
                    payloadByteLength,
                    payloadCrc32c,
                    flags,
                    body
            );
        } catch (TransportException exception) {
            LOGGER.warn(
                    "Tile payload envelope parsing failed supportedCompatibilityVersion={} envelopeBytes={} sessionId={} frameType={} frameIndex={} tileIndex={} payloadKind={} message={}",
                    supportedCompatibilityVersion,
                    envelopeLength,
                    sessionId,
                    frameType,
                    frameIndex,
                    tileIndex,
                    payloadKind,
                    exception.getMessage()
            );
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Tile payload envelope parsing failed unexpectedly supportedCompatibilityVersion={} envelopeBytes={} sessionId={} frameType={} frameIndex={} tileIndex={} payloadKind={}",
                    supportedCompatibilityVersion,
                    envelopeLength,
                    sessionId,
                    frameType,
                    frameIndex,
                    tileIndex,
                    payloadKind,
                    exception
            );
            throw exception;
        }
    }

    private byte frameTypeCode(FrameType frameType) {
        return switch (frameType) {
            case SYNC -> 1;
            case SESSION_HEADER -> 2;
            case MANIFEST -> 3;
            case DATA -> 4;
            case PARITY -> 5;
            case END -> 6;
        };
    }

    private FrameType decodeFrameType(byte code) {
        return switch (code) {
            case 1 -> FrameType.SYNC;
            case 2 -> FrameType.SESSION_HEADER;
            case 3 -> FrameType.MANIFEST;
            case 4 -> FrameType.DATA;
            case 5 -> FrameType.PARITY;
            case 6 -> FrameType.END;
            default -> throw new TransportException("Unsupported frame type code: " + (code & 0xFF));
        };
    }

    private byte payloadKindCode(PayloadKind payloadKind) {
        return switch (payloadKind) {
            case SYNC_METADATA -> 1;
            case SESSION_HEADER -> 2;
            case MANIFEST_FRAGMENT -> 3;
            case FILE_CHUNK -> 4;
            case PARITY_SHARD -> 5;
            case SESSION_END -> 6;
        };
    }

    private PayloadKind decodePayloadKind(byte code) {
        return switch (code) {
            case 1 -> PayloadKind.SYNC_METADATA;
            case 2 -> PayloadKind.SESSION_HEADER;
            case 3 -> PayloadKind.MANIFEST_FRAGMENT;
            case 4 -> PayloadKind.FILE_CHUNK;
            case 5 -> PayloadKind.PARITY_SHARD;
            case 6 -> PayloadKind.SESSION_END;
            default -> throw new TransportException("Unsupported payload kind code: " + (code & 0xFF));
        };
    }
}
