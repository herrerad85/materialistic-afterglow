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

/**
 * Find-in-page style traversal over a set of comment matches. Holds the ordered match indices (into
 * the searched list, in display order) and a cursor that cycles through them with wraparound.
 *
 * The cursor is one-based for display, so [current] over [total] reads naturally in a find bar (for
 * example "2 / 5"). When there are no matches, [current] is 0 and [total] is 0, and [next] / [prev]
 * are no-ops. This object is immutable, it does not depend on a RecyclerView, and traversal is a
 * pure function of the match list plus the cursor, so a host can map [matchIndex] to an adapter
 * position and scroll there.
 */
class CommentSearchCursor
private constructor(
    /** Match indices into the searched list, in display order. */
    val matches: List<Int>,
    /** Zero-based position within [matches], or -1 when there are no matches. */
    private val cursor: Int,
) {

  /** Number of matches. */
  val total: Int
    get() = matches.size

  /** One-based position of the active match for display, or 0 when there are no matches. */
  val current: Int
    get() = if (cursor < 0) 0 else cursor + 1

  /**
   * Index, into the searched list, of the active match, or -1 when there are no matches. A host
   * maps this to an adapter position to scroll to and highlight.
   */
  val matchIndex: Int
    get() = if (cursor < 0) -1 else matches[cursor]

  /** Advances to the next match, wrapping past the end to the first. A no-op with no matches. */
  fun next(): CommentSearchCursor {
    if (matches.isEmpty()) return this
    return CommentSearchCursor(matches, (cursor + 1) % matches.size)
  }

  /**
   * Steps back to the previous match, wrapping past the start to the last. A no-op with no matches.
   */
  fun prev(): CommentSearchCursor {
    if (matches.isEmpty()) return this
    return CommentSearchCursor(matches, (cursor - 1 + matches.size) % matches.size)
  }

  companion object {
    /**
     * Builds a cursor over [matches], parked on the first match (or empty when [matches] is empty).
     */
    fun of(matches: List<Int>): CommentSearchCursor =
        CommentSearchCursor(matches, if (matches.isEmpty()) -1 else 0)

    /**
     * Builds a cursor over [matches] parked on the match whose searched-list index is
     * [activeIndex], falling back to the first match when [activeIndex] is not (or no longer) a
     * match. Lets a host re-anchor the cursor onto the same comment after the list shifts under it
     * (collapse / expand).
     */
    fun at(matches: List<Int>, activeIndex: Int): CommentSearchCursor {
      if (matches.isEmpty()) return CommentSearchCursor(matches, -1)
      val ordinal = matches.indexOf(activeIndex)
      return CommentSearchCursor(matches, if (ordinal >= 0) ordinal else 0)
    }
  }
}

/**
 * Pure find-in-thread matcher over the currently loaded comments. Keeps search state out of the
 * comment adapters (a god-class): the input is the loaded comment [List] in display order plus a
 * query, and the output is the ordered match indices wrapped in a [CommentSearchCursor].
 *
 * Matching is on each comment's visible body text ([Item.getDisplayedText], which the Item already
 * strips of HTML via `AppUtils.fromHtml`), compared case-insensitively as a substring. A blank or
 * empty query matches nothing. Scope is loaded-only by construction: it searches exactly the list
 * it is given, so an honest empty state ("No matches in loaded comments") is a host concern.
 *
 * The list may contain null elements (tolerated defensively, not a footer contract): a null element
 * simply never matches, and indices are kept so a match index still maps one-to-one onto the
 * adapter position.
 */
object CommentSearch {

  /** Returns the indices of [comments] whose body text contains [query], case-insensitively. */
  fun match(comments: List<Item?>, query: String): List<Int> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    val lowered = needle.lowercase()
    val matches = ArrayList<Int>()
    for (i in comments.indices) {
      val body = comments[i]?.displayedText?.toString()
      if (body != null && body.lowercase().contains(lowered)) {
        matches.add(i)
      }
    }
    return matches
  }

  /**
   * Searches [comments] for [query] and returns a cursor parked on the first match, ready for
   * [CommentSearchCursor.next] / [CommentSearchCursor.prev] traversal. A blank query yields an
   * empty cursor.
   */
  fun search(comments: List<Item?>, query: String): CommentSearchCursor =
      CommentSearchCursor.of(match(comments, query))
}
