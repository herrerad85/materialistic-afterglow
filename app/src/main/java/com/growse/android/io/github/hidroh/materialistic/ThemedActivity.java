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

import android.app.ActivityManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import android.text.TextUtils;
import android.view.Menu;

public abstract class ThemedActivity extends AppCompatActivity {
    private final MenuTintDelegate mMenuTintDelegate = new MenuTintDelegate();
    private final Preferences.Observable mThemeObservable = new Preferences.Observable();
    private boolean mResumed = true;
    private boolean mPendingThemeChanged;
    private boolean mDestroyed;
    private final OnBackPressedCallback mBackCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            onBackPressedCompat();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Preferences.Theme.apply(this, isDialogTheme(), isTranslucent());
        if (!isDialogTheme() && !isTranslucent()) {
            androidx.activity.EdgeToEdge.enable(this);
        }
        super.onCreate(savedInstanceState);
        getOnBackPressedDispatcher().addCallback(this, mBackCallback);
        setTaskTitle(getTitle());
        mMenuTintDelegate.onActivityCreated(this);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mThemeObservable.subscribe(this, (key, contextChanged) ->  onThemeChanged(key),
                R.string.pref_theme, R.string.pref_daynight_auto);
    }

    @CallSuper
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mMenuTintDelegate.onOptionsMenuCreated(menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mResumed = true;
        if (mPendingThemeChanged) {
            recreate();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mResumed = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDestroyed = true;
        mThemeObservable.unsubscribe(this);
    }

    /**
     * Back handling for the whole activity chain, routed through {@link OnBackPressedCallback} so it
     * fires for predictive back gestures (the deprecated {@code onBackPressed()} override no longer
     * does). Subclasses override this and call {@code super.onBackPressedCompat()} to fall through to
     * the default finish, exactly as they used to call {@code super.onBackPressed()}.
     */
    protected void onBackPressedCompat() {
        // Default: the framework's back (fragment pop / finishAfterTransition). Disable our callback
        // and re-dispatch so the dispatcher runs its built-in fallback. This is identical to the old
        // super.onBackPressed() (which already routed to the dispatcher with no callbacks), keeping
        // the b/176265 IllegalStateException workaround.
        mBackCallback.setEnabled(false);
        try {
            getOnBackPressedDispatcher().onBackPressed();
        } catch (IllegalStateException e) {
            // TODO http://b.android.com/176265
            supportFinishAfterTransition();
        } finally {
            mBackCallback.setEnabled(true);
        }
    }

    public boolean isActivityDestroyed() {
        return mDestroyed;
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        setTaskTitle(title);
    }

    protected boolean isDialogTheme() {
        return false;
    }

    protected boolean isTranslucent() {
        return false;
    }

    private void onThemeChanged(int key) {
        if (key == R.string.pref_daynight_auto) {
            AppCompatDelegate.setDefaultNightMode(Preferences.Theme.getAutoDayNightMode(this));
        }
        if (mResumed) {
            recreate();
        } else {
            mPendingThemeChanged = true;
        }
    }

    void setTaskTitle(CharSequence title) {
        if (!TextUtils.isEmpty(title)) {
            setTaskDescription(new ActivityManager.TaskDescription(title.toString(),
                    BitmapFactory.decodeResource(getResources(), R.drawable.ic_app),
                    AppUtils.getThemedColor(this, R.attr.colorPrimary, Color.BLACK)));
        }
    }
}
