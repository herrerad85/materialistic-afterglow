/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * G6 lifecycle glue between the comment thread and the [CommentSeen] baseline. Java-callable from
 * [com.growse.android.io.github.hidroh.materialistic.ItemFragment], keeps all Room access off the
 * main thread, and splits the one-shot baseline read ([loadBaseline]) from the monotonic advance
 * ([advanceBaseline]) so the display watermark and the persisted value stay independent.
 *
 * Own a single instance per fragment and call [cancel] when the fragment is destroyed so an
 * in-flight read/write cannot deliver to a dead view or leak the scope.
 */
class CommentSeenTracker {

  /** Delivers the stored baseline back on the main thread (null on first visit). */
  fun interface BaselineCallback {
    fun onBaseline(baseline: Long?)
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /**
   * Read [storyId]'s baseline ONCE per thread open and hand it to [callback] on the main thread.
   *
   * This is the display watermark: the caller holds it fixed for the whole visit so every loaded
   * comment, including replies that arrive after the initial list, is marked against the same
   * value. Reading is kept separate from [advanceBaseline] so the persisted value can move forward
   * during the visit without changing what this visit marks (read-before-write is preserved by the
   * caller only advancing after this callback has delivered).
   */
  fun loadBaseline(context: Context, storyId: String, callback: BaselineCallback) {
    scope.launch {
      val baseline = MaterialisticDatabase.getInstance(context).commentSeenDao.maxSeen(storyId)
      withContext(Dispatchers.Main) { callback.onBaseline(baseline) }
    }
  }

  /**
   * Advance [storyId]'s persisted baseline to [maxSeenId], monotonically. Safe to call repeatedly
   * as more comments load, so later-loaded replies still move the baseline forward and are not
   * re-flagged next visit. The advance is atomic in the DAO ([CommentSeenDao.advanceMaxSeen]), so
   * concurrent advances on the IO pool cannot regress the baseline.
   */
  fun advanceBaseline(context: Context, storyId: String, maxSeenId: Long) {
    scope.launch {
      MaterialisticDatabase.getInstance(context).commentSeenDao.advanceMaxSeen(storyId, maxSeenId)
    }
  }

  fun cancel() {
    scope.cancel()
  }
}
