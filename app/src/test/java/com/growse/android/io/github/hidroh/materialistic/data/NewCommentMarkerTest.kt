/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic tests for the G6 new-comment marking. No Android deps, so plain JUnit. */
class NewCommentMarkerTest {

  @Test
  fun nullBaseline_marksNothing() {
    // First visit: no stored baseline, so no comment is "new".
    assertFalse(NewCommentMarker.isNew(12, null))
  }

  @Test
  fun marksOnlyIdsStrictlyGreaterThanBaseline() {
    assertTrue(NewCommentMarker.isNew(12, 9)) // above the last-seen max of 9
    assertFalse(NewCommentMarker.isNew(9, 9)) // the baseline itself is already seen
    assertFalse(NewCommentMarker.isNew(5, 9)) // below the baseline
  }

  @Test
  fun laterLoadedReply_isMarkedFromIdAlone() {
    // A reply discovered after the initial list (id 50) is never part of an initial diff, yet it is
    // still marked new purely because its id is past the fixed baseline of 9, exactly the property
    // that makes later-loaded replies show the marker without re-diffing the whole list.
    assertTrue(NewCommentMarker.isNew(50, 9))
  }

  @Test
  fun maxId_returnsLargest() {
    assertEquals(12L, NewCommentMarker.maxId(listOf(5, 12, 9)))
  }

  @Test
  fun maxId_emptyList_returnsNull() {
    assertNull(NewCommentMarker.maxId(emptyList()))
  }
}
