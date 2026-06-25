/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

import com.growse.android.io.github.hidroh.materialistic.HackerNews
import com.growse.android.io.github.hidroh.materialistic.IoDispatcher
import com.growse.android.io.github.hidroh.materialistic.data.Item
import com.growse.android.io.github.hidroh.materialistic.data.ItemManager
import com.growse.android.io.github.hidroh.materialistic.data.ResponseListener
import com.growse.android.io.github.hidroh.materialistic.data.UserManager
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * The live [ReplyDataSource], adapting the existing HN layer (ADR-0004). The user profile arrives
 * through the Rx-backed [UserManager.getUser] callback, bridged with [suspendCancellableCoroutine];
 * each parent and candidate child is then read with the blocking [ItemManager.getItem] on the
 * shared [IoDispatcher]. We use the HackerNews-backed [ItemManager] (not Algolia) so kids / dead /
 * deleted come from the canonical Firebase payload. Live correctness is validated by G3 device-QA;
 * this stage only needs to compile and bind. A failed user OR item fetch throws
 * ([ReplyFetchException]) so the worker retries; only a genuinely empty submitted history returns
 * an empty list. We never silently drop a parent or kid we expected to fetch (that would masquerade
 * as "no submitted items" or "no reply", losing a notification).
 */
class HackerNewsReplyDataSource
@Inject
constructor(
    private val userManager: UserManager,
    @HackerNews private val itemManager: ItemManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ReplyDataSource {

  override suspend fun recentSubmittedParents(
      username: String,
      limit: Int,
  ): List<PolledParent> {
    val user =
        suspendCancellableCoroutine<UserManager.User?> { cont ->
          userManager.getUser(
              username,
              object : ResponseListener<UserManager.User> {
                override fun onResponse(response: UserManager.User?) {
                  if (cont.isActive) cont.resume(response)
                }

                override fun onError(errorMessage: String?) {
                  if (cont.isActive) {
                    cont.resumeWithException(
                        ReplyFetchException(errorMessage ?: "user fetch failed")
                    )
                  }
                }
              },
              // Force-network: bypass the 30-min user cache so a reply to an item submitted in the
              // last poll window is seen now, not up to ~30 min later (E5-FU-03).
              true,
          )
        } ?: throw ReplyFetchException("user fetch returned null")

    // The submitted array is most-recent-first (ADR-0004 N=20). The blocking item fetch reads each
    // parent's current direct kid ids straight from its payload.
    val parentIds = user.items.take(limit).mapNotNull { it.id }.filter { it.isNotEmpty() }
    return withContext(ioDispatcher) {
      parentIds.map { parentId ->
        // A null item is a fetch failure (getItem returns null on IOException), NOT "parent gone":
        // a
        // deleted HN item still returns a payload. Throw so the worker retries; dropping it here
        // could empty the whole window and prune the baseline as if the user had no submissions.
        val item =
            itemManager.getItem(parentId, ItemManager.MODE_NETWORK)
                ?: throw ReplyFetchException("parent item fetch failed: $parentId")
        // Best-effort title only; a comment-body snippet is deferred. Comment parents have no title
        // (null), so the notification falls back to a generic body.
        PolledParent(
            parentId = parentId,
            kidIds = item.kidIdStrings(),
            titleOrText = item.title?.takeIf { it.isNotBlank() },
        )
      }
    }
  }

  override suspend fun children(kidIds: List<String>): List<CandidateChild> =
      withContext(ioDispatcher) {
        kidIds.map { kidId ->
          // Same contract as parents: a null payload is a transient fetch failure, not a notifiable
          // outcome. Throwing retries the pass while the kid is still unseen, so a flaky child
          // fetch
          // can't silently lose the reply notification.
          val child =
              itemManager.getItem(kidId, ItemManager.MODE_NETWORK)
                  ?: throw ReplyFetchException("child item fetch failed: $kidId")
          CandidateChild(
              kidId = kidId,
              parentId = child.parent ?: "",
              author = child.getBy(),
              dead = child.isDead,
              deleted = child.isDeleted,
          )
        }
      }

  private fun Item.kidIdStrings(): List<String> =
      (kids ?: LongArray(0)).map(java.lang.Long::toString)
}

/** A genuine HN fetch failure. The worker maps it to a retry; an empty result is not this. */
class ReplyFetchException(message: String) : Exception(message)
