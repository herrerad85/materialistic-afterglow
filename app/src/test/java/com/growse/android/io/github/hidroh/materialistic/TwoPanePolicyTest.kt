/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM characterization of the two-pane master/detail decisions (#59), extracted from
 * BaseListActivity. No Android runtime is touched.
 */
class TwoPanePolicyTest {

  @Test
  fun decideSelection_singlePaneOpensOnlyWhenAnItemIsSelected() {
    assertEquals(
        SelectionRouting(SelectionAction.OPEN_SINGLE_PANE, false),
        TwoPanePolicy.decideSelection(multiPane = false, previousId = null, newId = "5"),
    )
    assertEquals(
        SelectionRouting(SelectionAction.NONE, false),
        TwoPanePolicy.decideSelection(multiPane = false, previousId = "5", newId = null),
    )
  }

  @Test
  fun decideSelection_multiPaneReselectingSameItemIsNoOp() {
    assertEquals(
        SelectionRouting(SelectionAction.NONE, false),
        TwoPanePolicy.decideSelection(multiPane = true, previousId = "5", newId = "5"),
    )
  }

  @Test
  fun decideSelection_multiPaneInvalidatesMenuOnlyWhenSelectionEmptiesOrFills() {
    // empty -> set
    assertEquals(
        SelectionRouting(SelectionAction.OPEN_MULTI_PANE, true),
        TwoPanePolicy.decideSelection(multiPane = true, previousId = null, newId = "5"),
    )
    // set -> empty
    assertEquals(
        SelectionRouting(SelectionAction.OPEN_MULTI_PANE, true),
        TwoPanePolicy.decideSelection(multiPane = true, previousId = "5", newId = null),
    )
    // set -> different set: no menu change
    assertEquals(
        SelectionRouting(SelectionAction.OPEN_MULTI_PANE, false),
        TwoPanePolicy.decideSelection(multiPane = true, previousId = "5", newId = "6"),
    )
    // empty -> empty: still opens (shows the empty pane), no menu change
    assertEquals(
        SelectionRouting(SelectionAction.OPEN_MULTI_PANE, false),
        TwoPanePolicy.decideSelection(multiPane = true, previousId = null, newId = null),
    )
  }

  @Test
  fun selectionRouting_fieldsAreReadable() {
    val routing = TwoPanePolicy.decideSelection(multiPane = false, previousId = null, newId = "1")
    assertEquals(SelectionAction.OPEN_SINGLE_PANE, routing.action)
    assertFalse(routing.invalidateMenu)
    assertTrue(TwoPanePolicy.decideSelection(true, null, "1").invalidateMenu)
  }

  @Test
  fun shouldOpenExternal_onlyWithExternalBrowserAndNonCommentView() {
    assertTrue(TwoPanePolicy.shouldOpenExternal(true, Preferences.StoryViewMode.Article))
    assertTrue(TwoPanePolicy.shouldOpenExternal(true, Preferences.StoryViewMode.Readability))
    // Comments view forces the in-app screen even with external browser on
    assertFalse(TwoPanePolicy.shouldOpenExternal(true, Preferences.StoryViewMode.Comment))
    assertFalse(TwoPanePolicy.shouldOpenExternal(false, Preferences.StoryViewMode.Article))
    assertFalse(TwoPanePolicy.shouldOpenExternal(false, Preferences.StoryViewMode.Comment))
  }

  @Test
  fun shouldExitFullscreenOnBack_onlyWhenMultiPaneAndFullscreen() {
    assertTrue(TwoPanePolicy.shouldExitFullscreenOnBack(multiPane = true, fullscreen = true))
    assertFalse(TwoPanePolicy.shouldExitFullscreenOnBack(multiPane = true, fullscreen = false))
    assertFalse(TwoPanePolicy.shouldExitFullscreenOnBack(multiPane = false, fullscreen = true))
    assertFalse(TwoPanePolicy.shouldExitFullscreenOnBack(multiPane = false, fullscreen = false))
  }
}
