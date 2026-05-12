package com.alx4j.jab4j.reader.capture.media;

/**
 * Caller-declared kind of real-world capture media supplied to the media receiver.
 */
public enum CaptureMediaSourceKind {
    /**
     * One or more still image files, such as monitor photos or exported image frames.
     */
    STILL_IMAGE_FILE,

    /**
     * One or more folders containing frames extracted from a recorded playback.
     */
    EXTRACTED_FRAME_FOLDER,

    /**
     * One or more direct video files that require a video frame adapter before decode.
     */
    VIDEO_FILE;

    /**
     * Indicates whether this source kind represents direct video input.
     *
     * @return true for direct video files
     */
    public boolean video() {
        return this == VIDEO_FILE;
    }

    /**
     * Indicates whether this source kind is expected to contain ordered frame images.
     *
     * @return true for extracted frame folders
     */
    public boolean extractedFrameCollection() {
        return this == EXTRACTED_FRAME_FOLDER;
    }
}
