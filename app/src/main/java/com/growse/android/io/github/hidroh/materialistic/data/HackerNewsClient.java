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

package com.growse.android.io.github.hidroh.materialistic.data;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Path;

/**
 * Client to retrieve Hacker News content asynchronously
 */
public class HackerNewsClient implements ItemManager, UserManager {
    public static final String HOST = "hacker-news.firebaseio.com";
    public static final String BASE_WEB_URL = "https://news.ycombinator.com";
    public static final String WEB_ITEM_PATH = BASE_WEB_URL + "/item?id=%s";
    static final String BASE_API_URL = "https://" + HOST + "/v0/";
    private final RestService mRestService;
    private final ViewedItemStore mViewedItemStore;
    private final FavoriteManager mFavoriteManager;
    // Schedulers.io equivalent: an unbounded cached pool, exactly what the old
    // subscribeOn(Schedulers.io) used. Results are posted back to the main thread via mMainHandler,
    // replacing observeOn(mainThread).
    private final Executor mIoExecutor;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Inject
    public HackerNewsClient(RestServiceFactory factory,
                            ViewedItemStore viewedItemStore,
                            FavoriteManager favoriteManager) {
        this(factory, viewedItemStore, favoriteManager, Executors.newCachedThreadPool());
    }

    // Visible for testing: lets a test inject a synchronous executor so the async paths run inline.
    HackerNewsClient(RestServiceFactory factory,
                     ViewedItemStore viewedItemStore,
                     FavoriteManager favoriteManager,
                     Executor ioExecutor) {
        mRestService = factory.create(BASE_API_URL, RestService.class);
        mViewedItemStore = viewedItemStore;
        mFavoriteManager = favoriteManager;
        mIoExecutor = ioExecutor;
    }

    @Override
    public void getStories(@FetchMode String filter, @CacheMode int cacheMode,
                           final ResponseListener<Item[]> listener) {
        if (listener == null) {
            return;
        }
        dispatch(() -> toItems(getStoriesCall(filter, cacheMode).execute().body()), listener);
    }

    @Override
    public void getItem(final String itemId, @CacheMode int cacheMode, ResponseListener<Item> listener) {
        if (listener == null) {
            return;
        }
        dispatch(() -> {
            // Plain Boolean cache reads on the io thread (the G4d store bridge intent), stamped onto the
            // fetched item before the main-thread callback, exactly as the old zip did.
            boolean isViewed = mViewedItemStore.isViewed(itemId);
            boolean favorite = mFavoriteManager.check(itemId);
            HackerNewsItem hackerNewsItem = fetchItem(itemId, cacheMode);
            if (hackerNewsItem != null) {
                hackerNewsItem.preload();
                hackerNewsItem.setIsViewed(isViewed);
                hackerNewsItem.setFavorite(favorite);
            }
            return hackerNewsItem;
        }, listener);
    }

    @Override
    public Item[] getStories(String filter, @CacheMode int cacheMode) {
        try {
            return toItems(getStoriesCall(filter, cacheMode).execute().body());
        } catch (IOException e) {
            return new Item[0];
        }
    }

