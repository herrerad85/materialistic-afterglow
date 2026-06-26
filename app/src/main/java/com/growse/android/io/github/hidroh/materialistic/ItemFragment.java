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

package com.growse.android.io.github.hidroh.materialistic;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import dagger.hilt.android.AndroidEntryPoint;
import com.growse.android.io.github.hidroh.materialistic.annotation.Synthetic;
import com.growse.android.io.github.hidroh.materialistic.ai.SecretStore;
import com.growse.android.io.github.hidroh.materialistic.ai.SummaryBottomSheet;
import com.growse.android.io.github.hidroh.materialistic.data.Item;
import com.growse.android.io.github.hidroh.materialistic.data.ItemManager;
import com.growse.android.io.github.hidroh.materialistic.data.NewCommentMarker;
import com.growse.android.io.github.hidroh.materialistic.data.ResponseListener;
import com.growse.android.io.github.hidroh.materialistic.data.WebItem;
import com.growse.android.io.github.hidroh.materialistic.widget.CommentItemDecoration;
import com.growse.android.io.github.hidroh.materialistic.widget.CommentSearchController;
import com.growse.android.io.github.hidroh.materialistic.widget.ItemRecyclerViewAdapter;
import com.growse.android.io.github.hidroh.materialistic.widget.MultiPageItemRecyclerViewAdapter;
import com.growse.android.io.github.hidroh.materialistic.widget.SinglePageItemRecyclerViewAdapter;
import com.growse.android.io.github.hidroh.materialistic.widget.SnappyLinearLayoutManager;

import java.util.Collections;

@AndroidEntryPoint
public class ItemFragment extends LazyLoadFragment implements Scrollable, Navigable {

