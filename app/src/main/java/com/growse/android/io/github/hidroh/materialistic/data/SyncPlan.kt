/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data

/**
 * Pure offline-sync traversal decisions (#51), extracted from `SyncDelegate`.
 *
 * These name what a save-for-offline job fetches for a given item: defer when offline, pull the
 * article only for an enabled story that has a URL, recurse into comments only when enabled and the
 * item has kids, and count comments toward the progress total only when both comments and the
 * connection are enabled. No Android dependency; the runtime concerns (which thread loads the
 * article, the actual fetch/recursion) stay in `SyncDelegate`.
 */
object SyncPlan {
  /** No connection for this job: persist the id for a later run instead of fetching now. */
  @JvmStatic fun shouldDefer(connectionEnabled: Boolean): Boolean = !connectionEnabled

  /** Cache the article only for an enabled story type that actually has a URL. */
  @JvmStatic
  fun shouldSyncArticle(articleEnabled: Boolean, isStoryType: Boolean, hasUrl: Boolean): Boolean =
      articleEnabled && isStoryType && hasUrl

  /** Recurse into comments only when enabled and the item has kids. */
  @JvmStatic
  fun shouldSyncComments(commentsEnabled: Boolean, hasKids: Boolean): Boolean =
      commentsEnabled && hasKids

  /**
   * The item's kids count toward the progress total only when comments are enabled and the
   * connection is enabled (an offline/deferred job never expands them).
   */
  @JvmStatic
  fun commentsCountTowardProgress(commentsEnabled: Boolean, connectionEnabled: Boolean): Boolean =
      commentsEnabled && connectionEnabled
}
