/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM characterization of the type-driven display decisions (#56), extracted from
 * HackerNewsItem. No Android runtime is touched (the type constants are inlined String literals).
 */
class ItemDisplayTest {

  @Test
  fun effectiveType_defaultsEmptyOrNullToStory() {
    assertEquals(WebItem.STORY_TYPE, ItemDisplay.effectiveType(null))
    assertEquals(WebItem.STORY_TYPE, ItemDisplay.effectiveType(""))
    assertEquals(WebItem.COMMENT_TYPE, ItemDisplay.effectiveType(WebItem.COMMENT_TYPE))
    assertEquals(WebItem.JOB_TYPE, ItemDisplay.effectiveType(WebItem.JOB_TYPE))
  }

  @Test
  fun isStoryType_storyPollJobAreStoriesCommentsAreNot() {
    assertTrue(ItemDisplay.isStoryType(WebItem.STORY_TYPE))
    assertTrue(ItemDisplay.isStoryType(WebItem.POLL_TYPE))
    assertTrue(ItemDisplay.isStoryType(WebItem.JOB_TYPE))
    assertFalse(ItemDisplay.isStoryType(WebItem.COMMENT_TYPE))
  }

  @Test
  fun displayedTitle_commentShowsTextEverythingElseShowsTitle() {
    assertEquals("body", ItemDisplay.displayedTitle(WebItem.COMMENT_TYPE, "body", "headline"))
    assertEquals("headline", ItemDisplay.displayedTitle(WebItem.STORY_TYPE, "body", "headline"))
    assertEquals("headline", ItemDisplay.displayedTitle(WebItem.JOB_TYPE, "body", "headline"))
    assertEquals("headline", ItemDisplay.displayedTitle(WebItem.POLL_TYPE, "body", "headline"))
  }
}
