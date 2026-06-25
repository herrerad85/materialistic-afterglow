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

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.growse.android.io.github.hidroh.materialistic.AppUtils
import com.growse.android.io.github.hidroh.materialistic.R
import com.growse.android.io.github.hidroh.materialistic.data.Item

/**
 * Host glue for the inline find-in-thread bar. Wires the bar's views (query field, counter,
 * previous / next / close buttons) to the pure [CommentSearch] matcher and the comment
 * [RecyclerView], so a host (the comments [androidx.fragment.app.Fragment]) only has to inflate the
 * bar, supply the loaded comment list, and route the toolbar action to [toggle].
 *
 * Find-in-page, not filtering: it never reorders or removes rows. It searches exactly the supplied
 * loaded list (loaded-only by construction, see the E4-D3 decision), maps a match index straight to
 * an adapter position (single-page mode renders the list one-to-one), scrolls there through the
 * caller-supplied [scrollToPosition] path (which honours the smooth-scroll preference), and tints
 * the active row with the M3 highlight role. Search state lives here, never on the comment adapter.
 *
 * @param barView the inflated find-bar root (initially gone).
 * @param recyclerView the comment list, used to resolve a row view by position for highlighting.
 * @param loadedItems supplies the current loaded comments in display order ; the index returned by
 *   the matcher is the adapter position. The list may end with a null footer element, which never
 *   matches.
 * @param scrollToPosition jumps the list to an adapter position, reusing the host scroll path.
 * @param onRowRebind asks the host to rebind a row to its resting state, used to clear a highlight
 *   without the controller having to know the row's other decorations.
 */
