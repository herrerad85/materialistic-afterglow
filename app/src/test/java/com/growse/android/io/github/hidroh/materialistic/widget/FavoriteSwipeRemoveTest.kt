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

import android.app.Activity
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.growse.android.io.github.hidroh.materialistic.AlertDialogBuilder
import com.growse.android.io.github.hidroh.materialistic.MultiPaneListener
import com.growse.android.io.github.hidroh.materialistic.R
import com.growse.android.io.github.hidroh.materialistic.accounts.AccountActions
import com.growse.android.io.github.hidroh.materialistic.data.Favorite
import com.growse.android.io.github.hidroh.materialistic.data.FavoriteManager
import com.growse.android.io.github.hidroh.materialistic.data.SyncScheduler
import com.growse.android.io.github.hidroh.materialistic.data.WebItem
import com.growse.android.io.github.hidroh.materialistic.reply.ReplyNotificationScheduler
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Proves the single-story swipe-to-remove path in [FavoriteRecyclerViewAdapter] against the de-rx'd
 * [FavoriteManager]. A left swipe lands on [FavoriteRecyclerViewAdapter.dismiss], which must stage
 * only the swiped row, call [FavoriteManager.remove] with exactly that one id (the neighbour
 * stays), drive the pending-removal branch of [FavoriteRecyclerViewAdapter.notifyChanged] (one
 * `notifyItemRemoved`), and re-add the row via [FavoriteManager.add] when Undo is tapped.
 *
 * The adapter itself is unchanged by G4d; this closes the coverage gap the per-item swipe gesture
 * could not reach under emulator automation (the [ItemTouchHelper] fling never registers).
 * [Snackbar] is statically stubbed so the Undo listener is captured without a real view hierarchy.
 */
@RunWith(RobolectricTestRunner::class)
class FavoriteSwipeRemoveTest {

  /** Host the adapter casts to [MultiPaneListener]; resources come from the real activity. */
  class HostActivity : Activity(), MultiPaneListener {
    override fun onItemSelected(item: WebItem?) {}

    override fun getSelectedItem(): WebItem? = null

    override fun isMultiPane(): Boolean = false
  }

  private lateinit var host: HostActivity
  private val favoriteManager = mockk<FavoriteManager>(relaxed = true)
  private val favA = mockk<Favorite>(relaxed = true) { every { id } returns "A" }
  private val favB = mockk<Favorite>(relaxed = true) { every { id } returns "B" }
  private val undoSlot = slot<View.OnClickListener>()
  private val removeSlot = slot<Collection<String>>()
  private val removed = mutableListOf<Int>()
  private lateinit var adapter: FavoriteRecyclerViewAdapter

  @Before
  fun setUp() {
    host = Robolectric.buildActivity(HostActivity::class.java).create().get()
    host.setTheme(R.style.AppTheme)

    // Two saved stories; swiping position 0 must remove only "A".
    every { favoriteManager.getSize() } returns 2
    every { favoriteManager.getItem(0) } returns favA
    every { favoriteManager.getItem(1) } returns favB

    // Capture the Undo action without a real view hierarchy / looper.
    mockkStatic(Snackbar::class)
    val snackbar = mockk<Snackbar>(relaxed = true)
    every { Snackbar.make(any<View>(), any<Int>(), any<Int>()) } returns snackbar
    every { snackbar.setAction(any<Int>(), capture(undoSlot)) } returns snackbar

    adapter =
        FavoriteRecyclerViewAdapter(
            host,
            mockk<PopupMenu>(relaxed = true),
            mockk<AlertDialogBuilder<*>>(relaxed = true),
            mockk<AccountActions>(relaxed = true),
            favoriteManager,
            mockk<SyncScheduler>(relaxed = true),
            mockk<ReplyNotificationScheduler>(relaxed = true),
            mockk<FavoriteRecyclerViewAdapter.ActionModeDelegate>(relaxed = true),
        )
    adapter.registerAdapterDataObserver(
        object : RecyclerView.AdapterDataObserver() {
          override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            removed.add(positionStart)
          }
        }
    )
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun leftSwipeRemovesOnlyTheSwipedStoryAndUndoRestoresIt() {
    adapter.dismiss(mockk<View>(relaxed = true), 0)

    // Exactly the swiped id is staged and removed; the neighbour "B" is never passed to remove.
    verify { favoriteManager.remove(host, capture(removeSlot)) }
    assertEquals(listOf("A"), removeSlot.captured.toList())

    // notifyChanged takes the pending-removal branch: one notifyItemRemoved for the swiped row.
    every { favoriteManager.getSize() } returns 1
    adapter.notifyChanged()
    assertEquals(listOf(0), removed)

    // Undo re-adds the removed story.
    undoSlot.captured.onClick(mockk<View>(relaxed = true))
    verify { favoriteManager.add(host, favA) }
  }
}
