/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.growse.android.io.github.hidroh.materialistic.widget

import com.growse.android.io.github.hidroh.materialistic.data.Item
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure tests for [CommentSearch] and [CommentSearchCursor] , the find-in-thread matcher and its
 * traversal cursor. Comment body text is the already-stripped visible text the Item exposes via
 * [Item.getDisplayedText], so each mock just returns a plain string; no Android HTML is involved.
 */
class CommentSearchTest {

  private fun comment(body: String?): Item =
      mockk(relaxed = true) { every { getDisplayedText() } returns body }

  @Test
  fun `match is a case-insensitive substring of the body`() {
    val comments = listOf(comment("The Quick Brown Fox"), comment("nothing here"))

    assertEquals(listOf(0), CommentSearch.match(comments, "quick"))
    assertEquals(listOf(0), CommentSearch.match(comments, "QUICK"))
    assertEquals(listOf(0), CommentSearch.match(comments, "brown fox"))
  }

  @Test
  fun `every matching comment is returned in display order`() {
    val comments =
        listOf(comment("apples and oranges"), comment("bananas"), comment("orange juice"))

    assertEquals(listOf(0, 2), CommentSearch.match(comments, "orange"))
  }

  @Test
  fun `no match returns an empty list`() {
    val comments = listOf(comment("alpha"), comment("beta"))

    assertTrue(CommentSearch.match(comments, "gamma").isEmpty())
  }

  @Test
  fun `a blank or empty query matches nothing`() {
    val comments = listOf(comment("alpha"), comment("beta"))

    assertTrue(CommentSearch.match(comments, "").isEmpty())
    assertTrue(CommentSearch.match(comments, "   ").isEmpty())
  }

  @Test
  fun `a null body never matches and does not crash`() {
    val comments = listOf(comment(null), comment("has text"))

    assertEquals(listOf(1), CommentSearch.match(comments, "text"))
  }

  @Test
  fun `a null element (the adapter footer) never matches and indices stay aligned`() {
    // The single-page adapter ends its loaded list with a null footer; matching must skip it
    // without crashing and keep indices mapping one-to-one onto adapter positions.
    val comments = listOf(comment("first match"), comment("second match"), null)

    assertEquals(listOf(0, 1), CommentSearch.match(comments, "match"))
  }

  @Test
  fun `the cursor reports current over total starting on the first match`() {
    val cursor =
        CommentSearch.search(listOf(comment("foo"), comment("bar"), comment("foobar")), "foo")

    assertEquals(2, cursor.total)
    assertEquals(1, cursor.current)
    assertEquals(0, cursor.matchIndex)
  }

  @Test
  fun `next advances through matches and wraps around to the first`() {
    var cursor =
        CommentSearch.search(listOf(comment("foo"), comment("bar"), comment("foobar")), "foo")

    assertEquals(0, cursor.matchIndex)
    assertEquals(1, cursor.current)

    cursor = cursor.next()
    assertEquals(2, cursor.matchIndex)
    assertEquals(2, cursor.current)

    cursor = cursor.next()
    assertEquals(0, cursor.matchIndex)
    assertEquals(1, cursor.current)
  }

  @Test
  fun `prev steps back through matches and wraps around to the last`() {
    var cursor =
        CommentSearch.search(listOf(comment("foo"), comment("bar"), comment("foobar")), "foo")

    cursor = cursor.prev()
    assertEquals(2, cursor.matchIndex)
    assertEquals(2, cursor.current)

    cursor = cursor.prev()
    assertEquals(0, cursor.matchIndex)
    assertEquals(1, cursor.current)
  }

  @Test
  fun `an empty result has a zero counter and ignores next and prev`() {
    val cursor = CommentSearch.search(listOf(comment("alpha"), comment("beta")), "gamma")

    assertEquals(0, cursor.total)
    assertEquals(0, cursor.current)
    assertEquals(-1, cursor.matchIndex)

    assertEquals(-1, cursor.next().matchIndex)
    assertEquals(0, cursor.next().current)
    assertEquals(-1, cursor.prev().matchIndex)
    assertEquals(0, cursor.prev().current)
  }

  @Test
  fun `a blank query yields an empty cursor`() {
    val cursor = CommentSearch.search(listOf(comment("alpha"), comment("beta")), "  ")

    assertEquals(0, cursor.total)
    assertEquals(-1, cursor.matchIndex)
  }

  @Test
  fun `at re-anchors the cursor onto a still-present match by its list index`() {
    // The list shifted (e.g. a collapse), the active match moved to index 5; re-anchor onto it.
    val cursor = CommentSearchCursor.at(listOf(1, 5, 9), 5)

    assertEquals(3, cursor.total)
    assertEquals(2, cursor.current)
    assertEquals(5, cursor.matchIndex)
  }

  @Test
  fun `at falls back to the first match when the prior match is gone`() {
    val cursor = CommentSearchCursor.at(listOf(1, 5, 9), 7) // 7 no longer a match

    assertEquals(1, cursor.current)
    assertEquals(1, cursor.matchIndex)
  }

  @Test
  fun `at over no matches is an empty cursor`() {
    val cursor = CommentSearchCursor.at(emptyList(), 5)

    assertEquals(0, cursor.total)
    assertEquals(-1, cursor.matchIndex)
  }
}
