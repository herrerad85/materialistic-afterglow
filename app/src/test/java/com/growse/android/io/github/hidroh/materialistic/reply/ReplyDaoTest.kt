/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.growse.android.io.github.hidroh.materialistic.data.MaterialisticDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric characterization of the reply-tracking DAOs against a real in-memory
 * [MaterialisticDatabase]: baseline round-trips, account isolation, prune-by-window, and the seeded
 * marker (E5-D5).
 */
@RunWith(RobolectricTestRunner::class)
class ReplyDaoTest {

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
  fun seen_insertThenReadBack() {
    val dao = db.replySeenDao
    assertTrue(dao.seenKidIds("me").isEmpty())

    dao.insertAll(listOf(ReplySeen("me", "p1", "k1"), ReplySeen("me", "p1", "k2")))

    assertEquals(setOf("k1", "k2"), dao.seenKidIds("me").toSet())
  }

  @Test
  fun seen_insertIsIdempotentOnPrimaryKey() {
    val dao = db.replySeenDao
    dao.insertAll(listOf(ReplySeen("me", "p1", "k1")))
    // Re-observing the same kid (IGNORE on conflict) must not duplicate or throw.
    dao.insertAll(listOf(ReplySeen("me", "p1", "k1")))

    assertEquals(listOf("k1"), dao.seenKidIds("me"))
  }

  @Test
  fun seen_isAccountIsolated() {
    val dao = db.replySeenDao
    dao.insertAll(listOf(ReplySeen("alice", "p1", "k1"), ReplySeen("bob", "p9", "k9")))

    assertEquals(listOf("k1"), dao.seenKidIds("alice"))
    assertEquals(listOf("k9"), dao.seenKidIds("bob"))
  }

  @Test
  fun seen_pruneOutsideWindow_dropsAgedOutParentsOnlyForThatUser() {
    val dao = db.replySeenDao
    dao.insertAll(
        listOf(
            ReplySeen("me", "p1", "k1"),
            ReplySeen("me", "p2", "k2"),
            ReplySeen("me", "p3", "k3"), // p3 has aged out of the window
            ReplySeen("other", "p3", "k99"), // same parent id, different user, must survive
        )
    )

    dao.pruneOutsideWindow("me", listOf("p1", "p2"))

    assertEquals(setOf("k1", "k2"), dao.seenKidIds("me").toSet())
    assertEquals(listOf("k99"), dao.seenKidIds("other"))
  }

  @Test
  fun pollState_seededMarkerIsRowExistence_notRowCount() {
    val seen = db.replySeenDao
    val state = db.replyPollStateDao

    // A user whose recent items have zero replies: empty baseline, but NOT seeded yet.
    assertTrue(seen.seenKidIds("me").isEmpty())
    assertFalse(state.isSeeded("me"))

    // First successful poll writes the marker even though no kids were recorded.
    state.upsert(ReplyPollState("me", lastPolledAt = 1000L))

    assertTrue(state.isSeeded("me"))
    assertEquals(1000L, state.get("me")!!.lastPolledAt)
    // Still account-isolated.
    assertFalse(state.isSeeded("other"))
  }

  @Test
  fun pollState_upsertReplacesTimestamp() {
    val state = db.replyPollStateDao
    state.upsert(ReplyPollState("me", lastPolledAt = 1000L))
    state.upsert(ReplyPollState("me", lastPolledAt = 2000L))

    assertEquals(2000L, state.get("me")!!.lastPolledAt)
  }

  @Test
  fun deleteForUser_clearsBaselineAndState() {
    db.replySeenDao.insertAll(listOf(ReplySeen("me", "p1", "k1")))
    db.replyPollStateDao.upsert(ReplyPollState("me", 1000L))

    db.replySeenDao.deleteForUser("me")
    db.replyPollStateDao.deleteForUser("me")

    assertTrue(db.replySeenDao.seenKidIds("me").isEmpty())
    assertNull(db.replyPollStateDao.get("me"))
  }
}
