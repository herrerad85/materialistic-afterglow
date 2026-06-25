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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import javax.inject.Inject;

import com.growse.android.io.github.hidroh.materialistic.accounts.AccountSession;
import com.growse.android.io.github.hidroh.materialistic.annotation.Synthetic;
import com.growse.android.io.github.hidroh.materialistic.reply.ReplyNotificationScheduler;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public abstract class DrawerActivity extends ThemedActivity {

    @Inject AlertDialogBuilder mAlertDialogBuilder;
    @Inject AccountSession mAccountSession;
    @Inject ReplyNotificationScheduler mReplyNotificationScheduler;
    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;
    @Synthetic View mDrawer;
    @Synthetic Class<? extends Activity> mPendingNavigation;
    @Synthetic Bundle mPendingNavigationExtras;
    private TextView mDrawerAccount;
    private View mDrawerLogout;
    private View mDrawerUser;
    private final SharedPreferences.OnSharedPreferenceChangeListener mLoginListener
            = (sharedPreferences, key) -> {
        if (TextUtils.equals(key, getString(R.string.pref_username))) {
            setUsername();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        super.setContentView(R.layout.activity_drawer);
        mDrawer = findViewById(R.id.drawer);
        AppUtils.padVerticalSystemBars(mDrawer, false);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerAccount = (TextView) findViewById(R.id.drawer_account);
        mDrawerLogout = findViewById(R.id.drawer_logout);
        mDrawerUser = findViewById(R.id.drawer_user);
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.open_drawer,
                R.string.close_drawer) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                if (drawerView.equals(mDrawer) && mPendingNavigation != null) {
                    final Intent intent = new Intent(DrawerActivity.this, mPendingNavigation);
                    if (mPendingNavigationExtras != null) {
                        intent.putExtras(mPendingNavigationExtras);
                        mPendingNavigationExtras = null;
                    }
                    // TODO M bug https://code.google.com/p/android/issues/detail?id=193822
                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                    mPendingNavigation = null;
                }
            }
        };
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(mLoginListener);
        setUpDrawer();
        setUsername();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return mDrawerToggle.onOptionsItemSelected(item)|| super.onOptionsItemSelected(item);
    }

    @Override
    protected void onBackPressedCompat() {
        if (mDrawerLayout.isDrawerOpen(mDrawer)) {
            closeDrawers();
        } else if (isTaskRoot() && Preferences.isLaunchScreenLast(this)) {
            moveTaskToBack(true);
        } else {
            super.onBackPressedCompat();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDrawerLayout.removeDrawerListener(mDrawerToggle);
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(mLoginListener);
    }

    @Override
    public void setContentView(int layoutResID) {
        ViewGroup drawerLayout = (ViewGroup) findViewById(R.id.drawer_layout);
        View view = getLayoutInflater().inflate(layoutResID, drawerLayout, false);
        //noinspection ConstantConditions
        drawerLayout.addView(view, 0);
    }

    @SuppressWarnings("ConstantConditions")
    private void setUpDrawer() {
        mDrawerAccount.setOnClickListener(v -> showLogin());
        mDrawerLogout.setOnClickListener(v -> confirmLogout());
        View moreContainer = findViewById(R.id.drawer_more_container);
        TextView moreToggle = (TextView) findViewById(R.id.drawer_more);
        moreToggle.setOnClickListener(v -> {
            if (moreContainer.getVisibility() == View.VISIBLE) {
                moreToggle.setTextColor(AppUtils.getThemedColor(DrawerActivity.this,
                        android.R.attr.textColorTertiary, Color.BLACK));
                moreToggle.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_dummy_transparent_24dp, 0,
                        R.drawable.ic_expand_more_white_24dp, 0);
                moreContainer.setVisibility(View.GONE);
            } else {
                moreToggle.setTextColor(AppUtils.getThemedColor(DrawerActivity.this,
                        android.R.attr.textColorSecondary, Color.BLACK));
                moreToggle.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_dummy_transparent_24dp, 0,
                        R.drawable.ic_expand_less_white_24dp, 0);
                moreContainer.setVisibility(View.VISIBLE);
            }
        });
        findViewById(R.id.drawer_list).setOnClickListener(v -> navigate(ListActivity.class));
        findViewById(R.id.drawer_best).setOnClickListener(v -> navigate(BestActivity.class));
        findViewById(R.id.drawer_popular).setOnClickListener(v -> navigate(PopularActivity.class));
        findViewById(R.id.drawer_new).setOnClickListener(v -> navigate(NewActivity.class));
        findViewById(R.id.drawer_show).setOnClickListener(v -> navigate(ShowActivity.class));
        findViewById(R.id.drawer_ask).setOnClickListener(v -> navigate(AskActivity.class));
        findViewById(R.id.drawer_job).setOnClickListener(v -> navigate(JobsActivity.class));
        findViewById(R.id.drawer_settings).setOnClickListener(v -> navigate(SettingsActivity.class));
        findViewById(R.id.drawer_favorite).setOnClickListener(v -> navigate(FavoriteActivity.class));
        findViewById(R.id.drawer_submit).setOnClickListener(v -> navigate(SubmitActivity.class));
        findViewById(R.id.drawer_user).setOnClickListener(v -> {
            Bundle extras = new Bundle();
            extras.putString(UserActivity.EXTRA_USERNAME, mAccountSession.getActiveUsername());
            navigate(UserActivity.class, extras);
        });
        findViewById(R.id.drawer_feedback).setOnClickListener(v -> navigate(FeedbackActivity.class));

    }

    private void showLogin() {
        if (mAccountSession.savedAccounts().isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
        } else {
            AppUtils.showAccountChooser(this, mAlertDialogBuilder, mAccountSession,
                    mReplyNotificationScheduler);
        }
    }

    private void confirmLogout() {
        mAlertDialogBuilder.init(this)
                .setMessage(R.string.logout_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        mAccountSession.logout();
                        // E5-D3: no active session, so stop reply polling.
                        mReplyNotificationScheduler.onLogout();
                })
                .show();
    }

    private void navigate(Class<? extends Activity> activityClass) {
        navigate(activityClass, null);
    }

    private void navigate(Class<? extends Activity> activityClass, @Nullable Bundle extras) {
        mPendingNavigation = !getClass().equals(activityClass) ? activityClass : null;
        mPendingNavigationExtras = extras;
        closeDrawers();
    }

    private void setUsername() {
        String username = mAccountSession.getActiveUsername();
        if (username != null) {
            mDrawerAccount.setText(username);
            mDrawerLogout.setVisibility(View.VISIBLE);
            mDrawerUser.setVisibility(View.VISIBLE);
        } else {
            mDrawerAccount.setText(R.string.login);
            mDrawerLogout.setVisibility(View.GONE);
            mDrawerUser.setVisibility(View.GONE);
        }
    }

    private void closeDrawers() {
        mDrawerLayout.closeDrawers();
    }
}
