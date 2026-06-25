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

import javax.inject.Inject;

import retrofit2.Call;

public class AlgoliaPopularClient extends AlgoliaClient {

    @Inject
    public AlgoliaPopularClient(RestServiceFactory factory) {
        super(factory);
    }

    @Override
    protected Call<AlgoliaHits> search(@Range String filter) {
        return mRestService.searchByMinTimestamp(MIN_CREATED_AT + toTimestamp(filter) / 1000);
    }

    private long toTimestamp(@Range String filter) {
        return System.currentTimeMillis() - durationMillis(filter);
    }
}
