/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

import android.content.Context
import androidx.annotation.WorkerThread
import com.growse.android.io.github.hidroh.materialistic.data.MaterialisticDatabase
import com.growse.android.io.github.hidroh.materialistic.widget.CacheableWebView
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Cache

/**
 * Computes offline-storage usage and clears the regenerable caches (#24). Every method touches disk
 * or the database and is meant to run off the main thread. Each clear is scoped to exactly one
 * cache and never deletes saved stories, read markers, or any other Room table.
 */
@Singleton
class OfflineStorageManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val savedStoriesDao: MaterialisticDatabase.SavedStoriesDao,
    private val readableDao: MaterialisticDatabase.ReadableDao,
    private val httpCache: Cache,
) {

  @WorkerThread
  fun computeStats(): OfflineStorageStats {
    val archives = CacheableWebView.listArchiveFiles(context)
    return OfflineStorageStats(
        savedStoryCount = savedStoriesDao.count(),
        archiveCount = archives.size,
        archiveBytes = archives.sumOf { it.length() },
        readerTextBytes = readableDao.totalContentBytes(),
        httpCacheBytes = httpCacheSize(),
    )
  }

  /** Deletes only this app's `webarchive-*.mht` files. Returns the bytes actually freed. */
  @WorkerThread
  fun clearArticleArchives(): Long {
    var freed = 0L
    for (file in CacheableWebView.listArchiveFiles(context)) {
      val size = file.length()
      if (file.delete()) {
        freed += size
      }
    }
    return freed
  }

  /**
   * Deletes only the `readable` rows (reader text). Saved stories and read markers are untouched.
   */
  @WorkerThread
  fun clearReaderText(): Long {
    val freed = readableDao.totalContentBytes()
    readableDao.deleteAll()
    return freed
  }

  /**
   * Evicts the shared OkHttp response cache via its own API. Returns the size freed (approximate).
   */
  @WorkerThread
  fun clearHttpCache(): Long {
    val freed = httpCacheSize()
    try {
      httpCache.evictAll()
    } catch (_: IOException) {
      // best-effort: a partially-evicted cache is still smaller, and the next compute reflects
      // reality
    }
    return freed
  }

  private fun httpCacheSize(): Long =
      try {
        httpCache.size()
      } catch (_: IOException) {
        0L
      }
}
