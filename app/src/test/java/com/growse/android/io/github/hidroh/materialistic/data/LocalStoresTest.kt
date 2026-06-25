/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.growse.android.io.github.hidroh.materialistic.data.android.Cache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Characterizes the de-rx'd local stores ([ViewedItemStore], [FavoriteManager]) against a real
 * in-memory [MaterialisticDatabase] and [Cache]. An [UnconfinedTestDispatcher] (also installed as
 * Main) makes the fire-and-forget coroutines run inline, so the persisted effect is observable
 * synchronously.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LocalStoresTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val dispatcher = UnconfinedTestDispatcher()
  private lateinit var db: MaterialisticDatabase
  private lateinit var cache: Cache

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    db =
        Room.inMemoryDatabaseBuilder(context, MaterialisticDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    cache = Cache(db, db.savedStoriesDao, db.readStoriesDao, db.readableDao)
  }

  @After
  fun tearDown() {
    db.close()
    Dispatchers.resetMain()
  }

  private fun savedStory(id: String) =
      MaterialisticDatabase.SavedStory().apply {
        itemId = id
        url = "http://example.com/$id"
        title = "title $id"
        time = "1175714200000"
      }

  @Test
  fun viewedItemStore_isViewed_reflectsCache() {
    val store = ViewedItemStore(dispatcher, cache)
    assertFalse(store.isViewed(null))
    assertFalse(store.isViewed(""))
    assertFalse(store.isViewed("8863"))

    cache.setViewed("8863")

    assertTrue(store.isViewed("8863"))
    assertFalse(store.isViewed("9999"))
  }

  @Test
  fun viewedItemStore_view_marksViewedOffMainThread() {
    val store = ViewedItemStore(dispatcher, cache)
    assertFalse(cache.isViewed("8863"))

    store.view("8863") // unconfined dispatcher runs the launch inline

    assertTrue(cache.isViewed("8863"))
  }

  @Test
  fun favoriteManager_check_reflectsCache() {
    val manager = FavoriteManager(cache, dispatcher, db.savedStoriesDao)
    assertFalse(manager.check(null))
    assertFalse(manager.check(""))
    assertFalse(manager.check("8863"))

    db.savedStoriesDao.insert(savedStory("8863"))

    assertTrue(manager.check("8863"))
  }

  @Test
  fun favoriteManager_remove_deletesFromDao() {
    val manager = FavoriteManager(cache, dispatcher, db.savedStoriesDao)
    db.savedStoriesDao.insert(savedStory("8863"))
    assertNotNull(db.savedStoriesDao.selectByItemId("8863"))

    manager.remove(context, "8863") // unconfined dispatcher runs the launch inline

    assertNull(db.savedStoriesDao.selectByItemId("8863"))
    assertFalse(manager.check("8863"))
  }
}
