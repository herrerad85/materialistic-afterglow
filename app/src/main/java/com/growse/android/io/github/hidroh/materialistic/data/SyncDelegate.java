/*
 * Copyright (c) 2016 Ha Duy Trung
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

package com.growse.android.io.github.hidroh.materialistic.data;

import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.Process;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.webkit.WebView;

import java.io.IOException;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import com.growse.android.io.github.hidroh.materialistic.AppUtils;
import com.growse.android.io.github.hidroh.materialistic.ItemActivity;
import com.growse.android.io.github.hidroh.materialistic.Preferences;
import com.growse.android.io.github.hidroh.materialistic.annotation.Synthetic;
import com.growse.android.io.github.hidroh.materialistic.widget.AdBlockWebViewClient;
import com.growse.android.io.github.hidroh.materialistic.widget.CacheableWebView;
import retrofit2.Call;
import retrofit2.Callback;

public class SyncDelegate {
    static final String SYNC_PREFERENCES_FILE = "_syncpreferences";
    private static final long TIMEOUT_MILLIS = DateUtils.MINUTE_IN_MILLIS;

    private final HackerNewsClient.RestService mHnRestService;
    private final SharedPreferences mSharedPreferences;
    private final SyncNotifier mNotifier;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private SyncProgress mSyncProgress;
    private final Context mContext;
    private ProgressListener mListener;
    private Job mJob;
    @VisibleForTesting CacheableWebView mWebView;

    @Inject
    SyncDelegate(Context context, RestServiceFactory factory) {
        mContext = context;
        mSharedPreferences = context.getSharedPreferences(
                context.getPackageName() + SYNC_PREFERENCES_FILE, Context.MODE_PRIVATE);
        mHnRestService = factory.create(HackerNewsClient.BASE_API_URL,
                HackerNewsClient.RestService.class, new BackgroundThreadExecutor());
        mNotifier = new SyncNotifier(context);
    }

    @UiThread
    static void scheduleSync(Context context, Job job) {
        // Single-story save-for-offline only (JobScheduler -> ItemSyncJobService). The legacy
        // full-sync path (empty id -> ContentResolver.requestSync via the SyncAdapter framework, which
        // created a "Materialistic" sync account) was dead and was deleted in Slice 9 G2; nothing
        // schedules an empty-id job any more.
        if (!Preferences.Offline.isEnabled(context) || TextUtils.isEmpty(job.id)) {
            return;
        }
        JobInfo.Builder builder = new JobInfo.Builder(Long.valueOf(job.id).intValue(),
                new ComponentName(context.getPackageName(),
                        ItemSyncJobService.class.getName()))
                .setRequiredNetworkType(Preferences.Offline.isWifiOnly(context) ?
                        JobInfo.NETWORK_TYPE_UNMETERED :
                        JobInfo.NETWORK_TYPE_ANY)
                .setExtras(job.toPersistableBundle());
        if (Preferences.Offline.currentConnectionEnabled(context)) {
            builder.setOverrideDeadline(0);
        }
        ((JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE))
                .schedule(builder.build());
    }

    void subscribe(ProgressListener listener) {
        mListener = listener;
    }

    void performSync(@NonNull Job job) {
        // assume that connection wouldn't change until we finish syncing
        mJob = job;
        if (TextUtils.isEmpty(mJob.id)) {
            return; // empty-id (full-sync) jobs only came from the deleted SyncAdapter path (G2)
        }
        Message message = Message.obtain(mHandler, this::stopSync);
        message.what = Integer.valueOf(mJob.id);
        mHandler.sendMessageDelayed(message, TIMEOUT_MILLIS);
        mSyncProgress = new SyncProgress(mJob.id, mJob.commentsEnabled, mJob.articleEnabled);
        sync(mJob.id);
    }

    private void sync(String itemId) {
        if (SyncPlan.shouldDefer(mJob.connectionEnabled)) {
            defer(itemId);
            return;
        }
        HackerNewsItem cachedItem;
        if ((cachedItem = getFromCache(itemId)) != null) {
            sync(cachedItem);
        } else {
            updateProgress();
            // TODO defer on low battery as well?
            mHnRestService.networkItem(itemId).enqueue(new Callback<HackerNewsItem>() {
                @Override
                public void onResponse(Call<HackerNewsItem> call,
                                       retrofit2.Response<HackerNewsItem> response) {
                    HackerNewsItem item;
                    if ((item = response.body()) != null) {
                        sync(item);
                    }
                }

                @Override
                public void onFailure(Call<HackerNewsItem> call, Throwable t) {
                    notifyItem(itemId, null);
                }
            });
        }
    }

    @Synthetic
    void sync(@NonNull HackerNewsItem item) {
        mSharedPreferences.edit().remove(item.getId()).apply();
        notifyItem(item.getId(), item);
        syncArticle(item);
        syncChildren(item);
    }

    private void syncArticle(@NonNull HackerNewsItem item) {
        if (SyncPlan.shouldSyncArticle(mJob.articleEnabled, item.isStoryType(),
                !TextUtils.isEmpty(item.getUrl()))) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                loadArticle(item);
            } else {
                mContext.startService(new Intent(mContext, WebCacheService.class)
                        .putExtra(WebCacheService.EXTRA_URL, item.getUrl()));
                notifyArticle(100);
            }
        }
    }

    private void loadArticle(@NonNull final HackerNewsItem item) {
        mWebView = new CacheableWebView(mContext);
        mWebView.setWebViewClient(new AdBlockWebViewClient(Preferences.adBlockEnabled(mContext)));
        mWebView.setWebChromeClient(new CacheableWebView.ArchiveClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                notifyArticle(newProgress);
            }
        });
        notifyArticle(0);
        mWebView.loadUrl(item.getUrl());
    }

    private void syncChildren(@NonNull HackerNewsItem item) {
        if (SyncPlan.shouldSyncComments(mJob.commentsEnabled, item.getKids() != null)) {
            for (long id : item.getKids()) {
                sync(String.valueOf(id));
            }
        }
    }

    private void defer(String itemId) {
        mSharedPreferences.edit().putBoolean(itemId, true).apply();
    }

    private HackerNewsItem getFromCache(String itemId) {
        try {
            return mHnRestService.cachedItem(itemId).execute().body();
        } catch (IOException e) {
            return null;
        }
    }

    @Synthetic
    void notifyItem(@NonNull String id, @Nullable HackerNewsItem item) {
        mSyncProgress.finishItem(id, item,
                SyncPlan.commentsCountTowardProgress(mJob.commentsEnabled, mJob.connectionEnabled));
        updateProgress();
    }

    @Synthetic
    void notifyArticle(int newProgress) {
        mSyncProgress.updateArticle(newProgress, 100);
        updateProgress();
    }

    private void updateProgress() {
        if (mSyncProgress.getProgress() >= mSyncProgress.getMax()) { // TODO may never done
            finish(); // TODO finish once only
        } else if (mJob.notificationEnabled) {
            showProgress();
        }
    }

    private void showProgress() {
        mNotifier.showProgress(mJob.id, mSyncProgress.title, getItemActivity(mJob.id),
                mSyncProgress.getMax(), mSyncProgress.getProgress());
    }

    private void finish() {
        if (mListener != null) {
            mListener.onDone(mJob.id);
            mListener = null;
        }
        stopSync();
    }

    void stopSync() {
        // TODO
        mJob.connectionEnabled = false;
        int id = Integer.valueOf(mJob.id);
        mNotifier.cancel(id);
        mHandler.removeMessages(id);
    }

    private PendingIntent getItemActivity(String itemId) {
        return PendingIntent.getActivity(mContext, 0,
                new Intent(Intent.ACTION_VIEW)
                        .setData(AppUtils.createItemUri(itemId))
                        .putExtra(ItemActivity.EXTRA_CACHE_MODE, ItemManager.MODE_CACHE)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE :
                        PendingIntent.FLAG_ONE_SHOT);
    }

    private static class BackgroundThreadExecutor implements Executor {

        @Synthetic BackgroundThreadExecutor() { }

        @Override
        public void execute(@NonNull Runnable r) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            r.run();
        }
    }

    interface ProgressListener {
        void onDone(String token);
    }

    static class Job {
        private static final String EXTRA_ID = "extra:id";
        private static final String EXTRA_CONNECTION_ENABLED = "extra:connectionEnabled";
        private static final String EXTRA_READABILITY_ENABLED = "extra:readabilityEnabled";
        private static final String EXTRA_ARTICLE_ENABLED = "extra:articleEnabled";
        private static final String EXTRA_COMMENTS_ENABLED = "extra:commentsEnabled";
        private static final String EXTRA_NOTIFICATION_ENABLED = "extra:notificationEnabled";
        final String id;
        boolean connectionEnabled;
        boolean readabilityEnabled;
        boolean articleEnabled;
        boolean commentsEnabled;
        boolean notificationEnabled;

        Job(String id) {
            this.id = id;
        }

        Job(PersistableBundle bundle) {
            id = bundle.getString(EXTRA_ID);
            connectionEnabled = bundle.getInt(EXTRA_CONNECTION_ENABLED) == 1;
            readabilityEnabled = bundle.getInt(EXTRA_READABILITY_ENABLED) == 1;
            articleEnabled = bundle.getInt(EXTRA_ARTICLE_ENABLED) == 1;
            commentsEnabled = bundle.getInt(EXTRA_COMMENTS_ENABLED) == 1;
            notificationEnabled = bundle.getInt(EXTRA_NOTIFICATION_ENABLED) == 1;
        }

        @Synthetic PersistableBundle toPersistableBundle() {
            PersistableBundle bundle = new PersistableBundle();
            bundle.putString(EXTRA_ID, id);
            bundle.putInt(EXTRA_CONNECTION_ENABLED, connectionEnabled ? 1 : 0);
            bundle.putInt(EXTRA_READABILITY_ENABLED, readabilityEnabled ? 1 : 0);
            bundle.putInt(EXTRA_ARTICLE_ENABLED, articleEnabled ? 1 : 0);
            bundle.putInt(EXTRA_COMMENTS_ENABLED, commentsEnabled ? 1 : 0);
            bundle.putInt(EXTRA_NOTIFICATION_ENABLED, notificationEnabled ? 1 : 0);
            return bundle;
        }
    }

    public static class JobBuilder {
        private final Job job;

        public JobBuilder(Context context, String id) {
            job = new Job(id);
            setConnectionEnabled(Preferences.Offline.currentConnectionEnabled(context));
            setReadabilityEnabled(Preferences.Offline.isReadabilityEnabled(context));
            setArticleEnabled(Preferences.Offline.isArticleEnabled(context));
            setCommentsEnabled(Preferences.Offline.isCommentsEnabled(context));
            setNotificationEnabled(Preferences.Offline.isNotificationEnabled(context));
        }

        JobBuilder setConnectionEnabled(boolean connectionEnabled) {
            job.connectionEnabled = connectionEnabled;
            return this;
        }

        JobBuilder setReadabilityEnabled(boolean readabilityEnabled) {
            job.readabilityEnabled = readabilityEnabled;
            return this;
        }

        JobBuilder setArticleEnabled(boolean articleEnabled) {
            job.articleEnabled = articleEnabled;
            return this;
        }

        JobBuilder setCommentsEnabled(boolean commentsEnabled) {
            job.commentsEnabled = commentsEnabled;
            return this;
        }

        public JobBuilder setNotificationEnabled(boolean notificationEnabled) {
            job.notificationEnabled = notificationEnabled;
            return this;
        }

        public Job build() {
            return job;
        }
    }
}
