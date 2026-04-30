/**
 * Local reader CLI for restoring current writer {@code imageSequence} PNG exports.
 *
 * <p>The command accepts the exact {@code imageSequence} directory or a parent session directory with exactly one
 * {@code imageSequence} child. The required {@code frame-sequence.txt} file is a writer-export validation helper,
 * not the long-term source of frame truth for future capture paths.</p>
 *
 * <p>This exact writer-exported PNG restore path remains the reference baseline used to separate payload and restore
 * regressions from future camera capture or frame recovery failures.</p>
 *
 * <p>iPhone, camera, video, upload, and SaaS capture are not supported by this local restore CLI. A future production capture
 * path must provide accepted frames or equivalent decoded transfer content before restore can run.</p>
 */
package com.alx4j.jab4j.reader.cli;
