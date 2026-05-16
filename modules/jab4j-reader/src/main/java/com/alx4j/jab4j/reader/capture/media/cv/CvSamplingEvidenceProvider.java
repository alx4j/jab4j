package com.alx4j.jab4j.reader.capture.media.cv;

import java.util.Optional;
import com.alx4j.jab4j.reader.capture.media.normalize.NormalizedCaptureFrame;
import com.alx4j.jab4j.render.layout.FixedLayoutPlan;

/**
 * Supplies optional backend-neutral sampling evidence for one normalized media frame.
 */
@FunctionalInterface
public interface CvSamplingEvidenceProvider {

    /**
     * Returns sampling evidence for the supplied normalized frame and fixed layout plan.
     *
     * @param frame normalized capture frame
     * @param layoutPlan fixed layout plan matched to the frame
     * @return sampling evidence, or empty when no backend evidence is available
     */
    Optional<CvSamplingEvidence> evidenceFor(NormalizedCaptureFrame frame, FixedLayoutPlan layoutPlan);

    /**
     * Returns a provider that supplies no sampling evidence.
     *
     * @return empty evidence provider
     */
    static CvSamplingEvidenceProvider none() {
        return (frame, layoutPlan) -> Optional.empty();
    }
}
