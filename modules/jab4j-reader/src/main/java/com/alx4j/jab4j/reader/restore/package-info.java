/**
 * Source-neutral models and services for restoring decoded transfer content to the filesystem.
 *
 * <p>Restore consumes decoded transfer content plus accepted session context and publishes local output. It does not
 * reopen PNG files, parse writer export folders, consume camera buffers, or depend on upload/SaaS workflow state.
 * Future capture sources must produce equivalent decoded transfer content before invoking restore.</p>
 */
package com.alx4j.jab4j.reader.restore;
