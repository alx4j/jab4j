/**
 * Local reader CLI for restoring current writer {@code imageSequence} PNG exports.
 *
 * <p>The command accepts the exact {@code imageSequence} directory or a parent session directory with exactly one
 * {@code imageSequence} child. The required {@code frame-sequence.txt} file is an MVP writer-export validation helper,
 * not the long-term source of frame truth for future capture paths.</p>
 *
 * <p>iPhone, camera, video, upload, and SaaS capture are not supported by this MVP CLI. A future production capture
 * path must provide accepted frames or equivalent decoded transfer content before restore can run.</p>
 */
package com.alx4j.jab4j.reader.cli;
