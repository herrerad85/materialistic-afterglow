/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

import com.growse.android.io.github.hidroh.materialistic.data.Item
import com.growse.android.io.github.hidroh.materialistic.data.ItemManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterizes [ThreadTextAssembler] over a mocked [ItemManager]. No network by construction: the
 * blocking getItem is stubbed to return hand-built [Item] mocks for a small tree. Covers rank order
 * with indentation, dead/deleted skip (kids still traversed), the cost cap (stop including AND stop
 * fetching), fail-loud on a null root, and that clean rendered text (getDisplayedText) is used
 * rather than raw HTML.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadTextAssemblerTest {

  private val itemManager = mockk<ItemManager>()

  private fun assembler() = ThreadTextAssembler(itemManager, UnconfinedTestDispatcher())

  /** Builds an [Item] mock with the fields the assembler reads. */
  private fun item(
      by: String? = null,
      text: String? = null,
      title: String? = null,
      kids: LongArray? = null,
      descendants: Int = -1,
      dead: Boolean = false,
      deleted: Boolean = false,
  ): Item {
    val mock = mockk<Item>()
    every { mock.getBy() } returns by
    every { mock.displayedText } returns text
    every { mock.title } returns title
    every { mock.kids } returns kids
    every { mock.descendants } returns descendants
    every { mock.isDead } returns dead
    every { mock.isDeleted } returns deleted
    return mock
  }

  private fun stub(id: String, item: Item?) {
    every { itemManager.getItem(id, ItemManager.MODE_DEFAULT) } returns item
  }

  @Test
  fun happyPath_includesAllInRankOrderWithIndentation() = runTest {
    // story -> [c1, c2]; c1 -> [c1a]
    stub("s", item(title = "A story", kids = longArrayOf(1L, 2L), descendants = 3))
    stub("1", item(by = "alice", text = "first comment", kids = longArrayOf(11L)))
    stub("11", item(by = "carol", text = "nested reply"))
    stub("2", item(by = "bob", text = "second comment"))

    val result = assembler().assemble("s")

    assertEquals(3, result.includedCount)
    assertEquals(3, result.totalDescendants)
    assertFalse(result.truncated)
    assertTrue(result.disclosure.contains("all 3 comments"))
    // Breadth-first (D6): both top-level comments emit first (alice, bob), then the next level
    // (carol, indented 2). The cost cap keeps topic breadth this way rather than letting one deep
    // sub-thread consume the budget; depth is still encoded by indentation.
    val expectedBody =
        "[alice] first comment\n" + "[bob] second comment\n" + "  [carol] nested reply"
    assertEquals("A story\n\n$expectedBody", result.text)
    // The nested reply is indented one level deeper than the top-level comments.
    assertTrue(result.text.contains("\n  [carol] nested reply"))
  }

  @Test
  fun deletedTopComment_skipsBodyButTraversesLiveChild() = runTest {
    // story -> [deleted]; deleted -> [live child]
    stub("s", item(title = "T", kids = longArrayOf(1L), descendants = 2))
    stub(
        "1",
        item(by = "ghost", text = "should not appear", kids = longArrayOf(11L), deleted = true),
    )
    stub("11", item(by = "survivor", text = "live child body"))

    val result = assembler().assemble("s")

    assertEquals(1, result.includedCount)
    assertFalse(result.text.contains("should not appear"))
    assertTrue(result.text.contains("[survivor] live child body"))
  }

  @Test
  fun costCap_stopsIncludingAndStopsFetchingPastTheCeiling() = runTest {
    // A flat story with more top comments than MAX_COMMENTS. Each is a distinct id 1..N.
    val total = ThreadTextAssembler.MAX_COMMENTS + 10
    val kidIds = LongArray(total) { (it + 1).toLong() }
    stub("s", item(title = "Big", kids = kidIds, descendants = total))
    for (i in 1..total) {
      stub(i.toString(), item(by = "u$i", text = "body $i"))
    }

    val result = assembler().assemble("s")

    assertEquals(ThreadTextAssembler.MAX_COMMENTS, result.includedCount)
    assertEquals(total, result.totalDescendants)
    assertTrue(result.truncated)
    assertTrue(result.disclosure.contains("top ${ThreadTextAssembler.MAX_COMMENTS} of $total"))
    // A node well past the cap is never fetched once the ceiling stops the BFS. The last few ids in
    // the trailing chunk could be in-flight when the cap trips, so we assert against the very last
    // id which is comfortably beyond any partial chunk.
    verify(exactly = 0) { itemManager.getItem(total.toString(), ItemManager.MODE_DEFAULT) }
  }

  @Test
  fun rootFetchFailure_throwsThreadAssemblyException() = runTest {
    stub("s", null)

    val error = runCatching { assembler().assemble("s") }.exceptionOrNull()

    assertTrue(error is ThreadAssemblyException)
  }

  @Test
  fun usesDisplayedText_notRawHtml() = runTest {
    // getDisplayedText returns already-rendered/stripped text; the assembler must emit it verbatim.
    stub("s", item(title = null, text = null, kids = longArrayOf(1L), descendants = 1))
    stub("1", item(by = "dave", text = "hello world"))

    val result = assembler().assemble("s")

    assertTrue(result.text.contains("[dave] hello world"))
  }
}
