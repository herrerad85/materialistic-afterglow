/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

/** What a list selection should do in the current pane layout (#59). */
enum class SelectionAction {
  NONE,
  OPEN_MULTI_PANE,
  OPEN_SINGLE_PANE,
}

/** The routing for one selection, plus whether the options menu must be refreshed. */
data class SelectionRouting(
    @JvmField val action: SelectionAction,
    @JvmField val invalidateMenu: Boolean,
)

/**
 * Pure master/detail two-pane decisions (#59), extracted from BaseListActivity.
 *
 * No Android dependency: the activity keeps performing the side effects (assigning the selection,
 * invalidating the menu, opening a pane, launching an intent, sending the fullscreen broadcast);
 * this only decides what should happen.
 */
object TwoPanePolicy {
  /**
   * Route a list selection. In single-pane mode a non-null item opens the detail screen, a null
   * selection does nothing. In multi-pane mode reselecting the same item is a no-op; otherwise the
   * detail pane opens, and the menu is invalidated when the selection goes from empty to set or
   * back (the share / external items only show with a selection).
   */
  @JvmStatic
  fun decideSelection(
      multiPane: Boolean,
      previousId: String?,
      newId: String?,
  ): SelectionRouting {
    if (!multiPane) {
      val action = if (newId != null) SelectionAction.OPEN_SINGLE_PANE else SelectionAction.NONE
      return SelectionRouting(action, invalidateMenu = false)
    }
    if (previousId != null && newId != null && previousId == newId) {
      return SelectionRouting(SelectionAction.NONE, invalidateMenu = false)
    }
    val invalidateMenu = (previousId == null) != (newId == null)
    return SelectionRouting(SelectionAction.OPEN_MULTI_PANE, invalidateMenu)
  }

  /**
   * In single-pane mode, the external-browser preference opens the article URL directly, except
   * when the default story view is Comments (which always opens the in-app item screen) (#60).
   */
  @JvmStatic
  fun shouldOpenExternal(externalBrowser: Boolean, viewMode: Preferences.StoryViewMode): Boolean =
      externalBrowser && viewMode != Preferences.StoryViewMode.Comment
}
