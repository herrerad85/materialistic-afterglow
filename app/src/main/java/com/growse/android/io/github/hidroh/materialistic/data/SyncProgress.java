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

package com.growse.android.io.github.hidroh.materialistic.data;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Sync progress accounting (#52), extracted from {@code SyncDelegate}. Pure counters with no Android
 * dependency beyond {@link TextUtils}: the work total is "self + immediate kids + article
 * web-progress"; finishing self records the title and the immediate-child count, while each kid and
 * the article web-progress advance the current count.
 */
class SyncProgress {
    private final String id;
    private Boolean self;
    private int totalKids, finishedKids, webProgress, maxWebProgress;
    String title;

    SyncProgress(String id, boolean commentsEnabled, boolean articleEnabled) {
        this.id = id;
        if (commentsEnabled) {
            totalKids = 1;
        }
        if (articleEnabled) {
            maxWebProgress = 100;
        }
    }

    int getMax() {
        return 1 + totalKids + maxWebProgress;
    }

    int getProgress() {
        return (self != null ? 1 : 0) + finishedKids + webProgress;
    }

    void finishItem(@NonNull String id, @Nullable HackerNewsItem item, boolean kidsEnabled) {
        if (TextUtils.equals(id, this.id)) {
            finishSelf(item, kidsEnabled);
        } else {
            finishKid();
        }
    }

    void updateArticle(int webProgress, int maxWebProgress) {
        this.webProgress = webProgress;
        this.maxWebProgress = maxWebProgress;
    }

    private void finishSelf(@Nullable HackerNewsItem item, boolean kidsEnabled) {
        self = item != null;
        title = item != null ? item.getTitle() : null;
        if (kidsEnabled && item != null && item.getKids() != null) {
            // fetch recursively but only notify for immediate children
            totalKids = item.getKids().length;
        } else {
            totalKids = 0;
        }
    }

    private void finishKid() {
        finishedKids++;
    }
}
