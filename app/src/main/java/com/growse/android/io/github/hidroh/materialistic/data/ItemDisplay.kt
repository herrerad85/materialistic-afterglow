/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data

/**
 * Pure type-driven display / classification decisions (#56), extracted from HackerNewsItem.
 *
 * No Android dependency: given the item's raw type and its title/text, decide the effective type,
 * whether it is a story-like item, and which string fills the title slot. The Spannable author/time
 * rendering stays in the model for now (stateful, span/Context-coupled).
 */
object ItemDisplay {
  /** Empty or missing raw types are treated as stories, matching the legacy default. */
  @JvmStatic
  fun effectiveType(rawType: String?): String =
      if (rawType.isNullOrEmpty()) WebItem.STORY_TYPE else rawType

  /** Story, poll, and job items are story-type; comments are not. */
  @JvmStatic
  fun isStoryType(effectiveType: String): Boolean =
      when (effectiveType) {
        WebItem.STORY_TYPE,
        WebItem.POLL_TYPE,
        WebItem.JOB_TYPE -> true
        else -> false
      }

  /** A comment shows its body text in the title slot; everything else shows its title. */
  @JvmStatic
  fun displayedTitle(effectiveType: String, text: String?, title: String?): String? =
      if (effectiveType == WebItem.COMMENT_TYPE) text else title
}
