/*
 * Copyright (c) 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.growse.android.io.github.hidroh.materialistic.data.android.Cache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import rx.schedulers.Schedulers

/**
 * Robolectric characterization of the Room-backed saved-stories DAO and the readability [Cache]
 * (LocalCache) round-trips, exercised against a real in-memory [MaterialisticDatabase].
 */
@RunWith(RobolectricTestRunner::class)
class FavoriteCacheTest {

  private lateinit var db: MaterialisticDatabase
  private lateinit var cache: Cache

  @Before
  fun setUp() {
    val context: Context = ApplicationProvider.getApplicationContext()
    db =
        Room.inMemoryDatabaseBuilder(context, MaterialisticDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    cache =
        Cache(
            db,
            db.savedStoriesDao,
            db.readStoriesDao,
            db.readableDao,
            Schedulers.immediate(),
        )
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun savedStory_addRemoveRoundTrip() {
    val dao = db.savedStoriesDao
    assertNull(dao.selectByItemId("8863"))
    assertFalse(cache.isFavorite("8863"))

    val story =
        MaterialisticDatabase.SavedStory().apply {
          itemId = "8863"
          url = "http://www.getdropbox.com/u/2/screencast.html"
          title = "My YC app: Dropbox - Throw away your USB drive"
          time = "1175714200000"
        }
    dao.insert(story)

    val saved = dao.selectByItemId("8863")
    assertEquals("8863", saved!!.itemId)
    assertEquals("http://www.getdropbox.com/u/2/screencast.html", saved.url)
    assertEquals("My YC app: Dropbox - Throw away your USB drive", saved.title)
    assertTrue(cache.isFavorite("8863"))

    val removed = dao.deleteByItemId("8863")
    assertEquals(1, removed)
    assertNull(dao.selectByItemId("8863"))
    assertFalse(cache.isFavorite("8863"))
  }

  @Test
  fun readability_cachePutThenGet() {
    assertNull(cache.getReadability("8863"))

    cache.putReadability("8863", "<p>cached article body</p>")

    assertEquals("<p>cached article body</p>", cache.getReadability("8863"))
    // Distinct ids do not collide.
    assertNull(cache.getReadability("9999"))
  }

  @Test
  fun viewed_setThenIsViewed() {
    assertFalse(cache.isViewed("8863"))

    cache.setViewed("8863")

    assertTrue(cache.isViewed("8863"))
    assertFalse(cache.isViewed("9999"))
  }
}
