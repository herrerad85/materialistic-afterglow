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

import android.text.format.DateUtils;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Inject;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import com.growse.android.io.github.hidroh.materialistic.HackerNews;
import com.growse.android.io.github.hidroh.materialistic.annotation.Synthetic;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Query;

public class AlgoliaClient implements ItemManager {

    public static boolean sSortByTime = true;
    // Request-scoped static, mirroring the pre-existing sSortByTime pattern above (not a new
    // pattern); null = Any time (no date filter). Session-scoped, not persisted.
    public static String sDateRange = null;
    public static final String HOST = "hn.algolia.com";
    private static final String BASE_API_URL = "https://" + HOST + "/api/v1/";
    static final String MIN_CREATED_AT = "created_at_i>";

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({
            LAST_24H,
            PAST_WEEK,
            PAST_MONTH,
            PAST_YEAR
    })
    public @interface Range {}
    public static final String LAST_24H = "last_24h";
    public static final String PAST_WEEK = "past_week";
    public static final String PAST_MONTH = "past_month";
    public static final String PAST_YEAR = "past_year";
    RestService mRestService;
    @Inject @HackerNews ItemManager mHackerNewsClient;

    @Inject
    public AlgoliaClient(RestServiceFactory factory) {
        mRestService = factory.create(BASE_API_URL, RestService.class);
    }

    @Override
    public void getStories(String filter, @CacheMode int cacheMode,
                           final ResponseListener<Item[]> listener) {
        if (listener == null) {
            return;
        }
        // RestServiceFactory's MainThreadExecutor delivers enqueue callbacks on the main thread,
        // replacing the old observeOn(mainThread). search(filter) is overridden by AlgoliaPopularClient.
        search(filter).enqueue(new Callback<AlgoliaHits>() {
            @Override
            public void onResponse(@NonNull Call<AlgoliaHits> call,
                                   @NonNull Response<AlgoliaHits> response) {
                listener.onResponse(toItems(response.body()));
            }

            @Override
            public void onFailure(@NonNull Call<AlgoliaHits> call, @NonNull Throwable t) {
                listener.onError(t != null ? t.getMessage() : "");
            }
        });
    }

    @Override
    public void getItem(String itemId, @CacheMode int cacheMode, ResponseListener<Item> listener) {
        mHackerNewsClient.getItem(itemId, cacheMode, listener);
    }

    @Override
    public Item[] getStories(String filter, @CacheMode int cacheMode) {
        try {
            return toItems(search(filter).execute().body());
        } catch (IOException e) {
            return new Item[0];
        }
    }

    @Override
    public Item getItem(String itemId, @CacheMode int cacheMode) {
        return mHackerNewsClient.getItem(itemId, cacheMode);
    }

    protected Call<AlgoliaHits> search(String filter) {
        String numericFilters = minCreatedAtFilter(sDateRange, System.currentTimeMillis());
        return sSortByTime
                ? mRestService.searchByDate(filter, numericFilters)
                : mRestService.search(filter, numericFilters);
    }

    /**
     * Pure helper (no clock dependency) so the date math is unit-testable. Returns null when no
     * range is selected, else the Algolia numericFilters expression bounding results to items
     * created at or after now minus the range duration.
     */
    static String minCreatedAtFilter(@Range String range, long nowMillis) {
        if (range == null) {
            return null;
        }
        return MIN_CREATED_AT + (nowMillis - durationMillis(range)) / 1000;
    }

    static long durationMillis(@Range String range) {
        switch (range) {
            case LAST_24H:
            default:
                return DateUtils.DAY_IN_MILLIS;
            case PAST_WEEK:
                return DateUtils.WEEK_IN_MILLIS;
            case PAST_MONTH:
                return DateUtils.WEEK_IN_MILLIS * 4;
            case PAST_YEAR:
                return DateUtils.YEAR_IN_MILLIS;
        }
    }

    @NonNull
    private Item[] toItems(AlgoliaHits algoliaHits) {
        if (algoliaHits == null) {
            return new Item[0];
        }
        Hit[] hits = algoliaHits.hits;
        Item[] stories = new Item[hits == null ? 0 : hits.length];
        for (int i = 0; i < stories.length; i++) {
            //noinspection ConstantConditions
            HackerNewsItem item = new HackerNewsItem(
                    Long.parseLong(hits[i].objectID));
            item.rank = i + 1;
            stories[i] = item;
        }
        return stories;
    }

    interface RestService {
        @GET("search_by_date?hitsPerPage=100&tags=story&attributesToRetrieve=objectID&attributesToHighlight=none")
        Call<AlgoliaHits> searchByDate(@Query("query") String query,
                @Query("numericFilters") String numericFilters);

        @GET("search?hitsPerPage=100&tags=story&attributesToRetrieve=objectID&attributesToHighlight=none")
        Call<AlgoliaHits> search(@Query("query") String query,
                @Query("numericFilters") String numericFilters);

        @GET("search?hitsPerPage=100&tags=story&attributesToRetrieve=objectID&attributesToHighlight=none")
        Call<AlgoliaHits> searchByMinTimestamp(@Query("numericFilters") String timestampSeconds);
    }

    static class AlgoliaHits {
        @Keep @Synthetic
        Hit[] hits;
    }

    static class Hit {
        @Keep @Synthetic
        String objectID;
    }
}
