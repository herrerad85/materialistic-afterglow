/*
 * Copyright (c) 2015 Ha Duy Trung
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
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.growse.android.io.github.hidroh.materialistic.accounts.AccountActions
import com.growse.android.io.github.hidroh.materialistic.data.FavoriteManager
import com.growse.android.io.github.hidroh.materialistic.data.ItemManager
import com.growse.android.io.github.hidroh.materialistic.data.MaterialisticDatabase
import com.growse.android.io.github.hidroh.materialistic.data.ViewedItemStore
import com.growse.android.io.github.hidroh.materialistic.reply.ReplyNotificationScheduler
import com.growse.android.io.github.hidroh.materialistic.widget.PopupMenu
import com.growse.android.io.github.hidroh.materialistic.widget.StoryRecyclerViewAdapter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The story-list screen, the E1 Gate-2 canonical Coroutines + `@HiltViewModel` reference vertical.
 *
 * The async + state plumbing lives in [StoryListViewModel] (a single sealed [StoryListUiState] over
 * a `StateFlow`); this Fragment only collects that state lifecycle-safely and maps it to view
 * changes ([render]). Everything else , the adapter's new-stories Snackbar wiring, the favorites
 * LiveData observer, swipe-to-refresh, the preference observable , stays here unchanged.
 * Section→manager selection and the filter live in the VM (fed by the fragment arguments via
 * `SavedStateHandle`). See DECISIONS.md Q4/Q5 / ADR-0003.
 */
@AndroidEntryPoint
class ListFragment : BaseListFragment() {

  private val mPreferenceObservable = Preferences.Observable()
  private val mObserver =
      Observer<Uri?> { uri ->
        if (uri == null) return@Observer
        val toastMessageResId =
            when {
              FavoriteManager.isAdded(uri) -> R.string.toast_saved
              FavoriteManager.isRemoved(uri) -> R.string.toast_removed
              else -> 0
            }
        if (toastMessageResId == 0) return@Observer
        Snackbar.make(mRecyclerView, toastMessageResId, Snackbar.LENGTH_SHORT)
            .setAction(R.string.undo) { getAdapter().toggleSave(uri.lastPathSegment) }
            .show()
      }

  private var mAdapter: StoryRecyclerViewAdapter? = null
  private lateinit var mSwipeRefreshLayout: SwipeRefreshLayout
  @Inject @HackerNews lateinit var mHnItemManager: ItemManager
  @Inject @Algolia lateinit var mAlgoliaItemManager: ItemManager
  @Inject @Popular lateinit var mPopularItemManager: ItemManager
  @Inject lateinit var mPopupMenu: PopupMenu
  // Typed (AlertDialog) + @JvmSuppressWildcards so the injected request is a plain
  // AlertDialogBuilder<AlertDialog> (not AlertDialogBuilder<? extends AlertDialog>). The raw Java
  // @Inject sites use UiModule's raw binding; this Kotlin site uses the parameterized companion
  // binding (see UiModule.provideAlertDialogBuilderTyped) , Kotlin cannot express a raw type.
  @Inject
  @JvmSuppressWildcards
  lateinit var mAlertDialogBuilder: AlertDialogBuilder<androidx.appcompat.app.AlertDialog>
  @Inject lateinit var mAccountActions: AccountActions
  @Inject lateinit var mFavoriteManager: FavoriteManager
  @Inject lateinit var mViewedItemStore: ViewedItemStore
  @Inject lateinit var mOfflineStatusResolver: OfflineStatusResolver
  @Inject lateinit var mOfflineReadPolicy: OfflineReadPolicy
  @Inject lateinit var mReplyNotificationScheduler: ReplyNotificationScheduler
  private val mStoryListViewModel: StoryListViewModel by viewModels()
  private lateinit var mErrorView: View
  private lateinit var mEmptyView: View
  private var mRefreshCallback: RefreshCallback? = null
  private var mFilter: String? = null
  private var mCacheMode = ItemManager.MODE_DEFAULT
  private var mLastRenderedState: StoryListUiState? = null