class CommentSearchController(
    private val barView: View,
    private val recyclerView: RecyclerView,
    private val loadedItems: LoadedComments,
    private val scrollToPosition: ScrollToPosition,
    private val onRowRebind: RowRebind,
) {

  /**
   * Supplies the loaded comments in display order. A Java-friendly SAM for the host to implement.
   */
  fun interface LoadedComments {
    fun get(): List<Item>
  }

  /** Jumps the comment list to an adapter position, reusing the host scroll path. */
  fun interface ScrollToPosition {
    fun scrollTo(position: Int)
  }

  /** Asks the host to rebind a row to its resting state, used when clearing a highlight. */
  fun interface RowRebind {
    fun rebind(position: Int)
  }

  private val context: Context = barView.context
  private val queryField: EditText = barView.findViewById(R.id.comment_find_query)
  private val countView: TextView = barView.findViewById(R.id.comment_find_count)
  private val highlightColor: Int = AppUtils.getThemedColor(context, R.attr.colorCardHighlight, 0)
  private val cardBackgroundColor: Int =
      AppUtils.getThemedColor(context, R.attr.colorCardBackground, 0)

  private var cursor: CommentSearchCursor = CommentSearchCursor.of(emptyList())
  /** Adapter position currently tinted as the active match, or -1 when nothing is highlighted. */
  private var highlightedPosition = -1
  /**
   * The comment currently navigated to, tracked by identity so the cursor can be re-anchored onto
   * the same comment after the loaded list shifts (collapse / expand / auto-expand) without losing
   * the user's place or scrolling.
   */
  private var activeMatchItem: Item? = null
  /** The single pending post-scroll highlight retry, kept as a field so they cannot accumulate. */
  private var pendingIdleListener: RecyclerView.OnScrollListener? = null
  /** The adapter the data observer is registered on while the bar shows, or null when not. */
  private var observedAdapter: RecyclerView.Adapter<*>? = null

  /**
   * Re-matches against the live list when it shifts under the open bar, so indices never go stale.
   */
  private val dataObserver =
      object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = onLoadedListChanged()

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = onLoadedListChanged()

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = onLoadedListChanged()

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) =
            onLoadedListChanged()
      }

  init {
    queryField.addTextChangedListener(
        object : TextWatcher {
          override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

          override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

          override fun afterTextChanged(s: Editable?) {
            runSearch(s?.toString().orEmpty())
          }
        }
    )
    queryField.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_SEARCH) {
        step(forward = true)
        true
      } else {
        false
      }
    }
    barView.findViewById<ImageButton>(R.id.comment_find_prev).setOnClickListener {
      step(forward = false)
    }
    barView.findViewById<ImageButton>(R.id.comment_find_next).setOnClickListener {
      step(forward = true)
    }
    barView.findViewById<ImageButton>(R.id.comment_find_close).setOnClickListener { hide() }
  }

  /** True when the bar is currently shown. */
  fun isShowing(): Boolean = barView.visibility == View.VISIBLE

  /** Toggles the bar : shows and focuses it when hidden, hides it when shown. */
  fun toggle() {
    if (isShowing()) {
      hide()
    } else {
      show()
    }
  }

  private fun show() {
    barView.visibility = View.VISIBLE
    registerDataObserver()
    queryField.requestFocus()
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.showSoftInput(queryField, InputMethodManager.SHOW_IMPLICIT)
    // Re-run over whatever is loaded now, in case the list grew since the bar last closed.
    runSearch(queryField.text?.toString().orEmpty())
  }

  /**
   * Hides the bar, clears the highlight, and drops the cursor. Safe to call when already hidden.
   */
  fun hide() {
    clearHighlight()
    cancelPendingIdle()
    unregisterDataObserver()
    activeMatchItem = null
    cursor = CommentSearchCursor.of(emptyList())
    countView.text = ""
    barView.visibility = View.GONE
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(queryField.windowToken, 0)
  }

  private fun runSearch(query: String) {
    clearHighlight()
    cancelPendingIdle()
    cursor = CommentSearch.search(loadedItems.get(), query)
    updateCount(query)
    if (cursor.total > 0) {
      moveToActiveMatch()
    } else {
      activeMatchItem = null
    }
  }

  private fun step(forward: Boolean) {
    if (cursor.total == 0) {
      return
    }
    clearHighlight()
    cursor = if (forward) cursor.next() else cursor.prev()
    updateCount(queryField.text?.toString().orEmpty())
    moveToActiveMatch()
  }

  private fun registerDataObserver() {
    if (observedAdapter != null) {
      return
    }
    val adapter = recyclerView.adapter ?: return
    adapter.registerAdapterDataObserver(dataObserver)
    observedAdapter = adapter
  }

  private fun unregisterDataObserver() {
    observedAdapter?.unregisterAdapterDataObserver(dataObserver)
    observedAdapter = null
  }

  /**
   * Re-runs the match after the loaded list shifts while the bar is open, re-anchoring the cursor
   * on the same comment by identity (so collapse / expand / auto-expand never send next/prev to the
   * wrong row) and refreshing the counter. It does NOT scroll , a structural change must not yank
   * the list out from under the reader , it only re-tints the active match if it is currently on
   * screen.
   */
  private fun onLoadedListChanged() {
    if (!isShowing()) {
      return
    }
    // The shift's own rebind cascade repaints the moved rows, clearing any old tint for us.
    highlightedPosition = -1
    val query = queryField.text?.toString().orEmpty()
    val items = loadedItems.get()
    val matches = if (query.isBlank()) emptyList() else CommentSearch.match(items, query)
    val activeIndex = activeMatchItem?.let { items.indexOf(it) } ?: -1
    cursor = CommentSearchCursor.at(matches, activeIndex)
    updateCount(query)
    val position = cursor.matchIndex
    activeMatchItem = if (position >= 0) items.getOrNull(position) else null
    if (position >= 0) {
      applyHighlight(position)
    }
  }

  private fun updateCount(query: String) {
    countView.text =
        when {
          query.isBlank() -> ""
          cursor.total == 0 -> context.getString(R.string.comment_search_no_matches)
          else -> context.getString(R.string.comment_search_count, cursor.current, cursor.total)
        }
  }

  private fun moveToActiveMatch() {
    val position = cursor.matchIndex
    if (position < 0) {
      activeMatchItem = null
      return
    }
    activeMatchItem = loadedItems.get().getOrNull(position)
    scrollToPosition.scrollTo(position)
    // The target row is usually not laid out yet: it may be off-screen, or a smooth scroll may
    // still be animating. Try now, then on the next frame (covers an instant scroll), then once the
    // list reaches idle (covers a smooth scroll still in flight). The idle retry is what tints the
    // FIRST match of a fresh search, which typically scrolls the farthest.
    if (!applyHighlight(position)) {
      recyclerView.post { applyHighlight(position) }
      highlightWhenIdle(position)
    }
  }

  /**
   * Reapplies the highlight to [position] once the list stops scrolling. Kept as a single field so
   * repeated far scrolls cannot stack listeners, self-removing, and guarded so it never tints a
   * stale row if the user stepped or closed the bar while the scroll settled.
   */
  private fun highlightWhenIdle(position: Int) {
    cancelPendingIdle()
    val listener =
        object : RecyclerView.OnScrollListener() {
          override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState != RecyclerView.SCROLL_STATE_IDLE) {
              return
            }
            recyclerView.removeOnScrollListener(this)
            if (pendingIdleListener === this) {
              pendingIdleListener = null
            }
            // Post so the tint lands AFTER the adapter's own idle unlockBinding rebind of the
            // scrolled range, which on an upward jump includes the match row and would otherwise
            // repaint over (erase) the tint.
            recyclerView.post {
              if (cursor.matchIndex == position && highlightedPosition != position) {
                applyHighlight(position)
              }
            }
          }
        }
    pendingIdleListener = listener
    recyclerView.addOnScrollListener(listener)
  }

  private fun cancelPendingIdle() {
    pendingIdleListener?.let { recyclerView.removeOnScrollListener(it) }
    pendingIdleListener = null
  }

  /** Tints the row at [position] with the highlight role. Returns false when it is not laid out. */
  private fun applyHighlight(position: Int): Boolean {
    val rowView = recyclerView.layoutManager?.findViewByPosition(position) ?: return false
    val content = rowView.findViewById<View>(R.id.content) ?: rowView
    content.setBackgroundColor(highlightColor)
    highlightedPosition = position
    return true
  }

  private fun clearHighlight() {
    val position = highlightedPosition
    if (position < 0) {
      return
    }
    highlightedPosition = -1
    val rowView = recyclerView.layoutManager?.findViewByPosition(position)
    if (rowView != null) {
      val content = rowView.findViewById<View>(R.id.content) ?: rowView
      content.setBackgroundColor(cardBackgroundColor)
    }
    // Let the adapter restore the row's true resting decoration (for example a user-name highlight)
    // on its next bind, so the find tint never sticks to a row that owns a different background.
    onRowRebind.rebind(position)
  }
}
