/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

import com.growse.android.io.github.hidroh.materialistic.data.Item
import com.growse.android.io.github.hidroh.materialistic.data.ItemManager
import com.growse.android.io.github.hidroh.materialistic.data.ResponseListener
import com.growse.android.io.github.hidroh.materialistic.data.UserManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Characterizes the fail-loud fetch contract of [HackerNewsReplyDataSource] over mocked
 * [UserManager] / [ItemManager]. The poller leans on this contract for retry-on-network-failure: a
 * null item payload (getItem returns null on IOException) must surface as [ReplyFetchException],
 * not a silently dropped parent/kid. Only a genuinely empty submitted history returns an empty
 * list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HackerNewsReplyDataSourceTest {

  private val userManager = mockk<UserManager>()
  private val itemManager = mockk<ItemManager>()

  private fun source() =
      HackerNewsReplyDataSource(userManager, itemManager, UnconfinedTestDispatcher())

  private fun stubUser(submittedIds: List<String>) {
    val user = mockk<UserManager.User>()
    every { user.items } returns submittedIds.map { itemWithId(it) }.toTypedArray()
    every { userManager.getUser(any(), any(), any()) } answers
        {
          secondArg<ResponseListener<UserManager.User>>().onResponse(user)
        }
  }

  private fun itemWithId(id: String): Item {
    val item = mockk<Item>()
    every { item.id } returns id
    return item
  }

  @Test
  fun emptySubmittedHistory_returnsEmptyWithoutThrowing() = runTest {
    stubUser(emptyList())

    assertTrue(source().recentSubmittedParents("me", 20).isEmpty())
  }

  @Test
  fun poller_fetchesUserForceNetwork() = runTest {
    // E5-FU-03: the poller must bypass the 30-min user cache, else a reply to a just-submitted item
    // is invisible until the cache TTL expires. Profile UI keeps the cached 2-arg getUser.
    stubUser(emptyList())

    source().recentSubmittedParents("me", 20)

    verify { userManager.getUser("me", any(), true) }
  }

  @Test
  fun parentItemFetchReturnsNull_throwsReplyFetchException() = runTest {
    stubUser(listOf("p1"))
    every { itemManager.getItem("p1", ItemManager.MODE_NETWORK) } returns null

    val error = runCatching { source().recentSubmittedParents("me", 20) }.exceptionOrNull()

    assertTrue(error is ReplyFetchException)
  }

  @Test
  fun parentItemFetch_readsCurrentKidsFromPayload() = runTest {
    stubUser(listOf("p1"))
    val parent = mockk<Item>()
    every { parent.kids } returns longArrayOf(11L, 22L)
    every { parent.title } returns null
    every { itemManager.getItem("p1", ItemManager.MODE_NETWORK) } returns parent

    assertEquals(
        listOf(PolledParent("p1", listOf("11", "22"), titleOrText = null)),
        source().recentSubmittedParents("me", 20),
    )
  }

  @Test
  fun parentItemFetch_threadsStoryTitle() = runTest {
    stubUser(listOf("p1"))
    val parent = mockk<Item>()
    every { parent.kids } returns longArrayOf(11L)
    every { parent.title } returns "Show HN: a thing"
    every { itemManager.getItem("p1", ItemManager.MODE_NETWORK) } returns parent

    assertEquals(
        listOf(PolledParent("p1", listOf("11"), titleOrText = "Show HN: a thing")),
        source().recentSubmittedParents("me", 20),
    )
  }

  @Test
  fun childItemFetchReturnsNull_throwsReplyFetchException() = runTest {
    every { itemManager.getItem("k1", ItemManager.MODE_NETWORK) } returns null

    val error = runCatching { source().children(listOf("k1")) }.exceptionOrNull()

    assertTrue(error is ReplyFetchException)
  }

  @Test
  fun children_mapEachFetchedPayload() = runTest {
    val child = mockk<Item>()
    every { child.parent } returns "p1"
    every { child.getBy() } returns "alice"
    every { child.isDead } returns false
    every { child.isDeleted } returns false
    every { itemManager.getItem("k1", ItemManager.MODE_NETWORK) } returns child

    assertEquals(
        listOf(CandidateChild("k1", "p1", author = "alice", dead = false, deleted = false)),
        source().children(listOf("k1")),
    )
  }
}
