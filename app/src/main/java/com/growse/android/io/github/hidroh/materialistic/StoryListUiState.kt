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

import com.growse.android.io.github.hidroh.materialistic.data.Item

/**
 * The single sealed UI state for the story list (E1 Gate 2, the canonical reference vertical).
 *
 * State is **data only** , it carries no lambdas/callbacks. Behavior (refresh/retry) lives on
 * [StoryListViewModel] as functions; the Fragment maps each state to view changes. `Content`
 * carries only the current `items`: the new/promoted/"N new stories" diff is owned by
 * [com.growse.android.io.github.hidroh.materialistic.widget.StoryRecyclerViewAdapter] (vestigial,
 * untouched here , deferred to E4). See DECISIONS.md Q5 / ADR-0003.
 */
sealed interface StoryListUiState {
  /** A load is in flight (maps to the swipe-refresh spinner). */
  data object Loading : StoryListUiState

  /** A successful, non-empty load. */
  data class Content(val items: List<Item>) : StoryListUiState

  /** A successful load that returned zero stories (maps to the empty view). */
  data object Empty : StoryListUiState

  /** The load failed: `getStories` returned null, or the call threw. */
  data object Error : StoryListUiState
}
