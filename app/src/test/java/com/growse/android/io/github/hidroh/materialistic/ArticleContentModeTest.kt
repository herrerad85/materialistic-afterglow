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
 * Pure-JVM characterization of the article content-mode decision (#48), extracted from
 * WebFragment.load / setWebSettings. No Android runtime is touched.
 */
class ArticleContentModeTest {

  @Test
  fun forItem_hackerNewsUrlIsSelfPost() {
    assertEquals(ArticleContentMode.SELF_POST, ArticleContentMode.forItem(true))
  }

  @Test
  fun forItem_externalUrlIsRemoteArticle() {
    assertEquals(ArticleContentMode.REMOTE_ARTICLE, ArticleContentMode.forItem(false))
  }

  @Test
  fun isRemote_onlyRemoteArticleLoadsOverTheNetwork() {
    assertFalse(ArticleContentMode.SELF_POST.isRemote)
    assertTrue(ArticleContentMode.REMOTE_ARTICLE.isRemote)
  }
}
