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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.widget.TextView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.core.util.Pair;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.growse.android.io.github.hidroh.materialistic.accounts.UserServices;
import com.growse.android.io.github.hidroh.materialistic.annotation.PublicApi;
import com.growse.android.io.github.hidroh.materialistic.data.Item;
import com.growse.android.io.github.hidroh.materialistic.data.ItemManager;
import com.growse.android.io.github.hidroh.materialistic.data.WebItem;
import com.growse.android.io.github.hidroh.materialistic.widget.PopupMenu;

@SuppressWarnings("WeakerAccess")
@PublicApi
public class AppUtils {
    /**
     * Resolves the user-facing message for a failed account action: a {@link UserServices.Exception}
     * carrying a string resource (e.g. an auth or unexpected-response failure) shows that distinct
     * message; any other throwable falls back to the action's generic message.
     */
    @StringRes
    public static int accountErrorMessageRes(Throwable throwable, @StringRes int fallback) {
        if (throwable instanceof UserServices.Exception) {
            int messageRes = ((UserServices.Exception) throwable).messageRes;
            if (messageRes != 0) {
                return messageRes;
            }
        }
        return fallback;
    }

    private static final String ABBR_YEAR = "y";
    private static final String ABBR_WEEK = "w";
    private static final String ABBR_DAY = "d";
    private static final String ABBR_HOUR = "h";
    private static final String ABBR_MINUTE = "m";
    private static final String FORMAT_HTML_COLOR = "%06X";
    public static final int HOT_THRESHOLD_HIGH = 300;
    public static final int HOT_THRESHOLD_NORMAL = 100;
    static final int HOT_THRESHOLD_LOW = 10;
    public static final int HOT_FACTOR = 3;

    // Temporary compatibility wrappers over OutboundIntents (outbound intent helpers extracted from
    // AppUtils) and ItemUris (in-app deep-link URIs); call sites keep calling these.
    public static void openWebUrlExternal(Context context, @Nullable WebItem item,
                                          String url, @Nullable CustomTabsSession session) {
        OutboundIntents.openWebUrlExternal(context, item, url, session);
    }

    // Temporary compatibility wrappers over HtmlText (generic HTML/text rendering extracted from
    // AppUtils); call sites keep calling these.
    public static void setTextWithLinks(TextView textView, CharSequence html) {
        HtmlText.setTextWithLinks(textView, html);
    }

    public static CharSequence fromHtml(String htmlText) {
        return HtmlText.fromHtml(htmlText);
    }

    public static CharSequence fromHtml(String htmlText, boolean compact) {
        return HtmlText.fromHtml(htmlText, compact);
    }

    public static Intent makeSendIntentChooser(Context context, Uri data) {
        return OutboundIntents.makeSendIntentChooser(context, data);
    }

    public static void openExternal(@NonNull final Context context,
                             @NonNull PopupMenu popupMenu,
                             @NonNull View anchor,
                             @NonNull final WebItem item,
                             final CustomTabsSession session) {
        OutboundIntents.openExternal(context, popupMenu, anchor, item, session);
    }

    public static void share(@NonNull final Context context,
                             @NonNull PopupMenu popupMenu,
                             @NonNull View anchor,
                             @NonNull final WebItem item) {
        OutboundIntents.share(context, popupMenu, anchor, item);
    }

