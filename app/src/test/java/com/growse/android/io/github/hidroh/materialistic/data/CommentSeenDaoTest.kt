/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric checks for the G6 baseline DAO against a real in-memory [MaterialisticDatabase]. The
 * advance is monotonic: a lower advance arriving after a higher one cannot lower the stored
 * baseline (the read-modify-write race the tracker used to be exposed to is now closed in one
 * transaction).
 */
@RunWith(RobolectricTestRunner::class)
class CommentSeenDaoTest {

  private lateinit var db: MaterialisticDatabase

  @Before
  fun setUp() {
    val context: Context = ApplicationProvider.getApplicationContext()
    db =
        Room.inMemoryDatabaseBuilder(context, MaterialisticDatabase::class.java)
            .allowMainThreadQueries()
            .build()
  }

  @After fun tearDown() = db.close()

  @Test
  fun advance_firstVisit_insertsBaseline() {
    val dao = db.commentSeenDao
    assertNull(dao.maxSeen("s1"))
    dao.advanceMaxSeen("s1", 100)
    assertEquals(100L, dao.maxSeen("s1"))
  }

  @Test
  fun advance_higher_raisesBaseline() {
    val dao = db.commentSeenDao
    dao.advanceMaxSeen("s1", 100)
    dao.advanceMaxSeen("s1", 200)
    assertEquals(200L, dao.maxSeen("s1"))
  }

  @Test
  fun advance_lowerAfterHigher_doesNotLowerBaseline() {
    val dao = db.commentSeenDao
    dao.advanceMaxSeen("s1", 150)
    dao.advanceMaxSeen("s1", 100) // a lower advance landing after a higher one (the race)
    assertEquals(150L, dao.maxSeen("s1")) // must not regress
  }
}
