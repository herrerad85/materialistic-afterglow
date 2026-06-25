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

package com.growse.android.io.github.hidroh.materialistic.accounts;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.growse.android.io.github.hidroh.materialistic.AppUtils;
import com.growse.android.io.github.hidroh.materialistic.R;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

public class UserServicesClient implements UserServices {
    private static final String BASE_WEB_URL = "https://news.ycombinator.com";
    private static final String LOGIN_PATH = "login";
    private static final String VOTE_PATH = "vote";
    private static final String COMMENT_PATH = "comment";
    private static final String SUBMIT_PATH = "submit";
    private static final String ITEM_PATH = "item";
    private static final String SUBMIT_POST_PATH = "r";
    private static final String LOGIN_PARAM_ACCT = "acct";
    private static final String LOGIN_PARAM_PW = "pw";
    private static final String LOGIN_PARAM_CREATING = "creating";
    private static final String LOGIN_PARAM_GOTO = "goto";
    private static final String ITEM_PARAM_ID = "id";
    private static final String VOTE_PARAM_ID = "id";
    private static final String VOTE_PARAM_HOW = "how";
    private static final String COMMENT_PARAM_PARENT = "parent";
    private static final String COMMENT_PARAM_TEXT = "text";
    private static final String SUBMIT_PARAM_TITLE = "title";
    private static final String SUBMIT_PARAM_URL = "url";
    private static final String SUBMIT_PARAM_TEXT = "text";
    private static final String SUBMIT_PARAM_FNID = "fnid";
    private static final String SUBMIT_PARAM_FNOP = "fnop";
    private static final String VOTE_DIR_UP = "up";
    private static final String DEFAULT_REDIRECT = "news";
    private static final String CREATING_TRUE = "t";
    private static final String DEFAULT_FNOP = "submit-page";
    private static final String DEFAULT_SUBMIT_REDIRECT = "newest";
    private static final String REGEX_INPUT = "<\\s*input[^>]*>";
    private static final String REGEX_VALUE = "value[^\"]*\"([^\"]*)\"";
    private static final String REGEX_CREATE_ERROR_BODY = "<body>([^<]*)";
    private static final String HEADER_LOCATION = "location";
    private static final String HEADER_COOKIE = "cookie";
    private static final String HEADER_SET_COOKIE = "set-cookie";
    private final Call.Factory mCallFactory;
    private final Executor mIoExecutor;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public UserServicesClient(Call.Factory callFactory, Executor ioExecutor) {
        mCallFactory = callFactory;
        mIoExecutor = ioExecutor;
    }

    @FunctionalInterface
    private interface Action {
        // Fully qualified: the unqualified name Exception would bind to the inherited nested
        // UserServices.Exception (this class implements UserServices), not java.lang.Exception.
        boolean perform() throws java.lang.Exception;
    }

    // Runs the blocking account-action flow on the io executor, then delivers the result on the main
    // thread via the main-looper handler. Replaces the old Rx subscribeOn(io)/observeOn(main) wrapper:
    // network work stays off the main thread, onDone/onError still arrive on the main thread, and any
    // thrown exception (including UserServices.Exception) becomes onError, exactly as the old chains did.
    private void dispatch(Action action, Callback callback) {
        mIoExecutor.execute(() -> {
            try {
                boolean result = action.perform();
                mMainHandler.post(() -> callback.onDone(result));
            } catch (Throwable t) {
                mMainHandler.post(() -> callback.onError(t));
            }
        });
    }

    @Override
    public void login(String username, String password, boolean createAccount, Callback callback) {
        dispatch(() -> {
            Response response = exec(postLogin(username, password, createAccount));
            if (response.code() == HttpURLConnection.HTTP_OK) {
                throw new UserServices.Exception(parseLoginError(response));
            }
            return response.code() == HttpURLConnection.HTTP_MOVED_TEMP;
        }, callback);
    }

    @Override
    public void voteUp(Credentials credentials, String itemId, Callback callback) {
        dispatch(() -> exec(postVote(credentials.getUsername(), credentials.getPassword(), itemId))
                .code() == HttpURLConnection.HTTP_MOVED_TEMP, callback);
    }

    @Override
    public void reply(Credentials credentials, String parentId, String text, Callback callback) {
        dispatch(() -> exec(postReply(parentId, text, credentials.getUsername(), credentials.getPassword()))
                .code() == HttpURLConnection.HTTP_MOVED_TEMP, callback);
    }

    @Override
    public void submit(Credentials credentials, String title, String content, boolean isUrl, Callback callback) {
        /*
          The flow:
          POST /submit with acc, pw
           if 302 to /login, considered failed
          POST /r with fnid, fnop, title, url or text
           if 302 to /newest, considered successful
           if 302 to /x, considered error, maybe duplicate or invalid input
           if 200 or anything else, considered error
         */
        dispatch(() -> {
            // fetch submit page with given credentials; a 302 here is the /login redirect = failed auth
            Response formResponse = exec(postSubmitForm(credentials.getUsername(), credentials.getPassword()));
            if (formResponse.code() == HttpURLConnection.HTTP_MOVED_TEMP) {
                throw new IOException();
            }
            String cookie = formResponse.header(HEADER_SET_COOKIE);
            String fnid;
            try {
                fnid = getInputValue(formResponse.body().string(), SUBMIT_PARAM_FNID);
            } finally {
                formResponse.close();
            }
            if (TextUtils.isEmpty(fnid)) {
                throw new IOException();
            }
            Response submitResponse = exec(postSubmit(title, content, isUrl, cookie, fnid));
            if (submitResponse.code() != HttpURLConnection.HTTP_MOVED_TEMP) {
                throw new IOException();
            }
            Uri uri = Uri.parse(submitResponse.header(HEADER_LOCATION));
            if (TextUtils.equals(uri.getPath(), DEFAULT_SUBMIT_REDIRECT)) {
                return true;
            }
            throw buildException(uri);
        }, callback);
    }

