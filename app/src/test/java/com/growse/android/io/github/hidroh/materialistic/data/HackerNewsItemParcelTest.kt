/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data

import android.os.Parcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Parcel round-trip guard for HackerNewsItem (#55). The duplicate legacy favorite slot is
 * intentionally preserved in the wire layout; the first read is ignored and the canonical second
 * slot is used. These prove favorite and every following view-state field survive a round trip, and
 * that a legacy parcel with both slots reads correctly. Robolectric supplies a working
 * android.os.Parcel.
 */
@RunWith(RobolectricTestRunner::class)
class HackerNewsItemParcelTest {

  @Test
  fun viewStateSurvivesParcelRoundTrip() {
    val item = HackerNewsItem(42L)
    item.setFavorite(true)
    item.setIsViewed(true)
    item.setCollapsed(true)
    item.setContentExpanded(true)
    item.incrementScore() // score 0 -> 1, voted + pendingVoted true

    val parcel = Parcel.obtain()
    item.writeToParcel(parcel, 0)
    parcel.setDataPosition(0)
    val restored = HackerNewsItem.CREATOR.createFromParcel(parcel)
    parcel.recycle()

    assertEquals("42", restored.id)
    assertTrue(restored.isFavorite)
    assertTrue(restored.isViewed)
    assertTrue(restored.isCollapsed)
    assertTrue(restored.isContentExpanded)
    assertEquals(1, restored.score)
    assertTrue(restored.isVoted)
    assertTrue(restored.isPendingVoted)
  }

  @Test
  fun defaultsSurviveParcelRoundTrip() {
    val item = HackerNewsItem(7L)

    val parcel = Parcel.obtain()
    item.writeToParcel(parcel, 0)
    parcel.setDataPosition(0)
    val restored = HackerNewsItem.CREATOR.createFromParcel(parcel)
    parcel.recycle()

    assertEquals("7", restored.id)
    assertEquals(false, restored.isFavorite)
    assertEquals(false, restored.isViewed)
    assertEquals(false, restored.isCollapsed)
    assertEquals(false, restored.isContentExpanded)
    assertEquals(false, restored.isVoted)
  }

  @Test
  fun legacyLayoutWithBothFavoriteSlotsReadsCanonicalSlot() {
    // Legacy wire order: both favorite slots present and disagreeing, canonical (second) slot wins.
    val parcel = Parcel.obtain()
    parcel.writeLong(99L) // id
    parcel.writeString("headline") // title
    parcel.writeLong(1234L) // time
    parcel.writeString("author") // by
    parcel.writeLongArray(longArrayOf(1L, 2L)) // kids
    parcel.writeString("http://example.com") // url
    parcel.writeString("body") // text
    parcel.writeString("comment") // type
    parcel.writeInt(0) // legacy favorite slot, ignored
    parcel.writeInt(5) // descendants
    parcel.writeInt(7) // score
    parcel.writeInt(1) // canonical favorite slot -> true
    parcel.writeInt(1) // viewed
    parcel.writeInt(2) // localRevision
    parcel.writeInt(3) // level
    parcel.writeInt(0) // dead
    parcel.writeInt(0) // deleted
    parcel.writeInt(1) // collapsed
    parcel.writeInt(1) // contentExpanded
    parcel.writeInt(4) // rank
    parcel.writeInt(6) // lastKidCount
    parcel.writeInt(1) // hasNewDescendants
    parcel.writeLong(8L) // parent
    parcel.writeInt(1) // voted
    parcel.writeInt(0) // pendingVoted
    parcel.writeLong(0L) // next
    parcel.writeLong(0L) // previous
    parcel.setDataPosition(0)
    val item = HackerNewsItem.CREATOR.createFromParcel(parcel)
    parcel.recycle()

    assertEquals("99", item.id)
    assertTrue(item.isFavorite) // canonical slot (1), not the legacy slot (0)
    assertTrue(item.isViewed)
    assertTrue(item.isCollapsed)
    assertTrue(item.isContentExpanded)
    assertEquals(7, item.score)
    assertEquals(4, item.rank)
    assertTrue(item.isVoted)
    assertFalse(item.isPendingVoted)
  }
}
