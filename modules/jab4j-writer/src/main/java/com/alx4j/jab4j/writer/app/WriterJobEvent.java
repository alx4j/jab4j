package com.alx4j.jab4j.writer.app;

import java.util.Objects;

/**
 * One lifecycle or progress event emitted by the writer application boundary.
 *
 * @param status lifecycle state for the event
 * @param message human-readable progress detail
 * @param completedUnits completed units for progress-oriented events, or {@code null}
 * @param totalUnits total units for progress-oriented events, or {@code null}
 */
public record WriterJobEvent(
        WriterJobStatus status,
        String message,
        Long completedUnits,
        Long totalUnits
) {

    /**
     * Creates a validated writer job event.
     *
     * @param status lifecycle state
     * @param message human-readable detail
     * @param completedUnits completed units, or {@code null}
     * @param totalUnits total units, or {@code null}
     */
    public WriterJobEvent {
        Objects.requireNonNull(status, "status must not be null");
        if (message == null || message.isBlank()) {
            throw new WriterJobException(WriterJobStatus.FAILED, "Writer job event message must not be blank");
        }
        if ((completedUnits == null) != (totalUnits == null)) {
            throw new WriterJobException(WriterJobStatus.FAILED, "Writer job event progress values must be both present or both absent");
        }
        if (completedUnits != null) {
            if (completedUnits < 0 || totalUnits < 0) {
                throw new WriterJobException(WriterJobStatus.FAILED, "Writer job event progress values must be non-negative");
            }
            if (completedUnits > totalUnits) {
                throw new WriterJobException(WriterJobStatus.FAILED, "Writer job event completedUnits must not exceed totalUnits");
            }
        }
    }
}