    @Override
    public Item getItem(String itemId, @CacheMode int cacheMode) {
        Call<HackerNewsItem> call;
        switch (cacheMode) {
            case MODE_DEFAULT:
            case MODE_CACHE:
            default:
                call = mRestService.item(itemId);
                break;
            case MODE_NETWORK:
                call = mRestService.networkItem(itemId);
                break;
        }
        try {
            return call.execute().body();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void getUser(String username, final ResponseListener<User> listener) {
        getUser(username, listener, false);
    }

    @Override
    public void getUser(String username, final ResponseListener<User> listener, boolean forceNetwork) {
        if (listener == null) {
            return;
        }
        dispatch(() -> {
            UserItem userItem = (forceNetwork
                    ? mRestService.networkUser(username)
                    : mRestService.user(username)).execute().body();
            if (userItem != null) {
                userItem.setSubmittedItems(toItems(userItem.getSubmitted()));
            }
            return userItem;
        }, listener);
    }

    // Runs the blocking fetch on the io executor, then delivers the result (or error message) on the
    // main thread, replacing the old subscribeOn(io)/observeOn(main)/subscribe(onNext, onError) chain.
    private <T> void dispatch(Callable<T> work, ResponseListener<T> listener) {
        mIoExecutor.execute(() -> {
            try {
                T result = work.call();
                mMainHandler.post(() -> listener.onResponse(result));
            } catch (Throwable t) {
                mMainHandler.post(() -> listener.onError(t != null ? t.getMessage() : ""));
            }
        });
    }

    private HackerNewsItem fetchItem(String itemId, @CacheMode int cacheMode) throws IOException {
        switch (cacheMode) {
            case MODE_NETWORK:
                return mRestService.networkItem(itemId).execute().body();
            case MODE_CACHE:
                // Mirror the old cachedItemRx.onErrorResumeNext(itemRx): an only-if-cached miss returns a
                // 504 (or throws), so fall back to the normal item fetch.
                try {
                    Response<HackerNewsItem> cached = mRestService.cachedItem(itemId).execute();
                    if (cached.isSuccessful()) {
                        return cached.body();
                    }
                } catch (IOException ignored) {
                    // fall through to the normal fetch
                }
                return mRestService.item(itemId).execute().body();
            case MODE_DEFAULT:
            default:
                return mRestService.item(itemId).execute().body();
        }
    }

    @NonNull
    private Call<int[]> getStoriesCall(@FetchMode String filter, @CacheMode int cacheMode) {
        Call<int[]> call;
        if (filter == null) {
            // for legacy 'new stories' widgets
            return cacheMode == MODE_NETWORK ?
                    mRestService.networkNewStories() : mRestService.newStories();
        }
        switch (filter) {
            case NEW_FETCH_MODE:
                call = cacheMode == MODE_NETWORK ?
                        mRestService.networkNewStories() : mRestService.newStories();
                break;
            case SHOW_FETCH_MODE:
                call = cacheMode == MODE_NETWORK ?
                        mRestService.networkShowStories() : mRestService.showStories();
                break;
            case ASK_FETCH_MODE:
                call = cacheMode == MODE_NETWORK ?
                        mRestService.networkAskStories() : mRestService.askStories();
                break;
            case JOBS_FETCH_MODE:
                call = cacheMode == MODE_NETWORK ?
                        mRestService.networkJobStories() : mRestService.jobStories();
                break;
            case BEST_FETCH_MODE:
                call = cacheMode == MODE_NETWORK ?
                        mRestService.networkBestStories() : mRestService.bestStories();
                break;
            default:
                call = cacheMode == MODE_NETWORK ?
                        mRestService.networkTopStories() : mRestService.topStories();
                break;
        }
        return call;
    }

    private HackerNewsItem[] toItems(int[] ids) {
        if (ids == null) {
            return null;
        }
        HackerNewsItem[] items = new HackerNewsItem[ids.length];
        for (int i = 0; i < items.length; i++) {
            HackerNewsItem item = new HackerNewsItem(ids[i]);
            item.rank = i + 1;
            items[i] = item;
        }
        return items;
    }

    interface RestService {
        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("topstories.json")
        Call<int[]> topStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("newstories.json")
        Call<int[]> newStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("showstories.json")
        Call<int[]> showStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("askstories.json")
        Call<int[]> askStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("jobstories.json")
        Call<int[]> jobStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("beststories.json")
        Call<int[]> bestStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("topstories.json")
        Call<int[]> networkTopStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("newstories.json")
        Call<int[]> networkNewStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("showstories.json")
        Call<int[]> networkShowStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("askstories.json")
        Call<int[]> networkAskStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("jobstories.json")
        Call<int[]> networkJobStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("beststories.json")
        Call<int[]> networkBestStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("item/{itemId}.json")
        Call<HackerNewsItem> item(@Path("itemId") String itemId);

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("item/{itemId}.json")
        Call<HackerNewsItem> networkItem(@Path("itemId") String itemId);

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_CACHE)
        @GET("item/{itemId}.json")
        Call<HackerNewsItem> cachedItem(@Path("itemId") String itemId);

        @GET("user/{userId}.json")
        Call<UserItem> user(@Path("userId") String userId);

        // no-cache bypasses the 30-min Cache-Control the CacheOverrideNetworkInterceptor stamps on
        // every HN-host response, so the reply poller sees items submitted in the last poll window.
        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("user/{userId}.json")
        Call<UserItem> networkUser(@Path("userId") String userId);
    }
}
