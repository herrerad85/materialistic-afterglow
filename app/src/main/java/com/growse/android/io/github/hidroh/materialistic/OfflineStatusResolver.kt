/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.growse.android.io.github.hidroh.materialistic.data.LocalCache
import com.growse.android.io.github.hidroh.materialistic.widget.CacheableWebView
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Computes per-item offline availability (#23) off the main thread and caches the result so list
 * rebinds during scroll are cheap. "Item data plus comments" is approximated by the saved/sync
 * record (the HTTP cache is not introspected, per the accepted design); the reading surface is the
 * article web archive file or stored reader text (or, for a self-post with no url, the item data
 * itself).
 */
@Singleton
class OfflineStatusResolver
@Inject
constructor(
    private val cache: LocalCache,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
  /** Single-method callback so both Kotlin (lambda) and Java (SAM) callers stay clean. */
  fun interface OnResolved {
    fun onResolved(status: OfflineStatus)
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val results = ConcurrentHashMap<String, OfflineStatus>()

  /**
   * Last computed status for [itemId], or null if it has not been computed yet. Main-thread cheap.
   */
  fun cached(itemId: String?): OfflineStatus? = itemId?.let { results[it] }

  /** Drops the cached status for [itemId] so the next [resolve] recomputes (e.g. after a save). */
  fun invalidate(itemId: String?) {
    itemId?.let { results.remove(it) }
  }

  /** Drops every cached status (e.g. after favorites are cleared) so all rows re-resolve. */
  fun invalidateAll() {
    results.clear()
  }

  /**
   * Always recomputes the status off the main thread (so it self-refreshes after save/unsave,
   * archive creation, or reader-text creation) and updates the cache. To avoid notify loops, the
   * [onResolved] callback fires only when there was no previously cached value or when the
   * recomputed status differs from it. Use [cached] for an instant, jank-free display value before
   * calling this.
   */
  fun resolve(itemId: String?, url: String?, onResolved: OnResolved) {
    if (itemId == null) {
      onResolved.onResolved(OfflineStatus.NOT_CACHED)
      return
    }
    val previous = results[itemId]
    scope.launch {
      val status = withContext(io) { computeStatus(itemId, url) }
      results[itemId] = status
      if (previous == null || previous != status) {
        onResolved.onResolved(status)
      }
    }
  }

  @WorkerThread
  @VisibleForTesting
  internal fun computeStatus(itemId: String?, url: String?): OfflineStatus {
    val hasData = cache.isFavorite(itemId) // saved/sync record proxies item data + comments
    val hasReadingSurface =
        if (url.isNullOrEmpty()) hasData // self-post: the item data is the reading surface
        else
            CacheableWebView.getArchiveFile(context, url).exists() ||
                cache.getReadability(itemId) != null
    return when {
      hasData && hasReadingSurface -> OfflineStatus.CACHED
      hasData || hasReadingSurface -> OfflineStatus.PARTIALLY_CACHED
      else -> OfflineStatus.NOT_CACHED
    }
  }
}