    private Request postLogin(String username, String password, boolean createAccount) {
        FormBody.Builder formBuilder = new FormBody.Builder()
                .add(LOGIN_PARAM_ACCT, username)
                .add(LOGIN_PARAM_PW, password)
                .add(LOGIN_PARAM_GOTO, DEFAULT_REDIRECT);
        if (createAccount) {
            formBuilder.add(LOGIN_PARAM_CREATING, CREATING_TRUE);
        }
        return new Request.Builder()
                .url(HttpUrl.parse(BASE_WEB_URL)
                        .newBuilder()
                        .addPathSegment(LOGIN_PATH)
                        .build())
                .post(formBuilder.build())
                .build();
    }

    private Request postVote(String username, String password, String itemId) {
        return new Request.Builder()
                .url(HttpUrl.parse(BASE_WEB_URL)
                        .newBuilder()
                        .addPathSegment(VOTE_PATH)
                        .build())
                .post(new FormBody.Builder()
                        .add(LOGIN_PARAM_ACCT, username)
                        .add(LOGIN_PARAM_PW, password)
                        .add(VOTE_PARAM_ID, itemId)
                        .add(VOTE_PARAM_HOW, VOTE_DIR_UP)
                        .build())
                .build();
    }

    private Request postReply(String parentId, String text, String username, String password) {
        return new Request.Builder()
                .url(HttpUrl.parse(BASE_WEB_URL)
                        .newBuilder()
                        .addPathSegment(COMMENT_PATH)
                        .build())
                .post(new FormBody.Builder()
                        .add(LOGIN_PARAM_ACCT, username)
                        .add(LOGIN_PARAM_PW, password)
                        .add(COMMENT_PARAM_PARENT, parentId)
                        .add(COMMENT_PARAM_TEXT, text)
                        .build())
                .build();
    }

    private Request postSubmitForm(String username, String password) {
        return new Request.Builder()
                .url(HttpUrl.parse(BASE_WEB_URL)
                        .newBuilder()
                        .addPathSegment(SUBMIT_PATH)
                        .build())
                .post(new FormBody.Builder()
                        .add(LOGIN_PARAM_ACCT, username)
                        .add(LOGIN_PARAM_PW, password)
                        .build())
                .build();
    }

    private Request postSubmit(String title, String content, boolean isUrl, String cookie, String fnid) {
        Request.Builder builder = new Request.Builder()
                .url(HttpUrl.parse(BASE_WEB_URL)
                        .newBuilder()
                        .addPathSegment(SUBMIT_POST_PATH)
                        .build())
                .post(new FormBody.Builder()
                        .add(SUBMIT_PARAM_FNID, fnid)
                        .add(SUBMIT_PARAM_FNOP, DEFAULT_FNOP)
                        .add(SUBMIT_PARAM_TITLE, title)
                        .add(isUrl ? SUBMIT_PARAM_URL : SUBMIT_PARAM_TEXT, content)
                        .build());
        if (!TextUtils.isEmpty(cookie)) {
            builder.addHeader(HEADER_COOKIE, cookie);
        }
        return builder.build();
    }

    private Response exec(Request request) throws IOException {
        return mCallFactory.newCall(request).execute();
    }

    private IOException buildException(Uri uri) {
        switch (uri.getPath()) {
            case ITEM_PATH:
                UserServices.Exception exception = new UserServices.Exception(R.string.item_exist);
                String itemId = uri.getQueryParameter(ITEM_PARAM_ID);
                if (!TextUtils.isEmpty(itemId)) {
                    exception.data = AppUtils.createItemUri(itemId);
                }
                return exception;
            default:
                return new IOException();
        }
    }

    private String getInputValue(String html, String name) {
        // extract <input ... >
        Matcher matcherInput = Pattern.compile(REGEX_INPUT).matcher(html);
        while (matcherInput.find()) {
            String input = matcherInput.group();
            if (input.contains(name)) {
                // extract value="..."
                Matcher matcher = Pattern.compile(REGEX_VALUE).matcher(input);
                return matcher.find() ? matcher.group(1) : null; // return first match if any
            }
        }
        return null;
    }

    private String parseLoginError(Response response) {
        try {
            Matcher matcher = Pattern.compile(REGEX_CREATE_ERROR_BODY).matcher(response.body().string());
            return matcher.find() ? matcher.group(1).replaceAll("\\n|\\r|\\t|\\s+", " ").trim() : null;
        } catch (IOException e) {
            return null;
        }
    }
}
