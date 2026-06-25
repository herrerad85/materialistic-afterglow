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

import android.os.Build;
import androidx.annotation.Keep;

import javax.inject.Inject;

import com.growse.android.io.github.hidroh.materialistic.BuildConfig;
import com.growse.android.io.github.hidroh.materialistic.annotation.Synthetic;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface FeedbackClient {
    interface Callback {
        void onSent(boolean success);
    }

    void send(String title, String body, Callback callback);

    class Impl implements FeedbackClient {
        private final FeedbackService mFeedbackService;

        @Inject
        public Impl(RestServiceFactory factory) {
            mFeedbackService = factory.create(FeedbackService.GITHUB_API_URL, FeedbackService.class);
        }

        @Override
        public void send(String title, String body, final Callback callback) {
            body = String.format("%s\nDevice: %s %s, SDK: %s, app version: %s",
                    body,
                    Build.MANUFACTURER,
                    Build.MODEL,
                    Build.VERSION.SDK_INT,
                    BuildConfig.VERSION_CODE);
            // enqueue() dispatches both callbacks on the RestServiceFactory MainThreadExecutor, so
            // onSent always lands on the main thread (replacing observeOn(mainScheduler)). A 2xx
            // response is success; any non-2xx response or network failure is reported as false,
            // matching the old map(true)/onErrorReturn(false) behavior.
            mFeedbackService.createGithubIssue(new Issue(title, body))
                    .enqueue(new retrofit2.Callback<Object>() {
                        @Override
                        public void onResponse(Call<Object> call, Response<Object> response) {
                            callback.onSent(response.isSuccessful());
                        }

                        @Override
                        public void onFailure(Call<Object> call, Throwable t) {
                            callback.onSent(false);
                        }
                    });
        }

        interface FeedbackService {
            String GITHUB_API_URL = "https://api.github.com/";

            @POST("repos/hidroh/materialistic/issues")
            @Headers("Authorization: token " + BuildConfig.GITHUB_TOKEN)
            Call<Object> createGithubIssue(@Body Issue issue);
        }

        static class Issue {
            private static final String LABEL_FEEDBACK = "feedback";

            @Keep @Synthetic
            final String title;
            @Keep @Synthetic
            final String body;
            @Keep @Synthetic
            final String[] labels;

            @Synthetic
            Issue(String title, String body) {
                this.title = title;
                this.body = body;
                this.labels = new String[]{LABEL_FEEDBACK};
            }
        }
    }
}
