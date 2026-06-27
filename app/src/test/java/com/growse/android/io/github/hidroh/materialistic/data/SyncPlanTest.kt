/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM characterization of the offline-sync traversal decisions (#51), extracted from
 * SyncDelegate. No Android runtime is touched.
 */
class SyncPlanTest {

  @Test
  fun shouldDefer_whenConnectionDisabled() {
    assertTrue(SyncPlan.shouldDefer(false))
    assertFalse(SyncPlan.shouldDefer(true))
  }

  @Test
  fun shouldSyncArticle_requiresEnabledStoryWithUrl() {
    assertTrue(SyncPlan.shouldSyncArticle(true, isStoryType = true, hasUrl = true))
    assertFalse(SyncPlan.shouldSyncArticle(false, isStoryType = true, hasUrl = true))
    assertFalse(SyncPlan.shouldSyncArticle(true, isStoryType = false, hasUrl = true))
    assertFalse(SyncPlan.shouldSyncArticle(true, isStoryType = true, hasUrl = false))
  }

  @Test
  fun shouldSyncComments_requiresEnabledAndKids() {
    assertTrue(SyncPlan.shouldSyncComments(true, hasKids = true))
    assertFalse(SyncPlan.shouldSyncComments(false, hasKids = true))
    assertFalse(SyncPlan.shouldSyncComments(true, hasKids = false))
  }

  @Test
  fun commentsCountTowardProgress_requiresCommentsAndConnection() {
    assertTrue(SyncPlan.commentsCountTowardProgress(true, connectionEnabled = true))
    assertFalse(SyncPlan.commentsCountTowardProgress(false, connectionEnabled = true))
    assertFalse(SyncPlan.commentsCountTowardProgress(true, connectionEnabled = false))
  }
}
