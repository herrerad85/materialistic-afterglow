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

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import com.growse.android.io.github.hidroh.materialistic.data.HackerNewsClient;
import com.growse.android.io.github.hidroh.materialistic.data.WebItem;

/**
 * In-app deep-link URI helpers extracted from AppUtils: build and parse the {@code <appId>://item/…}
 * / {@code <appId>://user/…} deep links. The scheme is {@link BuildConfig#APPLICATION_ID} (the only
 * applicationId-derived surface here) and MUST stay derived from it, never hardcoded, so the private
 * and public builds keep their own schemes. AppUtils keeps thin compatibility wrappers so call sites
 * are unchanged.
 */
public final class ItemUris {

    private static final String HOST_ITEM = "item";
    private static final String HOST_USER = "user";

    private ItemUris() {}

    public static Uri createItemUri(@NonNull String itemId) {
        return new Uri.Builder()
                .scheme(BuildConfig.APPLICATION_ID)
                .authority(HOST_ITEM)
                .path(itemId)
                .build();
    }

    public static Uri createUserUri(@NonNull String userId) {
        return new Uri.Builder()
                .scheme(BuildConfig.APPLICATION_ID)
                .authority(HOST_USER)
                .path(userId)
                .build();
    }

    public static String getDataUriId(@NonNull Intent intent, String altParamId) {
        if (intent.getData() == null) {
            return null;
        }
        if (TextUtils.equals(intent.getData().getScheme(), BuildConfig.APPLICATION_ID)) {
            return intent.getData().getLastPathSegment();
        } else { // web URI
            return intent.getData().getQueryParameter(altParamId);
        }
    }

    public static boolean isHackerNewsUrl(WebItem item) {
        return !TextUtils.isEmpty(item.getUrl()) &&
                item.getUrl().equals(String.format(HackerNewsClient.WEB_ITEM_PATH, item.getId()));
    }
}
