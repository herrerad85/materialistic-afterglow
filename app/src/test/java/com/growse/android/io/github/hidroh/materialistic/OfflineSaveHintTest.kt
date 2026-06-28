/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM characterization of the one-time offline-save hint policy (#67). */
class OfflineSaveHintTest {

  @Test
  fun showsOnFirstSaveWhenSyncDisabled() {
    assertTrue(OfflineSaveHint.shouldShow(wasAdded = true, syncEnabled = false, hintShown = false))
  }

  @Test
  fun notShownAfterItHasBeenSeen() {
    assertFalse(OfflineSaveHint.shouldShow(wasAdded = true, syncEnabled = false, hintShown = true))
  }

  @Test
  fun notShownWhenSyncEnabled() {
    assertFalse(OfflineSaveHint.shouldShow(wasAdded = true, syncEnabled = true, hintShown = false))
  }

  @Test
  fun notShownOnRemove() {
    assertFalse(
        OfflineSaveHint.shouldShow(wasAdded = false, syncEnabled = false, hintShown = false)
    )
  }
}
