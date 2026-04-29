/**
 * Source-neutral models for decoded transfer content from accepted frames.
 *
 * <p>Decoded content does not depend on local PNG folder layout, writer export file names, or
 * {@code frame-sequence.txt}. Future capture sources must either produce equivalent decoded transfer content before
 * restore runs or fail before invoking restore.</p>
 */
package com.alx4j.jab4j.reader.content;
