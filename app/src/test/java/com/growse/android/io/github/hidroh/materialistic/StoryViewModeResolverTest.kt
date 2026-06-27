/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

import com.growse.android.io.github.hidroh.materialistic.Preferences.StoryViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM characterization of the story initial-tab decision (#47), extracted from the inline math
 * in ItemActivity.onCreate and the ItemPagerAdapter constructor. No Android runtime is touched.
 */
class StoryViewModeResolverTest {

  @Test
  fun resolveStoryViewMode_openCommentsFlagWinsOverDefault() {
    assertEquals(
        StoryViewMode.Comment,
        StoryViewModeResolver.resolveStoryViewMode(true, StoryViewMode.Article),
    )
    assertEquals(
        StoryViewMode.Comment,
        StoryViewModeResolver.resolveStoryViewMode(true, StoryViewMode.Readability),
    )
  }

  @Test
  fun resolveStoryViewMode_withoutFlagKeepsDefault() {
    assertEquals(
        StoryViewMode.Article,
        StoryViewModeResolver.resolveStoryViewMode(false, StoryViewMode.Article),
    )
    assertEquals(
        StoryViewMode.Comment,
        StoryViewModeResolver.resolveStoryViewMode(false, StoryViewMode.Comment),
    )
    assertEquals(
        StoryViewMode.Readability,
        StoryViewModeResolver.resolveStoryViewMode(false, StoryViewMode.Readability),
    )
  }

  @Test
  fun initialTabPosition_commentModeOpensCommentsTab() {
    assertEquals(0, StoryViewModeResolver.initialTabPosition(StoryViewMode.Comment, 2))
  }

  @Test
  fun initialTabPosition_articleAndReadabilityOpenArticleTab() {
    assertEquals(1, StoryViewModeResolver.initialTabPosition(StoryViewMode.Article, 2))
    assertEquals(1, StoryViewModeResolver.initialTabPosition(StoryViewMode.Readability, 2))
  }

  @Test
  fun initialTabPosition_clampsToSinglePageStory() {
    assertEquals(0, StoryViewModeResolver.initialTabPosition(StoryViewMode.Comment, 1))
    assertEquals(0, StoryViewModeResolver.initialTabPosition(StoryViewMode.Article, 1))
    assertEquals(0, StoryViewModeResolver.initialTabPosition(StoryViewMode.Readability, 1))
  }
}
