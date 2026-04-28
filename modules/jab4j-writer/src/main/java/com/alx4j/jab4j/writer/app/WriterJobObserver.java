package com.alx4j.jab4j.writer.app;

/**
 * Observer hooks for writer lifecycle progress and failure reporting.
 */
public interface WriterJobObserver {

    /**
     * Called when the writer reaches a lifecycle milestone or progress update.
     *
     * @param event lifecycle event
     */
    default void onEvent(WriterJobEvent event) {
    }

    /**
     * Called when the writer fails and cannot continue the current run.
     *
     * @param exception boundary-level writer failure
     */
    default void onFailure(WriterJobException exception) {
    }

    /**
     * Returns a no-op observer.
     *
     * @return no-op observer
     */
    static WriterJobObserver noOp() {
        return new WriterJobObserver() {
        };
    }
}