  interface RefreshCallback {
    fun onRefreshed()
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    if (context is RefreshCallback) {
      mRefreshCallback = context
    }
    mPreferenceObservable.subscribe(
        context,
        { key, contextChanged -> onPreferenceChanged(key, contextChanged) },
        R.string.pref_highlight_updated,
        R.string.pref_username,
        R.string.pref_auto_viewed,
    )
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState != null) {
      mFilter = savedInstanceState.getString(STATE_FILTER)
      mCacheMode = savedInstanceState.getInt(STATE_CACHE_MODE)
    } else {
      mFilter = requireArguments().getString(EXTRA_FILTER)
    }
  }

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    val view = inflater.inflate(R.layout.fragment_list, container, false)
    mErrorView = view.findViewById(R.id.empty)
    mEmptyView = view.findViewById(R.id.empty_search)
    mRecyclerView = view.findViewById(R.id.recycler_view)
    mSwipeRefreshLayout = view.findViewById(R.id.swipe_layout)
    mSwipeRefreshLayout.setColorSchemeResources(R.color.white)
    mSwipeRefreshLayout.setProgressBackgroundColorSchemeColor(
        AppUtils.getThemedColor(requireActivity(), R.attr.colorAccent, Color.BLACK)
    )
    if (savedInstanceState == null) {
      mSwipeRefreshLayout.isRefreshing = true
    }
    mSwipeRefreshLayout.setOnRefreshListener {
      mCacheMode = ItemManager.MODE_NETWORK
      getAdapter().setCacheMode(mCacheMode)
      refresh()
    }
    return view
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    MaterialisticDatabase.getInstance(requireContext())
        .liveData
        .observe(viewLifecycleOwner, mObserver)
    val managerClassName = requireArguments().getString(EXTRA_ITEM_MANAGER)
    val itemManager =
        when (managerClassName) {
          com.growse.android.io.github.hidroh.materialistic.data.AlgoliaClient::class.java.name ->
              mAlgoliaItemManager
          com.growse.android.io.github.hidroh.materialistic.data.AlgoliaPopularClient::class
              .java
              .name -> mPopularItemManager
          else -> mHnItemManager
        }
    getAdapter().setHotThresHold(AppUtils.HOT_THRESHOLD_NORMAL)
    if (itemManager === mHnItemManager && mFilter != null) {
      when (mFilter) {
        ItemManager.BEST_FETCH_MODE -> getAdapter().setHotThresHold(AppUtils.HOT_THRESHOLD_HIGH)
        ItemManager.NEW_FETCH_MODE -> getAdapter().setHotThresHold(AppUtils.HOT_THRESHOLD_LOW)
      }
    } else if (itemManager === mPopularItemManager) {
      getAdapter().setHotThresHold(AppUtils.HOT_THRESHOLD_HIGH)
    }
    getAdapter().initDisplayOptions(mRecyclerView)
    getAdapter().setCacheMode(mCacheMode)
    // UpdateListener is a plain (non-fun) interface, so pass an explicit object (no SAM
    // conversion).
    getAdapter()
        .setUpdateListener(
            object : StoryRecyclerViewAdapter.UpdateListener {
              override fun onUpdated(
                  showAll: Boolean,
                  itemCount: Int,
                  actionClickListener: View.OnClickListener?,
              ) {
                if (showAll) {
                  Snackbar.make(
                          mRecyclerView,
                          resources.getQuantityString(
                              R.plurals.new_stories_count,
                              itemCount,
                              itemCount,
                          ),
                          Snackbar.LENGTH_LONG,
                      )
                      .setAction(R.string.show_me, actionClickListener)
                      .show()
                } else {
                  val snackbar =
                      Snackbar.make(
                          mRecyclerView,
                          resources.getQuantityString(
                              R.plurals.showing_new_stories,
                              itemCount,
                              itemCount,
                          ),
                          Snackbar.LENGTH_INDEFINITE,
                      )
                  snackbar.setAction(R.string.show_all, actionClickListener).show()
                }
              }
            }
        )
    // The VM owns section→manager selection + the load (it reads the same EXTRA_* arguments via
    // SavedStateHandle and auto-loads in init). The fragment only renders the resulting state.
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        mStoryListViewModel.uiState.collect { render(it) }
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putString(STATE_FILTER, mFilter)
    outState.putInt(STATE_CACHE_MODE, mCacheMode)
  }

  override fun onDetach() {
    mPreferenceObservable.unsubscribe(requireActivity())
    mRefreshCallback = null
    super.onDetach()
  }

  fun filter(filter: String?) {
    mFilter = filter
    getAdapter().setHighlightUpdated(false)
    mSwipeRefreshLayout.isRefreshing = true
    mStoryListViewModel.setFilter(filter)
  }

  // requireActivity(), not requireContext(): under @AndroidEntryPoint, requireContext() returns
  // Hilt's ViewComponentManager$FragmentContextWrapper, but the adapter casts its context to the
  // host MultiPaneListener Activity (ListRecyclerViewAdapter). requireActivity() is that Activity
  // (Fragment API guarantee), independent of Hilt's context wrapping.
  override fun getAdapter(): StoryRecyclerViewAdapter {
    return mAdapter
        ?: StoryRecyclerViewAdapter(
                requireActivity(),
                mPopupMenu,
                mAlertDialogBuilder,
                mAccountActions,
                mFavoriteManager,
                mHnItemManager,
                mViewedItemStore,
                mOfflineStatusResolver,
                mReplyNotificationScheduler,
            )
            .also { mAdapter = it }
  }

  private fun onPreferenceChanged(key: Int, contextChanged: Boolean) {
    if (!contextChanged) {
      getAdapter().initDisplayOptions(mRecyclerView)
    }
  }

  private fun refresh() {
    getAdapter().setShowAll(true)
    mStoryListViewModel.refresh()
  }

  /**
   * #25: copy for an empty list. When reading is cache-only (explicit Offline mode or no
   * connectivity) the empty result is an offline state, not a fetch failure, so it gets an
   * offline-specific message that distinguishes the two; an online empty/error keeps [default].
   */
  private fun listEmptyMessage(default: Int): Int {
    if (!mOfflineReadPolicy.shouldReadCacheOnly()) {
      return default
    }
    return when (mOfflineReadPolicy.emptyReason()) {
      OfflineEmptyReason.OFFLINE_MODE -> R.string.offline_empty_stories_offline
      else -> R.string.offline_empty_stories_no_connection
    }
  }

  /**
   * Maps the sealed [StoryListUiState] to the exact view changes the old `onItemsLoaded(Item[])`
   * performed. [StoryListUiState.Error] preserves the nuance: error view if the list is empty, else
   * just a `connection_error` toast (the existing items stay on screen).
   */
  private fun render(state: StoryListUiState) {
    if (!isAttached) {
      return
    }
    // Returning from an item (STOPPED then STARTED) makes repeatOnLifecycle re-collect the
    // StateFlow, which replays the same value instance. Re-applying an unchanged state would
    // clear and refill the adapter's SortedList, snapping the list to the top and losing the
    // user's place. Skipping the redundant re-render keeps the scroll position across an item
    // open and back. A genuine load (refresh, section, or filter switch) is a new instance
    // and still renders.
    if (state === mLastRenderedState) {
      return
    }
    mLastRenderedState = state
    when (state) {
      StoryListUiState.Loading -> mSwipeRefreshLayout.isRefreshing = true
      StoryListUiState.Error -> {
        mSwipeRefreshLayout.isRefreshing = false
        if (getAdapter().items.size() == 0) {
          // TODO make refreshing indicator visible in error view
          mEmptyView.visibility = View.GONE
          mRecyclerView.visibility = View.INVISIBLE
          // #25: a cache-only read (explicit Offline mode or no connectivity) that found nothing
          // shows an offline-specific message instead of the generic connection error.
          mErrorView
              .findViewById<TextView>(R.id.empty_error_text)
              .setText(listEmptyMessage(R.string.connection_error))
          mErrorView.visibility = View.VISIBLE
        } else {
          Toast.makeText(activity, getString(R.string.connection_error), Toast.LENGTH_SHORT).show()
        }
      }
      StoryListUiState.Empty -> {
        getAdapter().setItems(emptyList())
        mEmptyView
            .findViewById<TextView>(R.id.empty_search_text)
            .setText(listEmptyMessage(R.string.no_stories_search))
        mEmptyView.visibility = View.VISIBLE
        mRecyclerView.visibility = View.INVISIBLE
        mErrorView.visibility = View.GONE
        mSwipeRefreshLayout.isRefreshing = false
        mRefreshCallback?.onRefreshed()
      }
      is StoryListUiState.Content -> {
        getAdapter().setItems(state.items)
        mEmptyView.visibility = View.GONE
        mRecyclerView.visibility = View.VISIBLE
        mErrorView.visibility = View.GONE
        mSwipeRefreshLayout.isRefreshing = false
        mRefreshCallback?.onRefreshed()
      }
    }
  }

  companion object {
    @JvmField val EXTRA_ITEM_MANAGER: String = ListFragment::class.java.name + ".EXTRA_ITEM_MANAGER"
    @JvmField val EXTRA_FILTER: String = ListFragment::class.java.name + ".EXTRA_FILTER"
    private const val STATE_FILTER = "state:filter"
    private const val STATE_CACHE_MODE = "state:cacheMode"
  }
}