    public static int getThemedResId(Context context, @AttrRes int attr) {
        TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{attr});
        final int resId = a.getResourceId(0, 0);
        a.recycle();
        return resId;
    }

    /**
     * Resolves a colour attribute to a concrete @ColorInt. Unlike getThemedResId + ContextCompat,
     * this is correct whether the attr bottoms out at a @color reference OR a direct colour int
     * (the latter happens under dynamic colour / M3 roles). Use getThemedResId only for non-colour
     * attrs (selectableItemBackground, alertDialogTheme, dimens, drawables).
     */
    @ColorInt
    public static int getThemedColor(Context context, @AttrRes int attr, @ColorInt int fallback) {
        return MaterialColors.getColor(context, attr, fallback);
    }

    public static float getDimension(Context context, @StyleRes int styleResId, @AttrRes int attr) {
        TypedArray a = context.getTheme().obtainStyledAttributes(styleResId, new int[]{attr});
        float size = a.getDimension(0, 0);
        a.recycle();
        return size;
    }

    public static boolean isHackerNewsUrl(WebItem item) {
        return ItemUris.isHackerNewsUrl(item);
    }

    public static int getDimensionInDp(Context context, @DimenRes int dimenResId) {
        return (int) (context.getResources().getDimension(dimenResId) /
                        context.getResources().getDisplayMetrics().density);
    }

    public static String getAbbreviatedTimeSpan(long timeMillis) {
        long span = Math.max(System.currentTimeMillis() - timeMillis, 0);
        if (span >= DateUtils.YEAR_IN_MILLIS) {
            return (span / DateUtils.YEAR_IN_MILLIS) + ABBR_YEAR;
        }
        if (span >= DateUtils.WEEK_IN_MILLIS) {
            return (span / DateUtils.WEEK_IN_MILLIS) + ABBR_WEEK;
        }
        if (span >= DateUtils.DAY_IN_MILLIS) {
            return (span / DateUtils.DAY_IN_MILLIS) + ABBR_DAY;
        }
        if (span >= DateUtils.HOUR_IN_MILLIS) {
            return (span / DateUtils.HOUR_IN_MILLIS) + ABBR_HOUR;
        }
        return (span / DateUtils.MINUTE_IN_MILLIS) + ABBR_MINUTE;
    }

    public static boolean isOnWiFi(Context context) {
        NetworkInfo activeNetwork = ((ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        return activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting() &&
                activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
    }

    // Temporary compatibility wrapper over OfflineRead for callers that cannot inject the
    // OfflineReadPolicy seam (UserActivity, NetworkModule). Connectivity only, no explicit offline
    // mode, which NetworkModule's connectivity-only interceptor relies on.
    public static boolean hasConnection(Context context) {
        return OfflineRead.hasConnection(context);
    }

    // Temporary compatibility wrapper over OfflineRead for the plain-View / Java read-path callers
    // (CacheableWebView, ItemActivity, ItemFragment, WebFragment, BaseStoriesActivity); remove when
    // they migrate to the injected OfflineReadPolicy. The decision and its rationale live in
    // OfflineRead / OfflineReadPolicy (#22).
    public static boolean shouldReadCacheOnly(Context context) {
        return OfflineRead.shouldReadCacheOnly(context);
    }

    // Temporary compatibility wrapper over OfflineRead for the Java read-path callers (ItemActivity,
    // ItemFragment, WebFragment); remove when they migrate to the injected OfflineReadPolicy (#22).
    @ItemManager.CacheMode
    public static int effectiveCacheMode(Context context, @ItemManager.CacheMode int requestedMode) {
        return OfflineRead.effectiveCacheMode(context, requestedMode);
    }

    // The offline empty-reason mapping (#25) and the OfflineEmptyReason enum moved to OfflineRead /
    // OfflineReadPolicy; ListFragment now reads them through the injected OfflineReadPolicy seam.

    // Temporary compatibility wrappers over SystemBars (edge-to-edge inset helpers extracted from
    // AppUtils); the View call sites keep calling these. Remove when those sites migrate.
    public static void padTopSystemBars(View view) {
        SystemBars.padTop(view);
    }

    public static void padBottomSystemBars(View view, boolean includeIme) {
        SystemBars.padBottom(view, includeIme);
    }

    public static void padVerticalSystemBars(View view, boolean includeIme) {
        SystemBars.padVertical(view, includeIme);
    }

    public static void marginBottomSystemBars(View view) {
        SystemBars.marginBottom(view);
    }

    // Account/login flow (showLogin + showAccountChooser) moved to accounts/AccountFlowLogic, behind
    // the injected accounts/AccountFlow seam; the scheduler is now an explicit dependency rather than
    // self-sourced via EntryPointAccessors.

    public static void openPlayStore(Context context) {
        OutboundIntents.openPlayStore(context);
    }

    public static void toggleFab(FloatingActionButton fab, boolean visible) {
        if (visible) {
            fab.setTag(null);
            fab.show();
        } else {
            fab.setTag(FabAwareScrollBehavior.HIDDEN);
            fab.hide();
        }
    }

    public static void toggleFabAction(FloatingActionButton fab, WebItem item, boolean commentMode) {
        Context context = fab.getContext();
        fab.setImageResource(commentMode ? R.drawable.ic_reply_white_24dp : R.drawable.ic_zoom_out_map_white_24dp);
        fab.setOnClickListener(v -> {
            if (commentMode) {
                context.startActivity(new Intent(context, ComposeActivity.class)
                        .putExtra(ComposeActivity.EXTRA_PARENT_ID, item.getId())
                        .putExtra(ComposeActivity.EXTRA_PARENT_TEXT,
                                item instanceof Item ? ((Item) item).getText() : null));
            } else {
                LocalBroadcastManager.getInstance(context)
                        .sendBroadcast(new Intent(WebFragment.ACTION_FULLSCREEN)
                                .putExtra(WebFragment.EXTRA_FULLSCREEN, true));
            }
        });
    }

    public static String toHtmlColor(Context context, @AttrRes int colorAttr) {
        return String.format(FORMAT_HTML_COLOR,
                0xFFFFFF & getThemedColor(context, colorAttr, Color.BLACK));
    }

    public static void toggleWebViewZoom(WebSettings webSettings, boolean enabled) {
        webSettings.setSupportZoom(enabled);
        webSettings.setBuiltInZoomControls(enabled);
        webSettings.setDisplayZoomControls(false);
    }

    public static void setStatusBarDim(Window window, boolean dim) {
        setStatusBarColor(window, dim ? Color.TRANSPARENT :
                getThemedColor(window.getContext(), R.attr.colorPrimaryDark, Color.BLACK));
    }

    public static void setStatusBarColor(Window window, int color) {
        window.setStatusBarColor(color);
    }

    public static void navigate(int direction, AppBarLayout appBarLayout, Navigable navigable) {
        switch (direction) {
            case Navigable.DIRECTION_DOWN:
            case Navigable.DIRECTION_RIGHT:
                if (appBarLayout.getBottom() == 0) {
                    navigable.onNavigate(direction);
                } else {
                    appBarLayout.setExpanded(false, true);
                }
                break;
            default:
                navigable.onNavigate(direction);
                break;
        }
    }

    public static int getDisplayHeight(Context context) {
        Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
        Point point = new Point();
        display.getSize(point);
        return point.y;
    }

    public static LayoutInflater createLayoutInflater(Context context) {
        return LayoutInflater.from(new ContextThemeWrapper(context,
                Preferences.Theme.resolvePreferredTextSize(context)));
    }

    public static void share(Context context, String subject, String text) {
        OutboundIntents.share(context, subject, text);
    }

    public static Uri createItemUri(@NonNull String itemId) {
        return ItemUris.createItemUri(itemId);
    }

    public static Uri createUserUri(@NonNull String userId) {
        return ItemUris.createUserUri(userId);
    }

    public static String getDataUriId(@NonNull Intent intent, String altParamId) {
        return ItemUris.getDataUriId(intent, altParamId);
    }

    public static String wrapHtml(Context context, String html) {
        return context.getString(R.string.html,
                Preferences.Theme.getReadabilityTypeface(context),
                toHtmlPx(context, Preferences.Theme.resolvePreferredReadabilityTextSize(context)),
                AppUtils.toHtmlColor(context, android.R.attr.textColorPrimary),
                AppUtils.toHtmlColor(context, android.R.attr.textColorLink),
                TextUtils.isEmpty(html) ? context.getString(R.string.empty_text) : html,
                toHtmlPx(context, context.getResources().getDimension(R.dimen.activity_vertical_margin)),
                toHtmlPx(context, context.getResources().getDimension(R.dimen.activity_horizontal_margin)),
                Preferences.getReadabilityLineHeight(context));
    }

    private static float toHtmlPx(Context context, @StyleRes int textStyleAttr) {
        return toHtmlPx(context, AppUtils.getDimension(context, textStyleAttr, R.attr.contentTextSize));
    }

    private static float toHtmlPx(Context context, float dimen) {
        return dimen / context.getResources().getDisplayMetrics().density;
    }


    public static Intent multiWindowIntent(Activity activity, Intent intent) {
        return OutboundIntents.multiWindowIntent(activity, intent);
    }

    public static void setTextAppearance(TextView textView, @StyleRes int textAppearance) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            textView.setTextAppearance(textAppearance);
        } else {
            //noinspection deprecation
            textView.setTextAppearance(textView.getContext(), textAppearance);
        }
    }

    public static boolean urlEquals(String thisUrl, String thatUrl) {
        if (AndroidUtils.TextUtils.isEmpty(thisUrl) || AndroidUtils.TextUtils.isEmpty(thatUrl)) {
            return false;
        }
        thisUrl = thisUrl.endsWith("/") ? thisUrl : thisUrl + "/";
        thatUrl = thatUrl.endsWith("/") ? thatUrl : thatUrl + "/";
        return AndroidUtils.TextUtils.equals(thisUrl, thatUrl);
    }

    static class SystemUiHelper {
        private final Window window;
        private final int originalUiFlags;
        private boolean enabled = true;

        SystemUiHelper(Window window) {
            this.window = window;
            this.originalUiFlags = window.getDecorView().getSystemUiVisibility();
        }

        @SuppressLint("InlinedApi")
        void setFullscreen(boolean fullscreen) {
            if (!enabled) {
                return;
            }
            if (fullscreen) {
                window.getDecorView().setSystemUiVisibility(originalUiFlags |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            } else {
                window.getDecorView().setSystemUiVisibility(originalUiFlags);
            }
        }

        void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
