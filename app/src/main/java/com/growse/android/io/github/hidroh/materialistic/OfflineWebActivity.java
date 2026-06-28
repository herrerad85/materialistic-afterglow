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

import android.graphics.Color;
import android.os.Bundle;
import androidx.core.widget.NestedScrollView;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.TextView;

import android.webkit.WebViewClient;
import com.growse.android.io.github.hidroh.materialistic.widget.CacheableWebView;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class OfflineWebActivity extends ThemedActivity {
    static final String EXTRA_URL = OfflineWebActivity.class.getName() + ".EXTRA_URL";

    @SuppressWarnings("ConstantConditions")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String url = getIntent().getStringExtra(EXTRA_URL);
        if (TextUtils.isEmpty(url)) {
            finish();
            return;
        }
        setTitle(url);
        setContentView(R.layout.activity_offline_web);
        // No bottom inset padding here: activity_offline_web's root CoordinatorLayout has
        // fitsSystemWindows="true", which already reserves the nav-bar area for the included
        // fragment_web (web_view_container). Padding it again would double the inset.
        final NestedScrollView scrollView = (NestedScrollView) findViewById(R.id.nested_scroll_view);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setOnClickListener(v -> scrollView.smoothScrollTo(0, 0));
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME |
                ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE);
        getSupportActionBar().setSubtitle(R.string.offline);
        final ProgressBar progressBar = (ProgressBar) findViewById(R.id.progress);
        final WebView webView = (WebView) findViewById(R.id.web_view);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                setTitle(view.getTitle());
            }
        });
        webView.setWebChromeClient(new CacheableWebView.ArchiveClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(newProgress);
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                    webView.setBackgroundColor(Color.WHITE);
                    webView.setVisibility(View.VISIBLE);
                }
            }
        });
        AppUtils.toggleWebViewZoom(webView.getSettings(), true);
        // #25: this offline reader only ever opens while cache-only. If the saved web archive is gone
        // (e.g. cleared via the offline storage controls in #24), there is nothing to read, so show a
        // clear not-available-offline state instead of a blank cache-only WebView error.
        if (!CacheableWebView.getArchiveFile(this, url).exists()) {
            webView.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            findViewById(R.id.empty).setVisibility(View.VISIBLE);
            TextView text = findViewById(R.id.download_text);
            if (text != null) {
                text.setText(R.string.offline_empty_article);
            }
            View download = findViewById(R.id.download_button);
            if (download != null) {
                download.setVisibility(View.GONE);
            }
            return;
        }
        webView.loadUrl(url);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
