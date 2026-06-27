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
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.core.view.GravityCompat;
import com.growse.android.io.github.hidroh.materialistic.data.HackerNewsClient;
import com.growse.android.io.github.hidroh.materialistic.data.WebItem;
import com.growse.android.io.github.hidroh.materialistic.widget.PopupMenu;

/**
 * Outbound intent helpers extracted from AppUtils: open a URL externally (Custom Tab / browser
 * chooser, or the offline reader when reading cache-only), share a Story, and the multi-window /
 * Play Store intents. AppUtils keeps thin compatibility wrappers so call sites are unchanged. The
 * offline decision and themed-colour lookups still come from AppUtils.
 */
public final class OutboundIntents {

    private static final String PLAY_STORE_URL = "market://details?id=" + BuildConfig.APPLICATION_ID;

    private OutboundIntents() {}

    public static void openWebUrlExternal(Context context, @Nullable WebItem item,
                                          String url, @Nullable CustomTabsSession session) {
        if (AppUtils.shouldReadCacheOnly(context)) {
            context.startActivity(new Intent(context, OfflineWebActivity.class)
                    .putExtra(OfflineWebActivity.EXTRA_URL, url));
            return;
        }
        Intent intent = createViewIntent(context, item, url, session);
        if (!HackerNewsClient.BASE_WEB_URL.contains(Uri.parse(url).getHost())) {
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
            }
            return;
        }
        List<ResolveInfo> activities = context.getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        ArrayList<Intent> intents = new ArrayList<>();
        for (ResolveInfo info : activities) {
            if (info.activityInfo.packageName.equalsIgnoreCase(context.getPackageName())) {
                continue;
            }
            intents.add(createViewIntent(context, item, url, session)
                    .setPackage(info.activityInfo.packageName));
        }
        if (intents.isEmpty()) {
            return;
        }
        if (intents.size() == 1) {
            context.startActivity(intents.remove(0));
        } else {
            context.startActivity(Intent.createChooser(intents.remove(0),
                    context.getString(R.string.chooser_title))
                    .putExtra(Intent.EXTRA_INITIAL_INTENTS,
                            intents.toArray(new Parcelable[intents.size()])));
        }
    }

    public static Intent makeSendIntentChooser(Context context, Uri data) {
        // use ACTION_SEND_MULTIPLE instead of ACTION_SEND to filter out
        // share receivers that accept only EXTRA_TEXT but not EXTRA_STREAM
        return Intent.createChooser(new Intent(Intent.ACTION_SEND_MULTIPLE)
                        .setType("text/plain")
                        .putParcelableArrayListExtra(Intent.EXTRA_STREAM,
                                new ArrayList<Uri>(){{add(data);}}),
                context.getString(R.string.share_file));
    }

    public static void openExternal(@NonNull final Context context,
                             @NonNull PopupMenu popupMenu,
                             @NonNull View anchor,
                             @NonNull final WebItem item,
                             final CustomTabsSession session) {
        if (TextUtils.isEmpty(item.getUrl()) ||
                item.getUrl().startsWith(HackerNewsClient.BASE_WEB_URL)) {
            openWebUrlExternal(context,
                    item, String.format(HackerNewsClient.WEB_ITEM_PATH, item.getId()),
                    session);
            return;
        }
        popupMenu.create(context, anchor, GravityCompat.END)
                .inflate(R.menu.menu_share)
                .setOnMenuItemClickListener(menuItem -> {
                    openWebUrlExternal(context, item, menuItem.getItemId() == R.id.menu_article ?
                            item.getUrl() :
                            String.format(HackerNewsClient.WEB_ITEM_PATH, item.getId()), session);
                    return true;
                })
                .show();
    }

    public static void share(@NonNull final Context context,
                             @NonNull PopupMenu popupMenu,
                             @NonNull View anchor,
                             @NonNull final WebItem item) {
        if (TextUtils.isEmpty(item.getUrl()) ||
                item.getUrl().startsWith(HackerNewsClient.BASE_WEB_URL)) {
            share(context, item.getDisplayedTitle(),
                    String.format(HackerNewsClient.WEB_ITEM_PATH, item.getId()));
            return;
        }
        popupMenu.create(context, anchor, GravityCompat.END)
                .inflate(R.menu.menu_share)
                .setOnMenuItemClickListener(menuItem -> {
                    share(context, item.getDisplayedTitle(),
                            menuItem.getItemId() == R.id.menu_article ?
                                    item.getUrl() :
                                    String.format(HackerNewsClient.WEB_ITEM_PATH, item.getId()));
                    return true;
                })
                .show();
    }

    public static void share(Context context, String subject, String text) {
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, !TextUtils.isEmpty(subject) ?
                        TextUtils.join(" - ", new String[]{subject, text}) : text);
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        }
    }

    @SuppressWarnings("deprecation")
    public static void openPlayStore(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URL));
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY |
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK |
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_playstore, Toast.LENGTH_SHORT).show();
        }
    }

    @NonNull
    private static Intent createViewIntent(Context context, @Nullable WebItem item,
                                           String url, @Nullable CustomTabsSession session) {
        if (Preferences.customTabsEnabled(context)) {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder(session)
                    .setToolbarColor(AppUtils.getThemedColor(context, R.attr.colorPrimary, Color.BLACK))
                    .setShowTitle(true)
                    .enableUrlBarHiding()
                    .addDefaultShareMenuItem();
            if (item != null) {
                builder.addMenuItem(context.getString(R.string.comments),
                        PendingIntent.getActivity(context, 0,
                                new Intent(context, ItemActivity.class)
                                        .putExtra(ItemActivity.EXTRA_ITEM, item)
                                        .putExtra(ItemActivity.EXTRA_OPEN_COMMENTS, true),
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE :
                                        PendingIntent.FLAG_ONE_SHOT));
            }
            return builder.build().intent.setData(Uri.parse(url));
        } else {
            return new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        }
    }

    @SuppressLint("InlinedApi")
    public static Intent multiWindowIntent(Activity activity, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInMultiWindowMode()) {
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT |
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        }
        return intent;
    }
}
