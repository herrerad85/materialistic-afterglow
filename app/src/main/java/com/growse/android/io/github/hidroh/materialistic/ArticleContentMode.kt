/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

/**
 * Which kind of content the article tab shows (#48).
 *
 * The decision is purely whether the item's link is an HN-hosted URL: such items (self / Ask / Show
 * / job posts) carry their own text and are rendered as local HTML, while everything else is an
 * external article loaded remotely in the web view. This replaces the boolean-blind `isRemote`
 * argument that drove the web settings and the now-dead Readability flag.
 *
 * This names only the content-type choice. The offline / not-available handling for remote articles
 * (#25) and the web view's own load are unchanged.
 */
enum class ArticleContentMode {
  /** The item's own HN-hosted text, rendered as local HTML. */
  SELF_POST,
  /** An external article URL loaded remotely in the web view. */
  REMOTE_ARTICLE;

  /** Remote articles load over the network with full-width / overview web settings. */
  val isRemote: Boolean
    get() = this == REMOTE_ARTICLE

  companion object {
    /** HN-hosted items show their own text; everything else is a remote article. */
    @JvmStatic
    fun forItem(isHackerNewsUrl: Boolean): ArticleContentMode =
        if (isHackerNewsUrl) SELF_POST else REMOTE_ARTICLE
  }
}
