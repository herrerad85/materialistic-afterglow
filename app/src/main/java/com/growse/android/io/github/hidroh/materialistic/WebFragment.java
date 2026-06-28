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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import dagger.hilt.android.AndroidEntryPoint;
import com.growse.android.io.github.hidroh.materialistic.annotation.Synthetic;
import com.growse.android.io.github.hidroh.materialistic.data.FileDownloader;
import com.growse.android.io.github.hidroh.materialistic.data.Item;
import com.growse.android.io.github.hidroh.materialistic.data.ItemManager;
import com.growse.android.io.github.hidroh.materialistic.data.ResponseListener;
import com.growse.android.io.github.hidroh.materialistic.data.WebItem;
import com.growse.android.io.github.hidroh.materialistic.widget.AdBlockWebViewClient;
import com.growse.android.io.github.hidroh.materialistic.widget.CacheableWebView;
import com.growse.android.io.github.hidroh.materialistic.widget.PopupMenu;
import com.growse.android.io.github.hidroh.materialistic.widget.WebView;
import okhttp3.Call;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

@AndroidEntryPoint
public class WebFragment extends LazyLoadFragment
        implements Scrollable, KeyDelegate.BackInterceptor {
    public static final String EXTRA_ITEM = WebFragment.class.getName() +".EXTRA_ITEM";
    private static final String STATE_EMPTY = "state:empty";
    private static final String STATE_READABILITY = "state:readability";
    static final String ACTION_FULLSCREEN = WebFragment.class.getName() + ".ACTION_FULLSCREEN";
    static final String EXTRA_FULLSCREEN = WebFragment.class.getName() + ".EXTRA_FULLSCREEN";
    private static final String STATE_FULLSCREEN = "state:fullscreen";
    private static final String STATE_CONTENT = "state:content";
    public static final String PDF_LOADER_URL = "file:///android_asset/pdf/index.html";
    private static final String PDF_MIME_TYPE = "application/pdf";
    @Synthetic WebView mWebView;
    private NestedScrollView mScrollView;
    @Synthetic boolean mExternalRequired = false;
    @Inject @HackerNews ItemManager mItemManager;
    @Inject PopupMenu mPopupMenu;
    private KeyDelegate.NestedScrollViewHelper mScrollableHelper;
    private final Preferences.Observable mPreferenceObservable = new Preferences.Observable();
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setFullscreen(intent.getBooleanExtra(WebFragment.EXTRA_FULLSCREEN, false));
        }
    };
    private ViewGroup mFullscreenView;
    private ViewGroup mScrollViewContent;
    @Synthetic ImageButton mButtonRefresh;
    private ViewSwitcher mControls;
    private EditText mEditText;
    private View mButtonMore;
    private View mButtonNext;
    protected ProgressBar mProgressBar;
    private boolean mFullscreen;
    private boolean mIsPdf;
    protected String mContent;
    private AppUtils.SystemUiHelper mSystemUiHelper;
    private View mFragmentView;
    @Inject FileDownloader mFileDownloader;
    private WebItem mItem;
    private boolean mIsHackerNewsUrl, mEmpty, mReadability;
    private PdfAndroidJavascriptBridge mPdfAndroidJavascriptBridge;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mPreferenceObservable.subscribe(context, this::onPreferenceChanged,
                R.string.pref_readability_font,
                R.string.pref_readability_line_height,
                R.string.pref_readability_text_size);
        LocalBroadcastManager.getInstance(context).registerReceiver(mReceiver,
                new IntentFilter(ACTION_FULLSCREEN));
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mFullscreen = savedInstanceState.getBoolean(STATE_FULLSCREEN, false);
            mContent = savedInstanceState.getString(STATE_CONTENT);
            mEmpty = savedInstanceState.getBoolean(STATE_EMPTY, false);
            mReadability = savedInstanceState.getBoolean(STATE_READABILITY, false);
            mItem = savedInstanceState.getParcelable(EXTRA_ITEM);
        } else {
            // Reader mode is unavailable because the hosted Mercury parser is dead, so a stored
            // Readability default is coerced to Article.
            mReadability = false;
            mItem = getArguments().getParcelable(EXTRA_ITEM);
        }
        mIsHackerNewsUrl = ItemUris.isHackerNewsUrl(mItem);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (isNewInstance()) {
            mFragmentView = inflater.inflate(R.layout.fragment_web, container, false);
            mFullscreenView = (ViewGroup) mFragmentView.findViewById(R.id.fullscreen);
            mScrollViewContent = (ViewGroup) mFragmentView.findViewById(R.id.scroll_view_content);
            mScrollView = (NestedScrollView) mFragmentView.findViewById(R.id.nested_scroll_view);
            mControls = (ViewSwitcher) mFragmentView.findViewById(R.id.control_switcher);
            mWebView = (WebView) mFragmentView.findViewById(R.id.web_view);
            mButtonRefresh = (ImageButton) mFragmentView.findViewById(R.id.button_refresh);
            mButtonMore = mFragmentView.findViewById(R.id.button_more);
            mButtonNext = mFragmentView.findViewById(R.id.button_next);
            mButtonNext.setEnabled(false);
            mEditText = (EditText) mFragmentView.findViewById(R.id.edittext);
            // No bottom inset padding here. Every host nests this fragment under a
            // fitsSystemWindows="true" CoordinatorLayout that already reserves the nav-bar area
            // (ItemActivity's view_pager; the two-pane detail content pager in
            // layout-w820dp-land/activity_list). Those hosts are ViewPagers, which do not dispatch
            // window insets to their page fragments, so an inset listener on web_view_container
            // never fired anyway (verified by bounds: web_view filled web_view_container exactly).
            // OfflineWebActivity inflates fragment_web directly and handles the inset at its own
            // call site.
            setUpWebControls(mFragmentView);
            setUpWebView(mFragmentView);
        }
        return mFragmentView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setHasOptionsMenu(true);
        if (isNewInstance()) {
            mScrollableHelper = new KeyDelegate.NestedScrollViewHelper(mScrollView);
            mSystemUiHelper = new AppUtils.SystemUiHelper(getActivity().getWindow());
            mSystemUiHelper.setEnabled(!getResources().getBoolean(R.bool.multi_pane));
            if (mFullscreen) {
                setFullscreen(true);
            }
        }
    }

    @Override
    protected void createOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_article, menu);
    }

    @Override
    protected void prepareOptionsMenu(Menu menu) {
        // Reader (Readability) mode is unavailable because the hosted Mercury parser is dead, so the
        // toggle is removed from menu_article.xml; only font options remain.
        menu.findItem(R.id.menu_font_options).setVisible(fontEnabled());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_font_options) {
            showPreferences();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        mWebView.onResume();
        mWebView.resumeTimers();
    }

    @Override
    public void onStop() {
        super.onStop();
        pauseWebView();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_FULLSCREEN, mFullscreen);
        outState.putString(STATE_CONTENT, mContent);
        outState.putParcelable(EXTRA_ITEM, mItem);
        outState.putBoolean(STATE_EMPTY, mEmpty);
        outState.putBoolean(STATE_READABILITY, mReadability);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPdfAndroidJavascriptBridge != null) {
            mPdfAndroidJavascriptBridge.cleanUp();
        }
        mWebView.destroy();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mPreferenceObservable.unsubscribe(getActivity());
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mReceiver);
    }

    @Override
    public void scrollToTop() {
        if (mFullscreen) {
            mWebView.pageUp(true);
        } else {
            mScrollableHelper.scrollToTop();
        }
    }

    @Override
    public boolean scrollToNext() {
        if (mFullscreen) {
            mWebView.pageDown(false);
            return true;
        } else {
            return mScrollableHelper.scrollToNext();
        }
    }

    @Override
    public boolean scrollToPrevious() {
        if (mFullscreen) {
            mWebView.pageUp(false);
            return true;
        } else {
            return mScrollableHelper.scrollToPrevious();
        }
    }

    @Override
    public boolean onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
            return true;
        }
        return false;
    }

    @Override
    protected void load() {
        mWebView.setVisibility(View.INVISIBLE);
        if (ArticleContentMode.forItem(mIsHackerNewsUrl) == ArticleContentMode.SELF_POST) {
            bindContent();
        } else {
            // Reader (Readability) mode is unavailable because the hosted Mercury parser is dead;
            // always fall back to the WebView article.
            // #25: with no URL there is nothing to show, and in cache-only reading (explicit offline
            // mode or no connectivity) an article with no saved web archive cannot load without the
            // network. Show a clear not-available state instead of a blank/cache-only WebView error
            // (this starts no network fetch). The empty-URL guard also keeps getArchiveFile, which
            // hashes the URL, from a null dereference.
            String url = mItem != null ? mItem.getUrl() : null;
            if (TextUtils.isEmpty(url)
                    || (AppUtils.shouldReadCacheOnly(getActivity())
                            && !CacheableWebView.getArchiveFile(getActivity(), url).exists())) {
                showArticleUnavailable();
            } else {
                loadUrl();
            }
        }
    }

    /**
     * #25: show the article empty/error state (the R.id.empty stub that also serves the external-app
     * case) with a message that fits the situation, and hide the download action which does not apply.
     * The off-ramp to turn off explicit offline mode is the item screen's offline notice; this surface
     * only explains why the article is blank.
     */
    private void showArticleUnavailable() {
        mEmpty = true;
        // This is the not-available state, not the external-app fallback; keep the two mutually
        // exclusive so the refresh control knows which one is showing.
        mExternalRequired = false;
        mWebView.setVisibility(View.GONE);
        mFragmentView.findViewById(R.id.empty).setVisibility(View.VISIBLE);
        TextView text = mFragmentView.findViewById(R.id.download_text);
        if (text != null) {
            text.setText(AppUtils.shouldReadCacheOnly(getActivity())
                    ? R.string.offline_empty_article : R.string.connection_error);
        }
        View download = mFragmentView.findViewById(R.id.download_button);
        if (download != null) {
            download.setVisibility(View.GONE);
        }
        setProgress(100);
    }

    /**
     * #25: leaving the not-available-article state to load real content. Hide the shared empty/download
     * view and clear the empty/external flags before the load; the web view shows itself via
     * onPageStarted. The download view's default action/text are restored so the external-app fallback
     * is never left with this surface's offline message or hidden button.
     */
    private void restoreContentView() {
        mEmpty = false;
        mExternalRequired = false;
        View empty = mFragmentView.findViewById(R.id.empty);
        if (empty != null) {
            empty.setVisibility(View.GONE);
        }
        restoreDownloadView();
    }

    private void restoreDownloadView() {
        TextView text = mFragmentView.findViewById(R.id.download_text);
        if (text != null) {
            text.setText(R.string.file_not_supported);
        }
        View download = mFragmentView.findViewById(R.id.download_button);
        if (download != null) {
            download.setVisibility(View.VISIBLE);
        }
    }

    private void loadUrl() {
        restoreContentView();
        setWebSettings(ArticleContentMode.REMOTE_ARTICLE);
        reloadUrl(mItem.getUrl());
    }

    private void reloadUrl(String url) {
        reloadUrl(url, null);
    }

    @SuppressLint("AddJavascriptInterface")
    private void reloadUrl(String url, @Nullable String pdfFilePath) {
        mIsPdf = false;
        if (mPdfAndroidJavascriptBridge != null) {
            mPdfAndroidJavascriptBridge.cleanUp();
            mWebView.removeJavascriptInterface("PdfAndroidJavascriptBridge");
        }
        if (pdfFilePath != null && TextUtils.equals(PDF_LOADER_URL, url)) {
            setProgress(80);
            mIsPdf = true;
            mPdfAndroidJavascriptBridge = new PdfAndroidJavascriptBridge(pdfFilePath, new PdfAndroidJavascriptBridge.Callbacks() {
                @Override
                public void onFailure() {
                    offerExternalApp();
                    setProgress(100);
                }

                @Override
                public void onLoad() {
                    setProgress(100);
                }
            });
            mWebView.addJavascriptInterface(mPdfAndroidJavascriptBridge, "PdfAndroidJavascriptBridge");
            mWebView.setInitialScale(1);
        }
        mWebView.reloadUrl(url);
    }

    @Synthetic
    void loadContent() {
        restoreContentView();
        setWebSettings(ArticleContentMode.SELF_POST);
        mWebView.reloadHtml(AppUtils.wrapHtml(getActivity(), mContent));
    }

    private void bindContent() {
        if (mItem instanceof Item) {
            mContent = ((Item) mItem).getText();
            loadContent();
        } else {
            mItemManager.getItem(mItem.getId(),
                    AppUtils.effectiveCacheMode(getActivity(), ItemManager.MODE_DEFAULT),
                    new ItemResponseListener(this));
        }
    }

    private void pauseWebView() {
        mWebView.onPause();
        mWebView.pauseTimers();
    }

    private boolean fontEnabled() {
        return mReadability && !mEmpty && !TextUtils.isEmpty(mContent);
    }

    private void setUpWebControls(View view) {
        view.findViewById(R.id.toolbar_web).setOnClickListener(v -> scrollToTop());
        view.findViewById(R.id.button_back).setOnClickListener(v -> mWebView.goBack());
        view.findViewById(R.id.button_forward).setOnClickListener(v -> mWebView.goForward());
        view.findViewById(R.id.button_clear).setOnClickListener(v -> {
            mSystemUiHelper.setFullscreen(true);
            reset();
            mControls.showNext();
        });
        view.findViewById(R.id.button_find).setOnClickListener(v -> {
            mEditText.requestFocus();
            toggleSoftKeyboard(true);
            mControls.showNext();
        });
        mButtonRefresh.setOnClickListener(v -> {
            // #25: after the not-available-article state (not the external-app fallback), refresh is a
            // retry: re-run load() so the archive precheck runs again. load() still avoids the network
            // while cache-only; once connectivity is back and Offline mode is off it loads normally.
            if (mEmpty && !mExternalRequired) {
                load();
            } else if (mWebView.getProgress() < 100) {
                mWebView.stopLoading();
            } else {
                mWebView.reload();
            }
        });
        view.findViewById(R.id.button_exit).setOnClickListener(v ->
                LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(
                        new Intent(WebFragment.ACTION_FULLSCREEN)
                                .putExtra(EXTRA_FULLSCREEN, false)));
        mButtonNext.setOnClickListener(v -> mWebView.findNext(true));
        mButtonMore.setOnClickListener(v ->
                mPopupMenu.create(getActivity(), mButtonMore, Gravity.NO_GRAVITY)
                        .inflate(R.menu.menu_web)
                        .setOnMenuItemClickListener(item -> {
                            if (item.getItemId() == R.id.menu_font_options) {
                                showPreferences();
                                return true;
                            }
                            if (item.getItemId() == R.id.menu_zoom_in) {
                                mWebView.zoomIn();
                                return true;
                            }
                            if (item.getItemId() == R.id.menu_zoom_out) {
                                mWebView.zoomOut();
                                return true;
                            }
                            return false;
                        })
                        .setMenuItemVisible(R.id.menu_font_options, fontEnabled())
                        .show());
        mEditText.setOnEditorActionListener((v, actionId, event) -> { findInPage(); return true; });
    }

    private void setUpWebView(View view) {
        mProgressBar = (ProgressBar) view.findViewById(R.id.progress);
        mWebView.setBackgroundColor(Color.TRANSPARENT);
        mWebView.setWebViewClient(new AdBlockWebViewClient(Preferences.adBlockEnabled(getActivity())) {
            @Override
            public void onPageStarted(android.webkit.WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (getActivity() != null) {
                    getActivity().invalidateOptionsMenu();
                }
            }

            @Override
            public void onPageFinished(android.webkit.WebView view, String url) {
                super.onPageFinished(view, url);
                if (getActivity() != null) {
                    getActivity().invalidateOptionsMenu();
                }
            }

            @Override
            public void onReceivedError(android.webkit.WebView view, WebResourceRequest request,
                    WebResourceError error) {
                super.onReceivedError(view, request, error);
                // The article's own page failed to load: a network/connection-level error (host
                // lookup, connect, timeout), not an HTTP status, which routes to
                // onReceivedHttpError. Replace WebView's stock "Update your app" error page with
                // our branded not-available state, which also fits the no-connection case missed by
                // load()'s pre-check when the link reports connected but cannot actually reach the
                // network. Only the main frame: a failed subresource (image, blocked ad) must never
                // blank a working article. Refresh re-runs load() -> loadUrl() -> restoreContentView
                // so reconnecting recovers normally.
                if (request.isForMainFrame() && getActivity() != null) {
                    showArticleUnavailable();
                }
            }
        });
        mWebView.setWebChromeClient(new CacheableWebView.ArchiveClient() {
            @Override
            public void onProgressChanged(android.webkit.WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (!mIsPdf) {
                    setProgress(newProgress);
                }
            }
        });
        mWebView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (getActivity() == null) {
                return;
            }
            if (mimetype.equals(PDF_MIME_TYPE)) {
                setProgress(10);
                mIsPdf = true;
                downloadFileAndRenderPdf();
            } else {
                offerExternalApp();
            }
        });
        AppUtils.toggleWebViewZoom(mWebView.getSettings(), false);
    }

    private void offerExternalApp() {
        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(mItem.getUrl()));
        if (intent.resolveActivity(getActivity().getPackageManager()) == null) {
            return;
        }
        mExternalRequired = true;
        mEmpty = false;
        mWebView.setVisibility(GONE);
        getActivity().findViewById(R.id.empty).setVisibility(VISIBLE);
        // #25: the not-available-article state reuses this same view and hides the button / rewrites the
        // text, so restore the download action and default text before wiring the external-app click.
        restoreDownloadView();
        getActivity().findViewById(R.id.download_button).setOnClickListener(v -> startActivity(intent));
    }

    private void setProgress(int progress) {
        mProgressBar.setProgress(progress);
        mProgressBar.setVisibility(progress == 100 ? GONE : VISIBLE);
        mButtonRefresh.setImageResource(progress == 100 ?
                R.drawable.ic_refresh_white_24dp : R.drawable.ic_clear_white_24dp);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setWebSettings(ArticleContentMode mode) {
        boolean isRemote = mode.isRemote();
        mReadability = !isRemote;
        mWebView.setBackgroundColor(isRemote ? Color.WHITE : Color.TRANSPARENT);
        mWebView.getSettings().setLoadWithOverviewMode(isRemote);
        mWebView.getSettings().setUseWideViewPort(isRemote);
        mWebView.getSettings().setJavaScriptEnabled(true);
        getActivity().invalidateOptionsMenu();
    }

    @Synthetic
    void setFullscreen(boolean isFullscreen) {
        if (getView() == null) {
            return;
        }
        mFullscreen = isFullscreen;
        mControls.setVisibility(isFullscreen ? VISIBLE : View.GONE);
        AppUtils.toggleWebViewZoom(mWebView.getSettings(), isFullscreen);
        ViewGroup.LayoutParams params = mWebView.getLayoutParams();
        if (isFullscreen) {
            mScrollView.removeView(mScrollViewContent);
            mWebView.scrollTo(mScrollView.getScrollX(), mScrollView.getScrollY());
            mFullscreenView.addView(mScrollViewContent);
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        } else {
            reset();
            // We'll zoom out until it returns false, which means it has min zoom level.
            // It's quite dangerous piece of code - potentially could lead to infinite loop,
            // so let's add some reasonable limit just in case
            int i = 0;
            while (mWebView.zoomOut() && i < 30) {
              i++;
            }
            mFullscreenView.removeView(mScrollViewContent);
            mScrollView.addView(mScrollViewContent);
            mScrollView.post(() -> mScrollView.scrollTo(mWebView.getScrollX(), mWebView.getScrollY()));
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        mWebView.setLayoutParams(params);
    }

    private void showPreferences() {
        Bundle args = new Bundle();
        args.putInt(PopupSettingsFragment.EXTRA_TITLE, R.string.font_options);
        args.putIntArray(PopupSettingsFragment.EXTRA_XML_PREFERENCES,
                new int[]{R.xml.preferences_readability});
        ((DialogFragment) Fragment.instantiate(getActivity(),
                PopupSettingsFragment.class.getName(), args))
                .show(getFragmentManager(), PopupSettingsFragment.class.getName());
    }

    private void onPreferenceChanged(int key, boolean contextChanged) {
        if (!contextChanged) {
            load();
        }
    }

    private void reset() {
        mEditText.setText(null);
        mButtonNext.setEnabled(false);
        toggleSoftKeyboard(false);
        mWebView.clearMatches();
    }

    private void findInPage() {
        String query = mEditText.getText().toString().trim();
        if (TextUtils.isEmpty(query)) {
            return;
        }
        mWebView.setFindListener((activeMatchOrdinal, numberOfMatches, isDoneCounting) -> {
            if (isDoneCounting) {
                handleFindResults(numberOfMatches);
            }
        });
        mWebView.findAllAsync(query);
    }

    private void handleFindResults(int numberOfMatches) {
        mButtonNext.setEnabled(numberOfMatches > 0);
        if (numberOfMatches == 0) {
            Toast.makeText(getContext(), R.string.no_matches, Toast.LENGTH_SHORT).show();
        } else {
            toggleSoftKeyboard(false);
        }
    }

    private void toggleSoftKeyboard(boolean visible) {
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (visible) {
            imm.showSoftInput(mEditText, InputMethodManager.SHOW_IMPLICIT);
        } else {
            imm.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);
        }
    }

    @Synthetic
    void onItemLoaded(@NonNull Item response) {
        getActivity().invalidateOptionsMenu();
        mItem = response;
        bindContent();
    }

    @Synthetic
    void onItemUnavailable() {
        // #25: the self-post text could not be read (an offline cache miss or a fetch error). Show the
        // same not-available state as a missing article archive instead of a blank page.
        showArticleUnavailable();
    }

    private void downloadFileAndRenderPdf() {
        mFileDownloader.downloadFile(mItem.getUrl(), PDF_MIME_TYPE, new FileDownloader.FileDownloaderCallback() {
            @Override
            public void onFailure(Call call, IOException e) {
                offerExternalApp();
            }

            @Override
            public void onSuccess(String filePath) {
                reloadUrl(PDF_LOADER_URL, filePath);
            }
        });
    }

    static class ItemResponseListener implements ResponseListener<Item> {
        private final WeakReference<WebFragment> mFragment;

        @Synthetic
        ItemResponseListener(WebFragment webFragment) {
            mFragment = new WeakReference<>(webFragment);
        }

        @Override
        public void onResponse(@Nullable Item response) {
            WebFragment fragment = mFragment.get();
            if (fragment == null || !fragment.isAttached()) {
                return;
            }
            if (response != null) {
                fragment.onItemLoaded(response);
            } else {
                // #25: a null response is a cache miss (offline) or a fetch failure; show the
                // not-available state instead of leaving a blank page.
                fragment.onItemUnavailable();
            }
        }

        @Override
        public void onError(String errorMessage) {
            WebFragment fragment = mFragment.get();
            if (fragment != null && fragment.isAttached()) {
                fragment.onItemUnavailable();
            }
        }
    }

    static class PdfAndroidJavascriptBridge {
        private File mFile;
        private @Nullable RandomAccessFile mRandomAccessFile;
        private @Nullable Callbacks mCallback;
        private Handler mHandler;

        PdfAndroidJavascriptBridge(String filePath, @Nullable Callbacks callback) {
            mFile = new File(filePath);
            mCallback = callback;
            mHandler = new Handler(Looper.getMainLooper());
        }

        @JavascriptInterface
        public String getChunk(long begin, long end) {
            try {
                if (mRandomAccessFile == null) {
                    mRandomAccessFile = new RandomAccessFile(mFile, "r");
                }
                if (mRandomAccessFile != null) {
                    final int bufferSize = (int)(end - begin);
                    byte[] data = new byte[bufferSize];
                    mRandomAccessFile.seek(begin);
                    mRandomAccessFile.read(data);
                    return Base64.encodeToString(data, Base64.DEFAULT);
                } else {
                    return "";
                }
            } catch (IOException e) {
                Log.e("Exception", e.toString());
                return "";
            }
        }

        @JavascriptInterface
        public long getSize() {
            return mFile.length();
        }

        @JavascriptInterface
        public void onLoad() {
            if (mCallback != null) {
                mHandler.post(() -> mCallback.onLoad());
            }
        }

        @JavascriptInterface
        public void onFailure() {
            if (mCallback != null) {
                mHandler.post(() -> mCallback.onFailure());
            }
        }

        public void cleanUp() {
            try {
                if (mRandomAccessFile != null) {
                    mRandomAccessFile.close();
                }
            } catch (IOException e) {
                Log.e("Exception", e.toString());
            }
        }

        @Override
        public void finalize() throws Throwable {
            try {
                cleanUp();
            } finally {
                super.finalize();
            }
        }

        interface Callbacks {
            void onFailure();
            void onLoad();
        }
    }
}
