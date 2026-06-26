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
import com.growse.android.io.github.hidroh.materialistic.widget.CacheableWebView
import java.io.File
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Characterizes [OfflineStorageManager] (#24): stats counting and the scoped clear actions. Uses a
 * real in-memory [MaterialisticDatabase], real `webarchive-*.mht` files in the cache dir, and a
 * real OkHttp [Cache]. Proves each clear frees only its own cache and never deletes saved stories,
 * read markers, or unrelated cache-dir files.
 */
@RunWith(RobolectricTestRunner::class)
class OfflineStorageManagerTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private lateinit var db: MaterialisticDatabase
  private lateinit var httpCache: Cache
  private lateinit var manager: OfflineStorageManager

  private val url1 = "https://example.com/a"
  private val url2 = "https://example.com/b"
  private val unrelated = File(context.cacheDir, "unrelated.bin")

  @Before
  fun setUp() {
    db =
        Room.inMemoryDatabaseBuilder(context, MaterialisticDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    httpCache = Cache(File(context.cacheDir, "http-cache-test"), 1024 * 1024)
    manager = OfflineStorageManager(context, db.savedStoriesDao, db.readableDao, httpCache)
  }

  @After
  fun tearDown() {
    db.close()
    CacheableWebView.listArchiveFiles(context).forEach { it.delete() }
    unrelated.delete()
    httpCache.delete()
  }

  private fun writeArchive(url: String, content: String) {
    CacheableWebView.getArchiveFile(context, url).writeText(content)
  }

  private fun saveStory(itemId: String) {
    db.savedStoriesDao.insert(
        MaterialisticDatabase.SavedStory().apply {
          this.itemId = itemId
          url = url1
          title = "t"
          time = "0"
        }
    )
  }

  @Test
  fun computeStats_countsArchivesReaderTextAndSaved() {
    writeArchive(url1, "0123456789") // 10 bytes
    writeArchive(url2, "01234") // 5 bytes
    db.readableDao.insert(MaterialisticDatabase.Readable("1", "abcdef")) // 6 bytes
    saveStory("1")

    val stats = manager.computeStats()

    assertEquals(2, stats.archiveCount)
    assertEquals(15L, stats.archiveBytes)
    assertEquals(6L, stats.readerTextBytes)
    assertEquals(1, stats.savedStoryCount)
    // Total cache excludes saved stories (user data) but sums the regenerable caches.
    assertEquals(
        stats.archiveBytes + stats.readerTextBytes + stats.httpCacheBytes,
        stats.cacheBytes,
    )
  }

  @Test
  fun clearArticleArchives_deletesOnlyWebarchiveFiles() {
    writeArchive(url1, "0123456789")
    writeArchive(url2, "01234")
    unrelated.writeText("keep me")

    val freed = manager.clearArticleArchives()

    assertEquals(15L, freed)
    assertEquals(0, CacheableWebView.listArchiveFiles(context).size)
    assertFalse(CacheableWebView.getArchiveFile(context, url1).exists())
    assertTrue("clearing archives must not touch unrelated cache files", unrelated.exists())
  }

  @Test
  fun clearReaderText_deletesReaderRowsButNotSavedOrReadMarkers() {
    db.readableDao.insert(MaterialisticDatabase.Readable("1", "abcdef"))
    saveStory("1")
    db.readStoriesDao.insert(MaterialisticDatabase.ReadStory("1"))

    val freed = manager.clearReaderText()

    assertEquals(6L, freed)
    assertEquals(0L, db.readableDao.totalContentBytes())
    assertNull(db.readableDao.selectByItemId("1"))
    // Saved stories and read markers are user/app state, never touched by a cache clear.
    assertEquals(1, db.savedStoriesDao.count())
    assertNotNull(db.savedStoriesDao.selectByItemId("1"))
    assertNotNull(db.readStoriesDao.selectByItemId("1"))
  }

  @Test
  fun clearHttpCache_evictsPopulatedCacheWithoutTouchingArchivesOrDatabase() {
    writeArchive(url1, "0123456789")
    db.readableDao.insert(MaterialisticDatabase.Readable("1", "abcdef"))

    // Populate the real OkHttp cache with a cacheable response fetched through it.
    val server = MockWebServer()
    server.enqueue(MockResponse().setBody("cached body").addHeader("Cache-Control", "max-age=600"))
    server.start()
    try {
      val client = OkHttpClient.Builder().cache(httpCache).build()
      client.newCall(Request.Builder().url(server.url("/x")).build()).execute().use {
        it.body!!.string() // read fully so the entry commits to the cache
      }
      assertTrue("expected a populated cache before clearing", httpCache.size() > 0L)

      val freed = manager.clearHttpCache()

      assertTrue("expected to free a non-empty cache", freed > 0L)
      assertEquals(0L, httpCache.size())
      // The HTTP-cache clear must not touch web archives or the database.
      assertTrue(CacheableWebView.getArchiveFile(context, url1).exists())
      assertEquals(6L, db.readableDao.totalContentBytes())
    } finally {
      server.shutdown()
    }
  }
}
