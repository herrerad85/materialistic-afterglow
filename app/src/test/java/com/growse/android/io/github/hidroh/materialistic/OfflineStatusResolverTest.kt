/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.growse.android.io.github.hidroh.materialistic.data.MaterialisticDatabase
import com.growse.android.io.github.hidroh.materialistic.data.android.Cache
import com.growse.android.io.github.hidroh.materialistic.widget.CacheableWebView
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Characterizes [OfflineStatusResolver.computeStatus] (#23) against a real in-memory
 * [MaterialisticDatabase] and real archive files in the cache dir. Verifies the accepted
 * definition: fully cached = item data (the saved/sync record) plus a reading surface (article
 * archive or reader text); partial = one but not both; not cached = neither. Self-posts (no url)
 * treat the item data as the reading surface.
 */
@RunWith(RobolectricTestRunner::class)
class OfflineStatusResolverTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private lateinit var db: MaterialisticDatabase
  private lateinit var resolver: OfflineStatusResolver

  private val savedUrl = "https://example.com/saved"
  private val archiveUrl = "https://example.com/archive"

  @Before
  fun setUp() {
    db =
        Room.inMemoryDatabaseBuilder(context, MaterialisticDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    val cache = Cache(db, db.savedStoriesDao, db.readStoriesDao, db.readableDao)
    resolver = OfflineStatusResolver(cache, context, Dispatchers.IO)
  }

  @After
  fun tearDown() {
    db.close()
    listOf(savedUrl, archiveUrl, "https://example.com/x").forEach {
      CacheableWebView.getArchiveFile(context, it).delete()
    }
  }

  private fun markSaved(itemId: String, url: String?) {
    val saved =
        MaterialisticDatabase.SavedStory().apply {
          this.itemId = itemId
          this.url = url
          title = "t"
          time = "0"
        }
    db.savedStoriesDao.insert(saved)
  }

  private fun writeArchive(url: String) {
    CacheableWebView.getArchiveFile(context, url).writeText("archived")
  }

  @Test
  fun notCached_whenNothingPresent() {
    assertEquals(OfflineStatus.NOT_CACHED, resolver.computeStatus("1", "https://example.com/x"))
  }

  @Test
  fun partiallyCached_whenSavedButNoReadingSurface() {
    markSaved("1", savedUrl)
    assertEquals(OfflineStatus.PARTIALLY_CACHED, resolver.computeStatus("1", savedUrl))
  }

  @Test
  fun partiallyCached_whenArchivePresentButNotSaved() {
    writeArchive(archiveUrl)
    assertEquals(OfflineStatus.PARTIALLY_CACHED, resolver.computeStatus("1", archiveUrl))
  }

  @Test
  fun partiallyCached_whenReaderTextPresentButNotSaved() {
    db.readableDao.insert(MaterialisticDatabase.Readable("1", "<p>reader</p>"))
    assertEquals(
        OfflineStatus.PARTIALLY_CACHED,
        resolver.computeStatus("1", "https://example.com/x"),
    )
  }

  @Test
  fun cached_whenSavedAndArchivePresent() {
    markSaved("1", archiveUrl)
    writeArchive(archiveUrl)
    assertEquals(OfflineStatus.CACHED, resolver.computeStatus("1", archiveUrl))
  }

  @Test
  fun cached_whenSavedAndReaderTextPresent() {
    markSaved("1", savedUrl)
    db.readableDao.insert(MaterialisticDatabase.Readable("1", "<p>reader</p>"))
    assertEquals(OfflineStatus.CACHED, resolver.computeStatus("1", savedUrl))
  }

  @Test
  fun selfPost_cachedWhenSaved_notCachedWhenNot() {
    // No url: the item data itself is the reading surface, so a saved self-post is fully cached.
    assertEquals(OfflineStatus.NOT_CACHED, resolver.computeStatus("1", null))
    markSaved("1", null)
    assertEquals(OfflineStatus.CACHED, resolver.computeStatus("1", null))
  }
}
