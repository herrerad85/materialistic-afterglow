/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

/**
 * A snapshot of offline/cache storage usage (#24). [savedStoryCount] is user-saved data shown for
 * context only; the byte fields are regenerable caches that the user can clear. [cacheBytes] is the
 * total regenerable footprint (it deliberately excludes saved stories, which are user data).
 */
data class OfflineStorageStats(
    val savedStoryCount: Int,
    val archiveCount: Int,
    val archiveBytes: Long,
    val readerTextBytes: Long,
    val httpCacheBytes: Long,
) {
  val cacheBytes: Long
    get() = archiveBytes + readerTextBytes + httpCacheBytes
}
