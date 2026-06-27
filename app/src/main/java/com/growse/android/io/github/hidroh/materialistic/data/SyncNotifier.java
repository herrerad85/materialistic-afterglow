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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import com.growse.android.io.github.hidroh.materialistic.R;

/**
 * The save-for-offline notification reporting (#53), extracted from {@code SyncDelegate}. Owns the
 * downloads channel and the shared progress-notification builder; {@code SyncDelegate} keeps the
 * orchestration and passes in the title, content intent, and progress counts. Notification id,
 * channel, group, builder fields, text, and cancellation are unchanged from the original inline
 * code.
 */
class SyncNotifier {
    private static final String NOTIFICATION_GROUP_KEY = "group";
    private static final String DOWNLOADS_CHANNEL_ID = "downloads";

    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private final NotificationCompat.Builder mNotificationBuilder;

    SyncNotifier(Context context) {
        mContext = context;
        mNotificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(DOWNLOADS_CHANNEL_ID,
                    context.getString(R.string.notification_channel_downloads),
                    NotificationManager.IMPORTANCE_LOW);
            mNotificationManager.createNotificationChannel(channel);
            mNotificationBuilder = new NotificationCompat.Builder(context, DOWNLOADS_CHANNEL_ID);
        } else {
            //noinspection deprecation
            mNotificationBuilder = new NotificationCompat.Builder(context);
        }
        mNotificationBuilder
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(),
                        R.mipmap.ic_launcher))
                .setSmallIcon(R.drawable.ic_notification)
                .setGroup(NOTIFICATION_GROUP_KEY)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setAutoCancel(true);
    }

    void showProgress(String jobId, String title, PendingIntent contentIntent,
                      int max, int progress) {
        mNotificationManager.notify(Integer.valueOf(jobId), mNotificationBuilder
                .setContentTitle(title)
                .setContentText(mContext.getString(R.string.download_in_progress))
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .setProgress(max, progress, false)
                .setSortKey(jobId)
                .build());
    }

    void cancel(int id) {
        mNotificationManager.cancel(id);
    }
}
