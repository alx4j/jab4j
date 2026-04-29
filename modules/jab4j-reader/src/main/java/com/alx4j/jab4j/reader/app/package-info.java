/**
 * Reader application orchestration for the current local decode-and-restore flow.
 *
 * <p>The default application service accepts only current writer {@code imageSequence} PNG exports through the writer
 * input adapter: either the exact {@code imageSequence} directory or a parent session directory with exactly one
 * {@code imageSequence} child. iPhone, camera, video, upload, and SaaS capture are future directions, not current MVP
 * capabilities.</p>
 *
 * <p>Future production capture must solve camera-grade frame acquisition, source-neutral frame identity,
 * metadata-band or equivalent frame metadata recovery, tolerance for camera/video distortion, and a mobile or SaaS
 * delivery surface before it can feed accepted frames or decoded transfer content into restore.</p>
 */
package com.alx4j.jab4j.reader.app;
