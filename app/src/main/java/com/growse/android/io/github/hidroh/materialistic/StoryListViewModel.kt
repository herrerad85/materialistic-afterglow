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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.growse.android.io.github.hidroh.materialistic.data.AlgoliaClient
import com.growse.android.io.github.hidroh.materialistic.data.AlgoliaPopularClient
import com.growse.android.io.github.hidroh.materialistic.data.ItemManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The canonical E1 Gate-2 reference ViewModel (the pattern every later slice copies). Coroutines +
 * `@HiltViewModel` + a single sealed [StoryListUiState] over a [StateFlow].
 *
 * The section→source mapping stays **explicit** (DECISIONS.md Q4): a small `when` over the manager
 * class name selects the qualified [ItemManager], mirroring the old `ListFragment` selection. The
 * VM runs the existing blocking `@WorkerThread` [ItemManager.getStories] on the injected
 * [IoDispatcher] , the client internals stay Rx for now (converted when a later slice opens them).
 * No repository layer; no manual `ViewModelProvider.Factory`. See ADR-0003.
 *
 * [SavedStateHandle] receives a `by viewModels()` fragment VM's arguments as defaults, so the
 * `EXTRA_*` keys the fragment puts in its arguments resolve here.
 */
@HiltViewModel
class StoryListViewModel
@Inject
constructor(
    @HackerNews hn: ItemManager,
    @Algolia algolia: ItemManager,
    @Popular popular: ItemManager,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

  // Section→source mapping (mirrors the old ListFragment.onActivityCreated selection): the arg is a
  // manager CLASS NAME. Resolve the manager once.
  private val itemManager: ItemManager =
      when (savedStateHandle.get<String>(ListFragment.EXTRA_ITEM_MANAGER)) {
        AlgoliaClient::class.java.name -> algolia
        AlgoliaPopularClient::class.java.name -> popular
        else -> hn
      }

  private var filter: String? = savedStateHandle[ListFragment.EXTRA_FILTER]
  private var cacheMode: Int = ItemManager.MODE_DEFAULT

  private val _uiState = MutableStateFlow<StoryListUiState>(StoryListUiState.Loading)
  val uiState: StateFlow<StoryListUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  /** Swipe-to-refresh: force a network read, then reload. */
  fun refresh() {
    cacheMode = ItemManager.MODE_NETWORK
    load()
  }

  /** Error-view retry: reload with the current cache mode. */
  fun retry() {
    load()
  }

  /** Section/filter switch (mirrors the old `ListFragment.filter(String)`). */
  fun setFilter(filter: String?) {
    this.filter = filter
    load()
  }

  private fun load() {
    viewModelScope.launch {
      _uiState.value = StoryListUiState.Loading
      // Explicit offline mode (or no connectivity) prefers cached stories; the requested cacheMode
      // (e.g. MODE_NETWORK from swipe-refresh) is honored when online and offline mode is off.
      val effectiveCacheMode = AppUtils.effectiveCacheMode(context, cacheMode)
      _uiState.value =
          runCatching { withContext(io) { itemManager.getStories(filter, effectiveCacheMode) } }
              .fold(
                  onSuccess = { items ->
                    when {
                      // getStories returns null on error (the Fragment keeps the
                      // "error view if list empty, else connection_error toast" nuance).
                      items == null -> StoryListUiState.Error
                      items.isEmpty() -> StoryListUiState.Empty
                      else -> StoryListUiState.Content(items.toList())
                    }
                  },
                  onFailure = { StoryListUiState.Error },
              )
    }
  }
}
