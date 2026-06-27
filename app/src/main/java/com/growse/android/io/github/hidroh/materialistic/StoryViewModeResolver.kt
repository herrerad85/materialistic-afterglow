/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

import kotlin.math.min

/**
 * Pure decisions for which reading tab opens first (#47).
 *
 * The story pager always lays out the comments tab at position 0 and the article tab at position 1,
 * so the resolved index is clamped to the number of pages actually present. Readability is a dead
 * view mode (the reader parser is gone) and is treated like Article here, exactly as the legacy
 * inline math did.
 */
object StoryViewModeResolver {
  /**
   * The comments-first intent flag (EXTRA_OPEN_COMMENTS) wins over the configured default story
   * view; otherwise [Preferences.getDefaultStoryView] keeps its effect unchanged.
   */
  @JvmStatic
  fun resolveStoryViewMode(
      openComments: Boolean,
      defaultViewMode: Preferences.StoryViewMode,
  ): Preferences.StoryViewMode =
      if (openComments) Preferences.StoryViewMode.Comment else defaultViewMode

  /**
   * Comment mode opens position 0, every other mode position 1, clamped to the available
   * [pageCount] (a story with no article tab has a single page).
   */
  @JvmStatic
  fun initialTabPosition(viewMode: Preferences.StoryViewMode, pageCount: Int): Int =
      min(pageCount - 1, if (viewMode == Preferences.StoryViewMode.Comment) 0 else 1)
}
