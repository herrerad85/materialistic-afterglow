/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

import com.growse.android.io.github.hidroh.materialistic.IoDispatcher
import com.growse.android.io.github.hidroh.materialistic.accounts.AccountSession
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The result of one poll pass. The worker maps these to WorkManager outcomes. */
enum class PollOutcome {
  /** Toggle off or no active session: nothing fetched, nothing written, nothing notified. */
  SKIPPED,
  /** The poll ran to completion (seeded silently, detected replies, or pruned an empty window). */
  COMPLETED,
}

/**
 * The testable core of the reply-notification worker (E5-D7 G2 / ADR-0004). Pure orchestration over
 * the [ReplyDataSource] seam, the two reply DAOs, the [AccountSession], the [ReplyNotifier] port,
 * and the opt-in [ReplyNotificationToggle]; the detection logic itself lives in the pure
 * [ReplyDetector]. No Android, no WorkManager, no notification APIs here, so it unit-tests with a
 * fake source + in-memory Room.
 *
 * One pass: skip when disabled or logged out; capture the seeded flag *before* writing the marker;
 * seed silently on the first poll; otherwise diff current kids against the baseline, fetch and
 * filter the candidate children *before* touching Room (so a fetch failure retries with the new
 * kids still unseen, never marking a kid seen-but-unnotified), then persist the new kids and prune
 * the baseline to the recent window, and finally hand the survivors to the notifier. A genuinely
 * empty submitted list prunes the whole baseline (never via an empty-window delete) and notifies
 * nothing. A data-source exception propagates so the worker can retry.
 */
class ReplyPoller
@Inject
constructor(
    private val dataSource: ReplyDataSource,
    private val replySeenDao: ReplySeenDao,
    private val replyPollStateDao: ReplyPollStateDao,
    private val accountSession: AccountSession,
    private val notifier: ReplyNotifier,
    private val toggle: ReplyNotificationToggle,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

  /** Run one poll. May throw on a data-source fetch failure (the worker maps that to a retry). */
  suspend fun pollOnce(): PollOutcome {
    if (!toggle.isEnabled()) return PollOutcome.SKIPPED
    val username = accountSession.activeUsername ?: return PollOutcome.SKIPPED

    // Capture seeded state BEFORE upserting the marker: this poll's notify behaviour depends on
    // whether the user was already seeded when it started, not after.
    val wasSeeded = withContext(ioDispatcher) { replyPollStateDao.isSeeded(username) }

    val parents = dataSource.recentSubmittedParents(username, RECENT_LIMIT)

    if (parents.isEmpty()) {
      // Genuine empty submitted window (NOT a fetch error: that throws). Never call
      // pruneOutsideWindow with an empty window (it deletes every row via NOT IN ()); clear the
      // whole baseline and keep the user seeded.
      withContext(ioDispatcher) {
        replySeenDao.deleteForUser(username)
        replyPollStateDao.upsert(ReplyPollState(username, now()))
      }
      return PollOutcome.COMPLETED
    }

    val seen = withContext(ioDispatcher) { replySeenDao.seenKidIds(username).toSet() }
    val result = ReplyDetector.detect(parents, seen, wasSeeded)

    // Fetch and filter children BEFORE mutating Room. On a child fetch failure the whole pass
    // retries with the new kids still UNSEEN, so a transient error never marks a kid
    // seen-but-unnotified. The silent first seed (E5-D2) skips this and notifies nothing.
    val toNotify =
        if (wasSeeded && result.notifyCandidates.isNotEmpty()) {
          val children = dataSource.children(result.notifyCandidates.map { it.kidId })
          ReplyDetector.notifiable(children, username)
        } else {
          emptyList()
        }

    withContext(ioDispatcher) {
      // Persist the baseline only after a successful fetch and BEFORE notifying, so a crash here
      // can only drop a notification, never re-emit one (at-most-once).
      replySeenDao.insertAll(result.newKids.map { ReplySeen(username, it.parentId, it.kidId) })
      replySeenDao.pruneOutsideWindow(username, parents.map { it.parentId })
      replyPollStateDao.upsert(ReplyPollState(username, now()))
    }

    if (toNotify.isNotEmpty()) {
      // Best-effort notification context: map each polled parent to its title (null for comment
      // parents), then attach it to the child's notification (E5-D8 / ADR-0004).
      val parentTitles = parents.associate { it.parentId to it.titleOrText }
      notifier.notify(
          toNotify.map { child ->
            NotifiableReply(
                kidId = child.kidId,
                parentId = child.parentId,
                author = child.author.orEmpty(),
                parentTitleOrText = parentTitles[child.parentId],
            )
          }
      )
    }

    return PollOutcome.COMPLETED
  }

  private fun now(): Long = System.currentTimeMillis()

  companion object {
    /**
     * ADR-0004 N: poll only the user's most-recent submitted items. Internal constant, not a UI.
     */
    const val RECENT_LIMIT = 20
  }
}
