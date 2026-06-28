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

import android.graphics.Typeface;
import android.os.StrictMode;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.work.Configuration;

import javax.inject.Inject;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.android.HiltAndroidApp;
import dagger.hilt.components.SingletonComponent;
import com.growse.android.io.github.hidroh.materialistic.accounts.AccountSession;
import com.growse.android.io.github.hidroh.materialistic.data.AlgoliaClient;
import com.growse.android.io.github.hidroh.materialistic.reply.ReplyNotificationScheduler;

@HiltAndroidApp
public class Application extends android.app.Application implements Configuration.Provider {

    public static Typeface TYPE_FACE = null;

    // Supplies the @HiltWorker factory so WorkManager can construct the injected reply-poll worker.
    // Injected (not entry-point-fetched) because getWorkManagerConfiguration() can run before
    // onCreate; field injection is available by the time Hilt has built the application component.
    @Inject HiltWorkerFactory mWorkerFactory;

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(Preferences.Theme.getAutoDayNightMode(this));
        AlgoliaClient.sSortByTime = Preferences.isSortByRecent(this);
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
        }
        Preferences.migrate(this);
        TYPE_FACE = FontCache.getInstance().get(this, Preferences.Theme.getTypeface(this));
        com.growse.android.io.github.hidroh.materialistic.accounts.AccountSession accountSession =
                EntryPointAccessors.fromApplication(this, AccountSessionEntryPoint.class)
                        .accountSession();
        accountSession.startAccountMonitor();
        // Slice 9 G2: drop the legacy "Materialistic" offline-sync account the deleted SyncAdapter
        // path created, so it stops showing as a phantom entry in the account chooser. Idempotent.
        accountSession.purgeLegacySyncAccount();
        // App-start reply-poll reconciliation (E5-D3): the scheduler is idempotent, so re-running it
        // on every launch is safe and re-establishes the schedule if it was lost.
        EntryPointAccessors.fromApplication(this, ReplyNotificationEntryPoint.class)
                .replyNotificationScheduler()
                .reconcile();
        AdBlocker.init(this);
    }

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setWorkerFactory(mWorkerFactory)
                .build();
    }

    /**
     * Composition-root access to the {@link AccountSession} singleton for the one-time account-monitor
     * startup hook. Confined to Application (which cannot be constructor-injected); not a general
     * service locator.
     */
    @EntryPoint
    @InstallIn(SingletonComponent.class)
    interface AccountSessionEntryPoint {
        AccountSession accountSession();
    }

    /**
     * Composition-root access to the {@link ReplyNotificationScheduler} for the app-start reconcile
     * hook (E5-D3). Mirrors {@link AccountSessionEntryPoint}: narrow, Application-confined, not a
     * general service locator.
     */
    @EntryPoint
    @InstallIn(SingletonComponent.class)
    public interface ReplyNotificationEntryPoint {
        ReplyNotificationScheduler replyNotificationScheduler();
    }
}
