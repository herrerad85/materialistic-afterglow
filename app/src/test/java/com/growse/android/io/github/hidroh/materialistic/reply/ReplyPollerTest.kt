/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.growse.android.io.github.hidroh.materialistic.accounts.AccountSession
import com.growse.android.io.github.hidroh.materialistic.accounts.Credentials
import com.growse.android.io.github.hidroh.materialistic.accounts.SavedAccount
import com.growse.android.io.github.hidroh.materialistic.data.MaterialisticDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
 * Robolectric characterization of [ReplyPoller] against the real reply DAOs (in-memory Room), a
 * fake [ReplyDataSource], a fake [AccountSession], a recording [ReplyNotifier], and a settable
 * toggle. Covers E5-D7 G2: skip when off / logged out, the silent first seed, seeded-poll
 * detection, post-fetch self/dead/deleted filtering, and the empty-window prune.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReplyPollerTest {

  private lateinit var db: MaterialisticDatabase
  private lateinit var notifier: RecordingNotifier
  private val toggle = SettableToggle(enabled = true)
  private val session = FakeAccountSession(username = "me")

  @Before
  fun setUp() {
    val context: Context = ApplicationProvider.getApplicationContext()
    db =
        Room.inMemoryDatabaseBuilder(context, MaterialisticDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    notifier = RecordingNotifier()
  }

  @After fun tearDown() = db.close()

  private fun poller(dataSource: ReplyDataSource): ReplyPoller =
      ReplyPoller(
          dataSource = dataSource,
          replySeenDao = db.replySeenDao,
          replyPollStateDao = db.replyPollStateDao,
          accountSession = session,
          notifier = notifier,
          toggle = toggle,
          ioDispatcher = UnconfinedTestDispatcher(),
      )

  @Test
  fun toggleOff_isNoOp() = runTest {
    toggle.enabled = false
    val source = FakeReplyDataSource(parents = listOf(PolledParent("p1", listOf("k1"))))

    val outcome = poller(source).pollOnce()

    assertEquals(PollOutcome.SKIPPED, outcome)
    assertTrue(db.replySeenDao.seenKidIds("me").isEmpty())
    assertNull(db.replyPollStateDao.get("me"))
    assertFalse(notifier.wasCalled)
    assertEquals(0, source.parentCalls)
  }

  @Test
  fun noActiveSession_isNoOp() = runTest {
    session.username = null
    val source = FakeReplyDataSource(parents = listOf(PolledParent("p1", listOf("k1"))))

    val outcome = poller(source).pollOnce()

    assertEquals(PollOutcome.SKIPPED, outcome)
    assertTrue(db.replySeenDao.seenKidIds("me").isEmpty())
    assertFalse(notifier.wasCalled)
    assertEquals(0, source.parentCalls)
  }

  @Test
  fun firstSeed_isSilent_recordsBaselineButNotifiesNothing() = runTest {
    // Not seeded; parents already have kids -> baseline recorded, marker written, NOTHING notified.
    val source = FakeReplyDataSource(parents = listOf(PolledParent("p1", listOf("k1", "k2"))))

    val outcome = poller(source).pollOnce()

    assertEquals(PollOutcome.COMPLETED, outcome)
    assertEquals(setOf("k1", "k2"), db.replySeenDao.seenKidIds("me").toSet())
    assertTrue(db.replyPollStateDao.isSeeded("me"))
    assertFalse(notifier.wasCalled)
    // The silent seed never fetches child payloads.
    assertEquals(0, source.childCalls)
  }

  @Test
  fun seededPoll_detectsNewDirectReply_notifies() = runTest {
    // Seed first: k1 known.
    db.replySeenDao.insertAll(listOf(ReplySeen("me", "p1", "k1")))
    db.replyPollStateDao.upsert(ReplyPollState("me", lastPolledAt = 1L))

    // Now p1 has a new kid k2 (not self/dead/deleted).
    val source =
        FakeReplyDataSource(
            parents = listOf(PolledParent("p1", listOf("k1", "k2"))),
            children =
                listOf(CandidateChild("k2", "p1", author = "alice", dead = false, deleted = false)),
        )

    val outcome = poller(source).pollOnce()

    assertEquals(PollOutcome.COMPLETED, outcome)
    assertEquals(setOf("k1", "k2"), db.replySeenDao.seenKidIds("me").toSet())
    assertEquals(1, notifier.notified.size)
    assertEquals("k2", notifier.notified.single().kidId)
    assertEquals("alice", notifier.notified.single().author)
  }

  @Test
  fun seededPoll_filtersSelfDeadDeletedAfterChildFetch() = runTest {
    db.replySeenDao.insertAll(listOf(ReplySeen("me", "p1", "k1")))
    db.replyPollStateDao.upsert(ReplyPollState("me", lastPolledAt = 1L))

    // Four new kids; only the genuine reply (kKeep by alice) should notify.
    val source =
        FakeReplyDataSource(
            parents = listOf(PolledParent("p1", listOf("k1", "kSelf", "kDead", "kDel", "kKeep"))),
            children =
                listOf(
                    CandidateChild("kSelf", "p1", author = "me", dead = false, deleted = false),
                    CandidateChild("kDead", "p1", author = "bob", dead = true, deleted = false),
                    CandidateChild("kDel", "p1", author = "carol", dead = false, deleted = true),
                    CandidateChild("kKeep", "p1", author = "alice", dead = false, deleted = false),
                ),
        )

    poller(source).pollOnce()

    // Every new kid is persisted regardless of notifiability (at-most-once baseline).
    assertEquals(
        setOf("k1", "kSelf", "kDead", "kDel", "kKeep"),
        db.replySeenDao.seenKidIds("me").toSet(),
    )
    // Only the real reply notifies.
    assertEquals(listOf("kKeep"), notifier.notified.map { it.kidId })
  }

  @Test
  fun emptySubmittedList_prunesBaselineButStaysSeeded_andNotifiesNothing() = runTest {
    // Seeded user with an existing baseline.
    db.replySeenDao.insertAll(listOf(ReplySeen("me", "p1", "k1"), ReplySeen("me", "p2", "k2")))
    db.replyPollStateDao.upsert(ReplyPollState("me", lastPolledAt = 1L))

    // Genuine empty submitted window (NOT a fetch error).
    val source = FakeReplyDataSource(parents = emptyList())

    val outcome = poller(source).pollOnce()

    assertEquals(PollOutcome.COMPLETED, outcome)
    assertTrue(db.replySeenDao.seenKidIds("me").isEmpty())
    // Still seeded (marker survives), so a later non-empty poll won't silently re-seed.
    assertTrue(db.replyPollStateDao.isSeeded("me"))
    assertFalse(notifier.wasCalled)
  }

  @Test
  fun childFetchFailure_afterNewKidDetected_marksNothingSeenAndDoesNotAdvanceState() = runTest {
    // Seeded; a new kid k2 is detected, but fetching its child payload fails.
    db.replySeenDao.insertAll(listOf(ReplySeen("me", "p1", "k1")))
    db.replyPollStateDao.upsert(ReplyPollState("me", lastPolledAt = 1L))
    val source =
        FakeReplyDataSource(
            parents = listOf(PolledParent("p1", listOf("k1", "k2"))),
            childError = ReplyFetchException("child fetch failed"),
        )

    val error = runCatching { poller(source).pollOnce() }.exceptionOrNull()

    // The pass throws (worker retries) and leaves Room untouched: k2 stays UNSEEN so the retry can
    // still detect and notify it, and the poll marker does not advance.
    assertTrue(error is ReplyFetchException)
    assertEquals(setOf("k1"), db.replySeenDao.seenKidIds("me").toSet())
    assertEquals(1L, db.replyPollStateDao.get("me")!!.lastPolledAt)
    assertFalse(notifier.wasCalled)
  }

  @Test
  fun parentFetchFailure_doesNotPruneBaselineOrAdvanceState() = runTest {
    // A non-empty baseline exists; the parent fetch fails (NOT a genuine empty window).
    db.replySeenDao.insertAll(listOf(ReplySeen("me", "p1", "k1"), ReplySeen("me", "p2", "k2")))
    db.replyPollStateDao.upsert(ReplyPollState("me", lastPolledAt = 1L))
    val source =
        FakeReplyDataSource(parents = emptyList(), parentError = ReplyFetchException("net down"))

    val error = runCatching { poller(source).pollOnce() }.exceptionOrNull()

    // A transient fetch failure must never masquerade as an empty window and prune the baseline.
    assertTrue(error is ReplyFetchException)
    assertEquals(setOf("k1", "k2"), db.replySeenDao.seenKidIds("me").toSet())
    assertEquals(1L, db.replyPollStateDao.get("me")!!.lastPolledAt)
    assertEquals(0, source.childCalls)
  }

  // --- fakes -------------------------------------------------------------------------------------

  private class RecordingNotifier : ReplyNotifier {
    val notified = mutableListOf<NotifiableReply>()
    var wasCalled = false

    override fun notify(replies: List<NotifiableReply>) {
      wasCalled = true
      notified.addAll(replies)
    }
  }

  private class SettableToggle(var enabled: Boolean) : ReplyNotificationToggle {
    override fun isEnabled(): Boolean = enabled
  }

  private class FakeAccountSession(var username: String?) : AccountSession {
    override val activeUsername: String?
      get() = username

    override fun credentials(): Credentials? = username?.let { Credentials(it, "pw") }

    override fun savedAccounts(): List<SavedAccount> =
        username?.let { listOf(SavedAccount(it)) } ?: emptyList()

    override fun signIn(username: String, password: String) {
      this.username = username
    }

    override fun setActive(username: String) {
      this.username = username
    }

    override fun logout() {
      username = null
    }

    override fun purgeLegacySyncAccount() {}

    override fun removeAccount(username: String) {
      if (this.username == username) this.username = null
    }

    override fun startAccountMonitor() = Unit
  }

  private class FakeReplyDataSource(
      private val parents: List<PolledParent>,
      private val children: List<CandidateChild> = emptyList(),
      private val parentError: Throwable? = null,
      private val childError: Throwable? = null,
  ) : ReplyDataSource {
    var parentCalls = 0
    var childCalls = 0

    override suspend fun recentSubmittedParents(
        username: String,
        limit: Int,
    ): List<PolledParent> {
      parentCalls++
      parentError?.let { throw it }
      return parents
    }

    override suspend fun children(kidIds: List<String>): List<CandidateChild> {
      childCalls++
      childError?.let { throw it }
      return children.filter { it.kidId in kidIds }
    }
  }
}
