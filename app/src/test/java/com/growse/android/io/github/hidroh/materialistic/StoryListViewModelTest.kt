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
package com.growse.android.io.github.hidroh.materialistic

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.growse.android.io.github.hidroh.materialistic.data.Item
import com.growse.android.io.github.hidroh.materialistic.data.ItemManager
import com.growse.android.io.github.hidroh.materialistic.data.ResponseListener
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Deterministic unit tests for the E1 Gate-2 canonical [StoryListViewModel] (the reference others
 * copy). A [FakeItemManager] + a [StandardTestDispatcher] injected as the `@IoDispatcher` make
 * every state transition , including Error , reproducible without a device or network. The same
 * dispatcher backs `Dispatchers.Main` (which `viewModelScope` uses) so `advanceUntilIdle()` drains
 * the `init`-triggered load.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StoryListViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var context: Context

  @Before
  fun setUp() {
    // viewModelScope dispatches on Dispatchers.Main; route it to the controllable test scheduler.
    Dispatchers.setMain(testDispatcher)
    // The VM now reads offline state (AppUtils.effectiveCacheMode) before fetching, so it needs a
    // real Context; the default state (offline off, connected) leaves the requested cacheMode
    // as-is.
    context = ApplicationProvider.getApplicationContext()
    Preferences.reset(context)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** A controllable [ItemManager]: the blocking getStories returns a queued result or throws. */
  private class FakeItemManager : ItemManager {
    /** Each load pops the next behavior; the last one repeats once the queue drains. */
    var behaviors: ArrayDeque<() -> Array<Item>?> = ArrayDeque()
    var callCount = 0

    override fun getStories(filter: String?, cacheMode: Int): Array<Item>? {
      callCount++
      val next = if (behaviors.size > 1) behaviors.removeFirst() else behaviors.first()
      return next()
    }

    // Unused by the VM (it only calls the blocking overload).
    override fun getStories(
        filter: String?,
        cacheMode: Int,
        listener: ResponseListener<Array<Item>>?,
    ) = throw UnsupportedOperationException()

    override fun getItem(itemId: String?, cacheMode: Int): Item =
        throw UnsupportedOperationException()

    override fun getItem(itemId: String?, cacheMode: Int, listener: ResponseListener<Item>?) =
        throw UnsupportedOperationException()
  }

  private fun viewModel(fake: ItemManager): StoryListViewModel {
    // The HN manager is the default selection (no EXTRA_ITEM_MANAGER set). The other two are
    // distinct dummies so the wrong one being picked would be observable.
    return StoryListViewModel(
        hn = fake,
        algolia = mockk(relaxed = true),
        popular = mockk(relaxed = true),
        io = testDispatcher,
        context = context,
        savedStateHandle = SavedStateHandle(),
    )
  }

  private fun item(): Item = mockk(relaxed = true)

  @Test
  fun `load emits Content on non-empty result`() = runTest {
    val fake = FakeItemManager().apply { behaviors.addLast { arrayOf(item(), item()) } }
    val vm = viewModel(fake)

    advanceUntilIdle()

    val state = vm.uiState.value
    assertTrue("expected Content, was $state", state is StoryListUiState.Content)
    assertEquals(2, (state as StoryListUiState.Content).items.size)
  }

  @Test
  fun `load emits Empty on empty result`() = runTest {
    val fake = FakeItemManager().apply { behaviors.addLast { emptyArray() } }
    val vm = viewModel(fake)

    advanceUntilIdle()

    assertEquals(StoryListUiState.Empty, vm.uiState.value)
  }

  @Test
  fun `load emits Error on null result`() = runTest {
    val fake = FakeItemManager().apply { behaviors.addLast { null } }
    val vm = viewModel(fake)

    advanceUntilIdle()

    assertEquals(StoryListUiState.Error, vm.uiState.value)
  }

  @Test
  fun `load emits Error when getStories throws`() = runTest {
    val fake =
        FakeItemManager().apply { behaviors.addLast { throw RuntimeException("network down") } }
    val vm = viewModel(fake)

    advanceUntilIdle()

    assertEquals(StoryListUiState.Error, vm.uiState.value)
  }

  @Test
  fun `refresh re-loads (Error then Content)`() = runTest {
    val fake =
        FakeItemManager().apply {
          behaviors.addLast { null } // first load (init) -> Error
          behaviors.addLast { arrayOf(item()) } // refresh -> Content
        }
    val vm = viewModel(fake)
    advanceUntilIdle()
    assertEquals(StoryListUiState.Error, vm.uiState.value)

    vm.refresh()
    advanceUntilIdle()

    assertTrue(vm.uiState.value is StoryListUiState.Content)
    assertEquals(2, fake.callCount) // proves a second load actually ran
  }

  @Test
  fun `retry re-loads (Error then Content)`() = runTest {
    val fake =
        FakeItemManager().apply {
          behaviors.addLast { null } // first load (init) -> Error
          behaviors.addLast { arrayOf(item()) } // retry -> Content
        }
    val vm = viewModel(fake)
    advanceUntilIdle()
    assertEquals(StoryListUiState.Error, vm.uiState.value)

    vm.retry()
    advanceUntilIdle()

    assertTrue(vm.uiState.value is StoryListUiState.Content)
    assertEquals(2, fake.callCount)
  }
}
