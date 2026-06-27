/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Characterization of the sync progress accounting (#52), extracted from SyncDelegate. Robolectric
 * supplies a working android.text.TextUtils for the self-vs-kid id match; MockK drives the item.
 */
@RunWith(RobolectricTestRunner::class)
class SyncProgressTest {

  @Test
  fun max_isSelfPlusPlaceholderKidPlusArticle() {
    assertEquals(1, SyncProgress("1", false, false).max)
    assertEquals(2, SyncProgress("1", true, false).max)
    assertEquals(101, SyncProgress("1", false, true).max)
    assertEquals(102, SyncProgress("1", true, true).max)
  }

  @Test
  fun progress_startsAtZero() {
    assertEquals(0, SyncProgress("1", true, true).progress)
  }

  @Test
  fun updateArticle_advancesProgressKeepingMax() {
    val p = SyncProgress("1", false, true)
    p.updateArticle(40, 100)
    assertEquals(40, p.progress)
    assertEquals(101, p.max)
  }

  @Test
  fun finishSelf_recordsTitleAndExpandsToImmediateKidCount() {
    val item = mockk<HackerNewsItem>(relaxed = true)
    every { item.title } returns "Story"
    every { item.kids } returns longArrayOf(10, 20, 30)
    val p = SyncProgress("5", true, false)
    p.finishItem("5", item, true)
    assertEquals("Story", p.title)
    assertEquals(4, p.max) // 1 self + 3 kids
    assertEquals(1, p.progress) // self counted
  }

  @Test
  fun finishItem_nonSelfIdCountsAsAFinishedKid() {
    val p = SyncProgress("5", true, false)
    p.finishItem("99", null, true)
    assertEquals(1, p.progress) // 0 self + 1 kid
  }

  @Test
  fun finishSelf_nullItemStillCompletesSelfAndClearsKids() {
    val p = SyncProgress("5", true, false)
    p.finishItem("5", null, true)
    assertNull(p.title)
    assertEquals(1, p.max) // self only, kids cleared
    assertEquals(1, p.progress) // a failed self-fetch still finishes self
  }
}
