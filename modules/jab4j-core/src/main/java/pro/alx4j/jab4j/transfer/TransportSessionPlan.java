package pro.alx4j.jab4j.transfer;

import java.util.List;
import java.util.Objects;
import pro.alx4j.jab4j.api.model.FrameDescriptor;
import pro.alx4j.jab4j.api.model.TransferSession;

/**
 * Deterministic transport session output including records, parity planning, and scheduled frames.
 *
 * @param session updated transfer session with chunk-bearing file records
 * @param manifestSummary deterministic manifest summary
 * @param records ordered logical transport records
 * @param chunkPayloads ordered chunk payloads for data records
 * @param parityPlan deterministic parity-group plan
 * @param frameDescriptors deterministic frame descriptors for downstream rendering and playback
 * @param finalSessionDigest deterministic final session digest
 */
public record TransportSessionPlan(
        TransferSession session,
        ManifestSummary manifestSummary,
        List<TransportRecord> records,
        List<ChunkPayload> chunkPayloads,
        List<ParityGroupPlan> parityPlan,
        List<FrameDescriptor> frameDescriptors,
        String finalSessionDigest
) {

    /**
     * Creates a validated transport session plan.
     *
     * @param session updated transfer session
     * @param manifestSummary deterministic manifest summary
     * @param records ordered logical transport records
     * @param chunkPayloads ordered chunk payloads
     * @param parityPlan deterministic parity-group plan
     * @param frameDescriptors deterministic frame descriptors for downstream rendering and playback
     * @param finalSessionDigest deterministic final session digest
     */
    public TransportSessionPlan {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(manifestSummary, "manifestSummary must not be null");
        records = List.copyOf(Objects.requireNonNull(records, "records must not be null"));
        chunkPayloads = List.copyOf(Objects.requireNonNull(chunkPayloads, "chunkPayloads must not be null"));
        parityPlan = List.copyOf(Objects.requireNonNull(parityPlan, "parityPlan must not be null"));
        frameDescriptors = List.copyOf(Objects.requireNonNull(frameDescriptors, "frameDescriptors must not be null"));
        if (finalSessionDigest == null || finalSessionDigest.isBlank()) {
            throw new IllegalArgumentException("finalSessionDigest must not be blank");
        }
    }
}