    public static final String EXTRA_ITEM = ItemFragment.class.getName() + ".EXTRA_ITEM";
    public static final String EXTRA_CACHE_MODE = ItemFragment.class.getName() + ".EXTRA_CACHE_MODE";
    private static final String STATE_ITEM = "state:item";
    private static final String STATE_ITEM_ID = "state:itemId";
    private static final String STATE_CACHE_MODE = "state:cacheMode";
    private RecyclerView mRecyclerView;
    private View mEmptyView;
    private Item mItem;
    private String mItemId;
    @Inject @HackerNews ItemManager mItemManager;
    @Inject com.growse.android.io.github.hidroh.materialistic.accounts.AccountActions mAccountActions;
    @Inject com.growse.android.io.github.hidroh.materialistic.widget.PopupMenu mPopupMenu;
    @Inject AlertDialogBuilder mAlertDialogBuilder;
    @Inject ResourcesProvider mResourcesProvider;
    @Inject SecretStore mSecretStore;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private SinglePageItemRecyclerViewAdapter.SavedState mAdapterItems;
    private ItemRecyclerViewAdapter mAdapter;
    private KeyDelegate.RecyclerViewHelper mScrollableHelper;
    private @ItemManager.CacheMode int mCacheMode = ItemManager.MODE_DEFAULT;
    private final Preferences.Observable mPreferenceObservable = new Preferences.Observable();
    private CommentItemDecoration mItemDecoration;
    private View mFragmentView;
    private CommentSearchController mCommentSearchController;
    // G6: marks comments that arrived since the last visit to this thread. The display baseline (the
    // last-visit max id) is read ONCE per thread open (reset on a fresh load / refresh) and held
    // fixed, then handed to each freshly built adapter; marking is per comment at bind time, so
    // replies that load after the initial list are marked too. The persisted baseline is advanced
    // separately (mSeenBaselineObserver) as more comments load, so later replies are not re-flagged
    // next visit.
    private final com.growse.android.io.github.hidroh.materialistic.data.CommentSeenTracker
            mCommentSeenTracker =
            new com.growse.android.io.github.hidroh.materialistic.data.CommentSeenTracker();
    private boolean mNewCommentBaselineLoaded;
    private Long mNewCommentBaseline;
    private final RecyclerView.AdapterDataObserver mSeenBaselineObserver =
            new RecyclerView.AdapterDataObserver() {
                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    // Replies loaded after the initial list: advance the persisted (not the display)
                    // baseline so they are not re-flagged next visit. Guarded on the display baseline
                    // having been read first, preserving read-before-write.
                    if (mNewCommentBaselineLoaded && !TextUtils.isEmpty(mItemId)) {
                        advanceSeenBaseline(mItemId);
                    }
                }
            };

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mPreferenceObservable.subscribe(context, this::onPreferenceChanged,
                R.string.pref_comment_display,
                R.string.pref_max_lines,
                R.string.pref_username,
                R.string.pref_line_height,
                R.string.pref_color_code,
                R.string.pref_thread_indicator,
                R.string.pref_font,
                R.string.pref_text_size,
                R.string.pref_smooth_scroll,
                R.string.pref_color_code_opacity);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (savedInstanceState != null) {
            mCacheMode = savedInstanceState.getInt(STATE_CACHE_MODE, ItemManager.MODE_DEFAULT);
            mItem = savedInstanceState.getParcelable(STATE_ITEM);
            mItemId = savedInstanceState.getString(STATE_ITEM_ID);
        } else {
            mCacheMode = getArguments().getInt(EXTRA_CACHE_MODE, ItemManager.MODE_DEFAULT);
            WebItem item = getArguments().getParcelable(EXTRA_ITEM);
            if (item instanceof Item) {
                mItem = (Item) item;
            }
            mItemId = item != null ? item.getId() : null;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable final Bundle savedInstanceState) {
        if (isNewInstance()) {
            mFragmentView = inflater.inflate(R.layout.fragment_item, container, false);
            mEmptyView = mFragmentView.findViewById(R.id.empty);
            mRecyclerView = (RecyclerView) mFragmentView.findViewById(R.id.recycler_view);
            mRecyclerView.setLayoutManager(new SnappyLinearLayoutManager(getActivity(), true));
            mItemDecoration = new CommentItemDecoration(getActivity());
            mRecyclerView.addItemDecoration(mItemDecoration);
            mSwipeRefreshLayout = (SwipeRefreshLayout) mFragmentView.findViewById(R.id.swipe_layout);
            mSwipeRefreshLayout.setColorSchemeResources(R.color.white);
            mSwipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.redA200);
            mSwipeRefreshLayout.setOnRefreshListener(() -> {
                if (TextUtils.isEmpty(mItemId)) {
                    return;
                }
                mCacheMode = ItemManager.MODE_NETWORK;
                if (mAdapter != null) {
                    mAdapter.setCacheMode(AppUtils.effectiveCacheMode(getActivity(), mCacheMode));
                }
                // A refresh re-reads the thread, so re-read the display baseline for the new visit.
                resetNewCommentBaseline();
                loadKidData();
            });
        }
        return mFragmentView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (isNewInstance()) {
            mScrollableHelper = new KeyDelegate.RecyclerViewHelper(mRecyclerView,
                    KeyDelegate.RecyclerViewHelper.SCROLL_ITEM);
            mScrollableHelper.smoothScrollEnabled(Preferences.smoothScrollEnabled(getActivity()));
            mCommentSearchController = new CommentSearchController(
                    mFragmentView.findViewById(R.id.comment_find_bar),
                    mRecyclerView,
                    this::getLoadedComments,
                    position -> {
                        if (mAdapter != null) {
                            mAdapter.lockBinding(mScrollableHelper.scrollToPosition(position));
                        }
                    },
                    position -> {
                        if (mAdapter != null) {
                            mAdapter.notifyItemChanged(position);
                        }
                    });
        }
    }

    @NonNull
    private java.util.List<Item> getLoadedComments() {
        // Loaded-only, single-page : the rendered list is the search scope and its index is the
        // adapter position. Other display modes have no flat loaded list to search, so return none.
        if (mAdapter instanceof SinglePageItemRecyclerViewAdapter) {
            return ((SinglePageItemRecyclerViewAdapter) mAdapter).getLoadedItems();
        }
        return Collections.emptyList();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_comments) {
            showPreferences();
            return true;
        }
        if (item.getItemId() == R.id.menu_find_in_thread) {
            if (mCommentSearchController != null) {
                mCommentSearchController.toggle();
            }
            return true;
        }
        if (item.getItemId() == R.id.menu_summarize_thread) {
            onSummarizeThread();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // The loaded comment tree (mAdapterItems) is deliberately NOT persisted: the whole flattened
        // tree, each comment with its full text, blew the Binder transaction limit
        // (TransactionTooLargeException) for large threads. On recreate LazyLoadFragment re-triggers
        // load(), and bindKidData() rebuilds the tree from the restored root item's kids; expanded row
        // state resets, which is preferred over an oversized parcel.
        outState.putParcelable(STATE_ITEM, mItem);
        outState.putString(STATE_ITEM_ID, mItemId);
        outState.putInt(STATE_CACHE_MODE, mCacheMode);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mAdapter != null) {
            mAdapter.detach(getActivity(), mRecyclerView);
        }
        mCommentSeenTracker.cancel();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mPreferenceObservable.unsubscribe(getActivity());
    }

    @Override
    public void scrollToTop() {
        mScrollableHelper.scrollToTop();
    }

    @Override
    public boolean scrollToNext() {
        return mScrollableHelper.scrollToNext();
    }

    @Override
    public boolean scrollToPrevious() {
        return mScrollableHelper.scrollToPrevious();
    }

    @Override
    public void onNavigate(int direction) {
        if (mAdapter == null) { // no kids
            return;
        }
        mAdapter.getNextPosition(mScrollableHelper.getCurrentPosition(),
                direction,
                position -> mAdapter.lockBinding(mScrollableHelper.scrollToPosition(position)));
    }

    @Override
    protected void load() {
        if (mItem != null) {
            bindKidData();
        } else if (!TextUtils.isEmpty(mItemId)) {
            loadKidData();
        }
    }

    @Override
    protected void createOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_item_view, menu);
    }

    @Override
    protected void prepareOptionsMenu(Menu menu) {
        // Gate the summarize action on the AI opt-in at prepare time (not just at create) so toggling
        // the setting while a thread is already open is reflected the next time the menu is prepared.
        // The key check stays on tap (a no-key tap prompts), so there is no Keystore decrypt here.
        MenuItem summarize = menu.findItem(R.id.menu_summarize_thread);
        if (summarize != null) {
            summarize.setVisible(Preferences.isAiSummariesEnabled(getActivity()));
        }
    }

    private void loadKidData() {
        // Explicit offline mode (or no connectivity) prefers cached item/comment data; a requested
        // MODE_NETWORK (swipe-refresh) is honored only when online and offline mode is off.
        mItemManager.getItem(mItemId, AppUtils.effectiveCacheMode(getActivity(), mCacheMode),
                new ItemResponseListener(this));
    }

    void onItemLoaded(@Nullable Item item) {
        mSwipeRefreshLayout.setRefreshing(false);
        if (item != null) {
            mAdapterItems = null;
            mItem = item;
            // A fresh thread load (initial or refresh): re-read the display baseline for the new
            // visit. Preference/display rebinds (which also call bindKidData) do NOT reset it, so the
            // baseline is read once and the markers stay stable across those rebinds.
            resetNewCommentBaseline();
            notifyItemLoaded(item);
            bindKidData();
        } else if (mItem == null) {
            // #25: the thread could not be read and nothing is loaded yet (an offline cache miss or a
            // fetch error). Explain it instead of leaving a blank pane. A failed refresh of an
            // already-loaded thread keeps its content (mItem != null), as before.
            showCommentsUnavailable();
        }
    }

    private void showCommentsUnavailable() {
        TextView text = mEmptyView.findViewById(R.id.empty_item_text);
        if (text != null) {
            text.setText(AppUtils.shouldReadCacheOnly(getActivity())
                    ? R.string.offline_empty_comments : R.string.connection_error);
        }
        mEmptyView.setVisibility(View.VISIBLE);
    }

    private void bindKidData() {
        if (mItem == null || mItem.getKidCount() == 0) {
            // A genuinely empty (but loaded) thread: keep the "no comments" copy, overriding any
            // not-available message a prior failed load may have set on the shared empty view (#25).
            TextView text = mEmptyView.findViewById(R.id.empty_item_text);
            if (text != null) {
                text.setText(R.string.no_comments);
            }
            mEmptyView.setVisibility(View.VISIBLE);
            return;
        }

        mEmptyView.setVisibility(View.GONE);
        String displayOption = Preferences.getCommentDisplayOption(getActivity());
        if (Preferences.isSinglePage(getActivity(), displayOption)) {
            boolean autoExpand = Preferences.isAutoExpand(getActivity(), displayOption);
            // if collapsed or no saved state then start a fresh (adapter items all collapsed)
            if (!autoExpand || mAdapterItems == null) {
                mAdapterItems = new SinglePageItemRecyclerViewAdapter.SavedState(
                        new ArrayList<>(Arrays.asList(mItem.getKidItems())));
            }
            mAdapter = new SinglePageItemRecyclerViewAdapter(mItemManager, mAccountActions, mPopupMenu,
                    mAlertDialogBuilder, mResourcesProvider, mAdapterItems, autoExpand);
        } else {
            mAdapter = new MultiPageItemRecyclerViewAdapter(mItemManager, mAccountActions, mPopupMenu,
                    mAlertDialogBuilder, mItem.getKidItems());
        }
        mAdapter.setCacheMode(AppUtils.effectiveCacheMode(getActivity(), mCacheMode));
        mAdapter.initDisplayOptions(getActivity());
        mAdapter.attach(getActivity(), mRecyclerView);
        mRecyclerView.setAdapter(mAdapter);
        // A full list/adapter rebuild (refresh, display-mode switch) invalidates any open find
        // session: its cursor indexes the discarded list and its observer is on the old adapter.
        // Close the bar so the user re-opens find against the fresh list.
        if (mCommentSearchController != null && mCommentSearchController.isShowing()) {
            mCommentSearchController.hide();
        }
        markNewComments();
    }

    private void resetNewCommentBaseline() {
        mNewCommentBaselineLoaded = false;
        mNewCommentBaseline = null;
    }

    // G6: hand the freshly built single-page adapter the last-visit baseline so it can mark new
    // comments (including replies that load later) at bind time. The display baseline is read once
    // per thread open and reused across preference/display rebinds; the persisted baseline is
    // advanced separately, after the read, and again as more comments load (mSeenBaselineObserver).
    private void markNewComments() {
        if (TextUtils.isEmpty(mItemId)
                || !(mAdapter instanceof SinglePageItemRecyclerViewAdapter)) {
            return;
        }
        // A rebind builds a new adapter, so re-register the observer and re-apply the baseline.
        mAdapter.registerAdapterDataObserver(mSeenBaselineObserver);
        if (mNewCommentBaselineLoaded) {
            mAdapter.setNewCommentBaseline(mNewCommentBaseline);
            return;
        }
        final String storyId = mItemId;
        mCommentSeenTracker.loadBaseline(getActivity().getApplicationContext(), storyId, baseline -> {
            if (!isAttached() || !TextUtils.equals(storyId, mItemId)
                    || !(mAdapter instanceof SinglePageItemRecyclerViewAdapter)) {
                return;
            }
            mNewCommentBaseline = baseline;
            mNewCommentBaselineLoaded = true;
            mAdapter.setNewCommentBaseline(baseline);
            // Initial advance over the loaded list; ordered after the read above (read-before-write).
            advanceSeenBaseline(storyId);
        });
    }

    // G6: persist the highest loaded comment id as the new baseline (monotonic, off the main
    // thread). Called once the display baseline has been read and again as replies load, so the
    // persisted value covers everything seen this visit without touching the display baseline.
    private void advanceSeenBaseline(String storyId) {
        // isAttached() guards getActivity() below: a late observer insert during teardown would
        // otherwise NPE on getActivity().getApplicationContext().
        if (!isAttached() || !(mAdapter instanceof SinglePageItemRecyclerViewAdapter)) {
            return;
        }
        java.util.List<Long> commentIds = new ArrayList<>();
        for (Item loaded : ((SinglePageItemRecyclerViewAdapter) mAdapter).getLoadedItems()) {
            if (loaded != null) { // the loaded list ends with a null footer
                commentIds.add(loaded.getLongId());
            }
        }
        Long max = NewCommentMarker.INSTANCE.maxId(commentIds);
        if (max != null) {
            mCommentSeenTracker.advanceBaseline(getActivity().getApplicationContext(), storyId, max);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void onPreferenceChanged(int key, boolean contextChanged) {
        if (contextChanged || key == R.string.pref_comment_display) {
            load();
        } else if (mAdapter != null) {
            mScrollableHelper.smoothScrollEnabled(Preferences.smoothScrollEnabled(getActivity()));
            mItemDecoration.setColorCodeEnabled(Preferences.colorCodeEnabled(getActivity()));
            mItemDecoration.setThreadIndicatorEnabled(Preferences.threadIndicatorEnabled(getActivity()));
            mAdapter.initDisplayOptions(getActivity());
            mAdapter.notifyDataSetChanged();
        }
    }

    private void onSummarizeThread() {
        if (TextUtils.isEmpty(mItemId)) {
            return;
        }
        // Opt-in is on (the action is hidden otherwise) but the key may be missing: prompt rather
        // than fail silently. hasApiKey() decrypts, but only on this user-initiated tap.
        if (!mSecretStore.hasApiKey()) {
            Toast.makeText(getActivity(), R.string.ai_key_required, Toast.LENGTH_LONG).show();
            return;
        }
        SummaryBottomSheet.newInstance(mItemId)
                .show(getChildFragmentManager(), SummaryBottomSheet.class.getName());
    }

    private void showPreferences() {
        Bundle args = new Bundle();
        args.putInt(PopupSettingsFragment.EXTRA_TITLE, R.string.font_options);
        args.putInt(PopupSettingsFragment.EXTRA_SUMMARY, R.string.pull_up_hint);
        args.putIntArray(PopupSettingsFragment.EXTRA_XML_PREFERENCES, new int[]{
                R.xml.preferences_font,
                R.xml.preferences_comments});
        ((DialogFragment) Fragment.instantiate(getActivity(),
                PopupSettingsFragment.class.getName(), args))
                .show(getFragmentManager(), PopupSettingsFragment.class.getName());
    }

    private void notifyItemLoaded(@NonNull Item item) {
        if (getActivity() instanceof ItemChangedListener) {
            ((ItemChangedListener) getActivity()).onItemChanged(item);
        }
    }

    static class ItemResponseListener implements ResponseListener<Item> {
        private WeakReference<ItemFragment> mItemFragment;

        @Synthetic
        ItemResponseListener(ItemFragment itemFragment) {
            mItemFragment = new WeakReference<>(itemFragment);
        }

        @Override
        public void onResponse(@Nullable Item response) {
            if (mItemFragment.get() != null && mItemFragment.get().isAttached()) {
                mItemFragment.get().onItemLoaded(response);
            }
        }

        @Override
        public void onError(String errorMessage) {
            if (mItemFragment.get() != null && mItemFragment.get().isAttached()) {
                mItemFragment.get().onItemLoaded(null);
            }
        }
    }

    interface ItemChangedListener {
        void onItemChanged(@NonNull Item item);
    }
}
