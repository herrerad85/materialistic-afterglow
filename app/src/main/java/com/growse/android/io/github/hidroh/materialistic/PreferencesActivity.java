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

package com.growse.android.io.github.hidroh.materialistic;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.ActionBar;
import androidx.preference.PreferenceFragmentCompat;
import androidx.appcompat.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;

import com.growse.android.io.github.hidroh.materialistic.ai.AiPreferenceController;
import com.growse.android.io.github.hidroh.materialistic.reply.ReplyNotificationPreferenceController;

public class PreferencesActivity extends ThemedActivity {
    public static final String EXTRA_TITLE = PreferencesActivity.class.getName() + ".EXTRA_TITLE";
    public static final String EXTRA_PREFERENCES = PreferencesActivity.class.getName() + ".EXTRA_PREFERENCES";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences);
        setTitle(getIntent().getIntExtra(EXTRA_TITLE, 0));
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME |
                ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE);
        AppUtils.padTopSystemBars(findViewById(R.id.toolbar));
        if (savedInstanceState == null) {
            Bundle args = new Bundle();
            args.putInt(EXTRA_PREFERENCES, getIntent().getIntExtra(EXTRA_PREFERENCES, 0));
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.content_frame,
                            Fragment.instantiate(this, SettingsFragment.class.getName(), args),
                            SettingsFragment.class.getName())
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Preferences.sync(getPreferenceManager());
            // Scoped hook (E5-D6): only attach the reply-notification controller on the screen that
            // actually has the switch, so we never register an activity-result launcher elsewhere.
            if (findPreference(getString(R.string.pref_reply_notifications)) != null) {
                new ReplyNotificationPreferenceController(this).attach();
            }
            // Scoped hook (E7-D4/D5): only attach the AI controller on the screen that actually has
            // the AI summaries switch, so the consent/key-entry wiring never runs elsewhere.
            if (findPreference(getString(R.string.pref_ai_summaries_enabled)) != null) {
                new AiPreferenceController(this).attach();
            }
        }

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            addPreferencesFromResource(getArguments().getInt(EXTRA_PREFERENCES));
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            getListView().setClipToPadding(false);
            AppUtils.padBottomSystemBars(getListView(), false);
        }
    }
}
