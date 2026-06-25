/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data

/**
 * Pure new-comment diffing for G6 (no Android deps, so it is plain-JUnit testable).
 *
 * A comment is "new since last visit" iff its id is strictly greater than the stored baseline (see
 * [CommentSeen] for why a single max-id baseline suffices). A null baseline means first visit:
 * nothing is marked new.
 */
object NewCommentMarker {

  /**
   * Whether [commentId] arrived after the [baseline] (the last-visit watermark). False on first
   * visit ([baseline] null) and for comments at or under the watermark.
   *
   * Decided per comment from its id alone, so the answer does not depend on when the comment was
   * loaded: a reply that arrives after the initial list is marked exactly like one present from the
   * start, as long as the same fixed baseline is used.
   */
  fun isNew(commentId: Long, baseline: Long?): Boolean = baseline != null && commentId > baseline

  /** The largest id in [commentIds], or null when the list is empty. */
  fun maxId(commentIds: List<Long>): Long? = commentIds.maxOrNull()
}
