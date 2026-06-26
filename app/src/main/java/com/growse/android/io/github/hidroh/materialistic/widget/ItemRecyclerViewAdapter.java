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

package com.growse.android.io.github.hidroh.materialistic.widget;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import android.annotation.SuppressLint;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import com.growse.android.io.github.hidroh.materialistic.AlertDialogBuilder;
import com.growse.android.io.github.hidroh.materialistic.AppUtils;
import com.growse.android.io.github.hidroh.materialistic.ComposeActivity;
import com.growse.android.io.github.hidroh.materialistic.Navigable;
import com.growse.android.io.github.hidroh.materialistic.Preferences;
import com.growse.android.io.github.hidroh.materialistic.R;
import com.growse.android.io.github.hidroh.materialistic.accounts.AccountActions;
import com.growse.android.io.github.hidroh.materialistic.accounts.UserServices;
import com.growse.android.io.github.hidroh.materialistic.annotation.Synthetic;
import com.growse.android.io.github.hidroh.materialistic.data.Item;
import com.growse.android.io.github.hidroh.materialistic.data.ItemManager;
import com.growse.android.io.github.hidroh.materialistic.data.NewCommentMarker;
import com.growse.android.io.github.hidroh.materialistic.data.ResponseListener;

public abstract class ItemRecyclerViewAdapter<VH extends ItemRecyclerViewAdapter.ItemViewHolder>
        extends RecyclerViewAdapter<VH> {
    private static final String PROPERTY_MAX_LINES = "maxLines";
    private static final int DURATION_PER_LINE_MILLIS = 20;
    LayoutInflater mLayoutInflater;
    private ItemManager mItemManager;
    final AccountActions mAccountActions;
    final PopupMenu mPopupMenu;
    final AlertDialogBuilder mAlertDialogBuilder;
    private int mTertiaryTextColor;
    private int mSecondaryTextColor;
    private int mCardBackgroundColor;
    private int mCardHighlightColor;
    private int mContentMaxLines = Integer.MAX_VALUE;
    private String mUsername;
    private final Map<String, Integer> mLineCounted = new HashMap<>();
    private int mCacheMode = ItemManager.MODE_DEFAULT;
    private float mLineHeight = 1.0f;
    // G6: the last-visit watermark (highest comment id seen on the previous visit). null (the
    // default and first-visit case) marks nothing new; each comment is judged against this fixed
    // value at bind time, so a reply loaded after the initial list is marked just like the rest.
    private Long mNewCommentBaseline = null;

    public interface PositionCallback {
        void onPosition(int position);
    }

    ItemRecyclerViewAdapter(ItemManager itemManager,
                            AccountActions accountActions,
                            PopupMenu popupMenu,
                            AlertDialogBuilder alertDialogBuilder) {
        mItemManager = itemManager;
        mAccountActions = accountActions;
        mPopupMenu = popupMenu;
        mAlertDialogBuilder = alertDialogBuilder;
    }

    @Override
    public void attach(Context context, RecyclerView recyclerView) {
        super.attach(context, recyclerView);
        mLayoutInflater = AppUtils.createLayoutInflater(context);
        TypedArray ta = context.obtainStyledAttributes(new int[]{
                android.R.attr.textColorTertiary,
                android.R.attr.textColorSecondary,
                R.attr.colorCardBackground,
                R.attr.colorCardHighlight
        });
        // Read each colour attr as a direct @ColorInt: correct whether the attr resolves to a
        // @color reference or a direct colour int (the latter under dynamic colour / M3 roles).
        mTertiaryTextColor = ta.getColor(0, Color.BLACK);
        mSecondaryTextColor = ta.getColor(1, Color.BLACK);
        mCardBackgroundColor = ta.getColor(2, Color.TRANSPARENT);
        mCardHighlightColor = ta.getColor(3, Color.TRANSPARENT);
        ta.recycle();
    }

    @Override
    public void onBindViewHolder(final VH holder, int position) {
        final Item item = getItem(position);
        if (item == null) {
            return;
        }
        clear(holder);
        if (item.getLocalRevision() < 0) {
            load(holder.getAdapterPosition(), item);
        } else if (item.getLocalRevision() > 0) {
            bind(holder, item);
        }
    }

    @Override
    public long getItemId(int position) {
        Item item = getItem(position);
        return item != null ? item.getLongId() : RecyclerView.NO_ID;
    }

    public void setCacheMode(int cacheMode) {
        mCacheMode = cacheMode;
    }

    /**
     * G6: the last-visit baseline (the highest comment id seen previously; null = first visit).
     * Marking is decided per comment at bind time (id &gt; baseline), so the same fixed baseline
     * marks later-loaded replies too. Triggers a rebind so currently-bound rows pick up (or drop)
     * the accent stripe.
     */
    @SuppressLint("NotifyDataSetChanged")
    public void setNewCommentBaseline(Long baseline) {
        mNewCommentBaseline = baseline;
        notifyDataSetChanged();
    }

    public void initDisplayOptions(Context context) {
        mContentMaxLines = Preferences.getCommentMaxLines(context);
        mUsername = Preferences.getUsername(context);
        mLineHeight = Preferences.getLineHeight(context);
    }

    public void getNextPosition(int position, int direction, PositionCallback callback) {
        switch (direction) {
            case Navigable.DIRECTION_UP:
                callback.onPosition(position - 1);
                break;
            case Navigable.DIRECTION_DOWN:
                callback.onPosition(position + 1);
                break;
        }
    }

    public void lockBinding(int[] lock) { }

    @Nullable
    protected abstract Item getItem(int position);

    @CallSuper
    protected void bind(final VH holder, final Item item) {
        if (item == null) {
            return;
        }
        highlightUserItem(holder, item);
        decorateDead(holder, item);
        markNewComment(holder, item);
        holder.mContentTextView.setLineSpacing(0f, mLineHeight);
        AppUtils.setTextWithLinks(holder.mContentTextView, item.getDisplayedText());
        Integer lineCount = mLineCounted.get(item.getId());
        if (lineCount != null && lineCount > 0) {
            toggleCollapsibleContent(holder, item, lineCount);
        } else {
            holder.mContentTextView.post(() -> {
                if (context == null) {
                    return;
                }
                int count = holder.mContentTextView.getLineCount();
                mLineCounted.put(item.getId(), count);
                toggleCollapsibleContent(holder, item, count);
            });
        }
        bindActions(holder, item);
    }

    protected void clear(VH holder) {
        holder.mCommentButton.setVisibility(View.GONE);
        holder.mPostedTextView.setOnClickListener(null);
        holder.mPostedTextView.setText(R.string.loading_text);
        holder.mContentTextView.setText(R.string.loading_text);
        holder.mReadMoreTextView.setVisibility(View.GONE);
    }

    @Synthetic
    boolean isAttached() {
        return context != null;
    }

    private void load(int adapterPosition, Item item) {
        item.setLocalRevision(0);
        mItemManager.getItem(item.getId(), mCacheMode,
                new ItemResponseListener(this, adapterPosition, item));
    }

    protected void onItemLoaded(int position, Item item) {
        if (position < getItemCount()) {
            notifyItemChanged(position);
        }
    }

    private void highlightUserItem(VH holder, Item item) {
        boolean highlight = !TextUtils.isEmpty(mUsername) &&
                TextUtils.equals(mUsername, item.getBy());
        holder.mContentView.setBackgroundColor(highlight ?
                mCardHighlightColor : mCardBackgroundColor);
    }

    private void decorateDead(VH holder, Item item) {
        holder.mContentTextView.setTextColor(item.isDead() ?
                mSecondaryTextColor : mTertiaryTextColor);
    }

    // G6: show the accent stripe iff this comment's id is past the last-visit baseline. Decided
    // every bind so a recycled row never keeps a stale marker, and so a reply loaded after the
    // initial list is marked the moment it binds.
    private void markNewComment(VH holder, Item item) {
        if (holder.mNewIndicator == null) {
            return;
        }
        holder.mNewIndicator.setVisibility(
                NewCommentMarker.INSTANCE.isNew(item.getLongId(), mNewCommentBaseline)
                        ? View.VISIBLE : View.GONE);
    }

    private void toggleCollapsibleContent(final VH holder, final Item item, int lineCount) {
        if (item.isContentExpanded() || lineCount <= mContentMaxLines) {
            holder.mContentTextView.setMaxLines(Integer.MAX_VALUE);
            holder.mReadMoreTextView.setVisibility(View.GONE);
            return;
        }
        holder.mContentTextView.setMaxLines(mContentMaxLines);
        holder.mReadMoreTextView.setVisibility(View.VISIBLE);
        holder.mReadMoreTextView.setText(context.getString(R.string.read_more, lineCount));
        holder.mReadMoreTextView.setOnClickListener(v -> {
            item.setContentExpanded(true);
            v.setVisibility(View.GONE);
            ObjectAnimator.ofInt(holder.mContentTextView, PROPERTY_MAX_LINES, lineCount)
                    .setDuration((lineCount - mContentMaxLines) * DURATION_PER_LINE_MILLIS)
                    .start();
        });
    }

    private void bindActions(final VH holder, final Item item) {
        if (item.isDead() || item.isDeleted()) {
            holder.mMoreButton.setVisibility(View.INVISIBLE);
            return;
        }
        holder.mMoreButton.setVisibility(View.VISIBLE);
        holder.mMoreButton.setOnClickListener(v ->
            mPopupMenu.create(context, holder.mMoreButton, Gravity.NO_GRAVITY)
                .inflate(R.menu.menu_contextual_comment)
                .setOnMenuItemClickListener(menuItem -> {
                    if (menuItem.getItemId() == R.id.menu_contextual_vote) {
                        vote(item);
                        return true;
                    }
                    if (menuItem.getItemId() == R.id.menu_contextual_comment) {
                        context.startActivity(new Intent(context, ComposeActivity.class)
                                .putExtra(ComposeActivity.EXTRA_PARENT_ID, item.getId())
                                .putExtra(ComposeActivity.EXTRA_PARENT_TEXT, item.getText()));
                        return true;
                    }
                    if (menuItem.getItemId() == R.id.menu_contextual_share) {
                        AppUtils.share(context,
                                item.isStoryType() ? item.getDisplayedTitle() : null,
                                item.isStoryType() ? item.getUrl() :
                                        item.getDisplayedText() == null ?
                                                null : item.getDisplayedText().toString());
                        return true;
                    }
                    return false;
                })
                .show());
    }

    private void vote(final Item item) {
        if (mAccountActions.vote(item.getId(), new VoteCallback(this)) == AccountActions.Result.NeedsLogin) {
            AppUtils.showLogin(context, mAlertDialogBuilder, mAccountActions.getSession());
        } else {
            Toast.makeText(context, R.string.sending, Toast.LENGTH_SHORT).show();
        }
    }

    @Synthetic
    void onVoted(Boolean successful) {
        if (successful == null) {
            Toast.makeText(context, R.string.vote_failed, Toast.LENGTH_SHORT).show();
        } else if (successful) {
            Toast.makeText(context, R.string.voted, Toast.LENGTH_SHORT).show();
        } else {
            AppUtils.showLogin(context, mAlertDialogBuilder, mAccountActions.getSession());
        }
    }

    @Synthetic
    void onVoteError(Throwable throwable) {
        Toast.makeText(context, AppUtils.accountErrorMessageRes(throwable, R.string.vote_failed),
                Toast.LENGTH_SHORT).show();
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        boolean mIsFooter;
        TextView mPostedTextView;
        TextView mContentTextView;
        TextView mReadMoreTextView;
        TextView mCommentButton;
        View mMoreButton;
        View mContentView;
        View mNewIndicator;

        ItemViewHolder(View itemView) {
            super(itemView);
            mPostedTextView = (TextView) itemView.findViewById(R.id.posted);
            mPostedTextView.setMovementMethod(LinkMovementMethod.getInstance());
            mContentTextView = (TextView) itemView.findViewById(R.id.text);
            mReadMoreTextView = (TextView) itemView.findViewById(R.id.more);
            mCommentButton = (TextView) itemView.findViewById(R.id.comment);
            mCommentButton.setVisibility(View.GONE);
            mMoreButton = itemView.findViewById(R.id.button_more);
            mContentView = itemView.findViewById(R.id.content);
            // G6: present only in item_comment.xml, so null in any other holder layout.
            mNewIndicator = itemView.findViewById(R.id.comment_new_indicator);
        }

        ItemViewHolder(View itemView, @SuppressWarnings("UnusedParameters") Object payload) {
            super(itemView);
            mIsFooter = true;
        }

        boolean isFooter() {
            return mIsFooter;
        }
    }

    private static class ItemResponseListener implements ResponseListener<Item> {
        private final WeakReference<ItemRecyclerViewAdapter> mAdapter;
        private final int mPosition;
        private final Item mPartialItem;

        @Synthetic
        ItemResponseListener(ItemRecyclerViewAdapter adapter, int position,
                                    Item partialItem) {
            mAdapter = new WeakReference<>(adapter);
            mPosition = position;
            mPartialItem = partialItem;
        }

        @Override
        public void onResponse(@Nullable Item response) {
            if (mAdapter.get() != null && mAdapter.get().isAttached() && response != null) {
                mPartialItem.populate(response);
                mAdapter.get().onItemLoaded(mPosition, mPartialItem);
            }
        }

        @Override
        public void onError(String errorMessage) {
            // do nothing
        }
    }

    static class VoteCallback extends UserServices.Callback {
        private final WeakReference<ItemRecyclerViewAdapter> mAdapter;

        @Synthetic
        VoteCallback(ItemRecyclerViewAdapter adapter) {
            mAdapter = new WeakReference<>(adapter);
        }

        @Override
        public void onDone(boolean successful) {
            if (mAdapter.get() != null && mAdapter.get().isAttached()) {
                mAdapter.get().onVoted(successful);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (mAdapter.get() != null && mAdapter.get().isAttached()) {
                mAdapter.get().onVoteError(throwable);
            }
        }
    }
}
