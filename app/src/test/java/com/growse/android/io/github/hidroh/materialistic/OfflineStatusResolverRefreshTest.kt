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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proves the #23 status self-refreshes: [OfflineStatusResolver.resolve] always recomputes off the
 * IO dispatcher rather than permanently trusting the in-memory value, so a previously cached
 * NOT_CACHED or PARTIALLY_CACHED upgrades once the underlying saved/archive/reader data changes,
 * and the callback fires only when the status is new or actually changed (no notify loop). An
 * [UnconfinedTestDispatcher] for both the main scope and IO makes resolve complete inline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OfflineStatusResolverRefreshTest {

  private val dispatcher = UnconfinedTestDispatcher()
  private val context: Context = ApplicationProvider.getApplicationContext()
  private lateinit var db: MaterialisticDatabase
  private lateinit var resolver: OfflineStatusResolver
  private val url = "https://example.com/refresh"

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    db =
        Room.inMemoryDatabaseBuilder(context, MaterialisticDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    val cache = Cache(db, db.savedStoriesDao, db.readStoriesDao, db.readableDao)
    resolver = OfflineStatusResolver(cache, context, dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    db.close()
    CacheableWebView.getArchiveFile(context, url).delete()
  }

  private fun markSaved(itemId: String) {
    db.savedStoriesDao.insert(
        MaterialisticDatabase.SavedStory().apply {
          this.itemId = itemId
          this.url = url
          title = "t"
          time = "0"
        }
    )
  }

  @Test
  fun notCachedUpgradesToCachedWhenDataAndArchiveAppear() {
    val seen = mutableListOf<OfflineStatus>()
    resolver.resolve("1", url) { seen.add(it) }
    assertEquals(OfflineStatus.NOT_CACHED, resolver.cached("1"))

    // Underlying data changes: the item becomes saved and an article archive appears.
    markSaved("1")
    CacheableWebView.getArchiveFile(context, url).writeText("archived")

    resolver.resolve("1", url) { seen.add(it) }
    assertEquals(OfflineStatus.CACHED, resolver.cached("1"))

    // No change on a third resolve: the callback must not fire again (avoids a notify loop).
    resolver.resolve("1", url) { seen.add(it) }
    assertEquals(OfflineStatus.CACHED, resolver.cached("1"))

    assertEquals(listOf(OfflineStatus.NOT_CACHED, OfflineStatus.CACHED), seen)
  }

  @Test
  fun partiallyCachedUpgradesToCachedAfterSave() {
    val seen = mutableListOf<OfflineStatus>()
    // Reading surface present but not saved -> partial.
    CacheableWebView.getArchiveFile(context, url).writeText("archived")
    resolver.resolve("1", url) { seen.add(it) }
    assertEquals(OfflineStatus.PARTIALLY_CACHED, resolver.cached("1"))

    // Now the item data (saved record) appears too -> fully cached.
    markSaved("1")
    resolver.resolve("1", url) { seen.add(it) }
    assertEquals(OfflineStatus.CACHED, resolver.cached("1"))

    assertEquals(listOf(OfflineStatus.PARTIALLY_CACHED, OfflineStatus.CACHED), seen)
  }

  @Test
  fun invalidateAllForcesRecompute() {
    markSaved("1")
    resolver.resolve("1", url) { /* ignore */ }
    assertEquals(OfflineStatus.PARTIALLY_CACHED, resolver.cached("1"))

    resolver.invalidateAll()
    assertEquals(null, resolver.cached("1"))
  }
}
