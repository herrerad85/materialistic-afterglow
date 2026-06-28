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

import android.content.Context;
import android.text.TextUtils;
import android.webkit.WebResourceResponse;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import okhttp3.HttpUrl;
import okio.BufferedSource;
import okio.Okio;

public class AdBlocker {
    // AdAway default blocklist (CC BY 3.0), bundled as a hosts file. See THIRD-PARTY-NOTICES.txt.
    private static final String AD_HOSTS_FILE = "adaway-hosts.txt";
    private static final Set<String> AD_HOSTS = new HashSet<>();

    // One-shot background load of the ad-host list at app start. A bare thread replaces the old
    // rx fire-and-forget (Observable.fromCallable + subscribeOn(io) + onErrorReturn(null)): same
    // semantics, no scheduler dependency, no executor to keep alive. A bare thread is enough for a
    // single startup load; switch to a shared executor only if more startup background work appears.
    public static void init(Context context) {
        new Thread(() -> {
            try {
                loadFromAssets(context);
            } catch (IOException ignored) {
                // Asset missing/unreadable: leave the host set empty, never fatal at startup
                // (matches the old onErrorReturn(null) swallow).
            }
        }, "adblock-init").start();
    }

    public static boolean isAd(String url) {
        HttpUrl httpUrl = HttpUrl.parse(url);
        return httpUrl != null && isAdHost(httpUrl.host().toLowerCase(Locale.ROOT));
    }

    public static WebResourceResponse createEmptyResource() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
    }

    /**
     * Parses one line of a hosts-format blocklist into a blockable hostname, or null to skip.
     * Tolerates AdAway's default list: blank lines, '#' comment lines, inline trailing comments,
     * bare hostnames, and "0.0.0.0 host" / "127.0.0.1 host" redirect lines. Non-domain entries
     * (localhost, IPv6 ::1) and anything malformed return null instead of throwing.
     */
    @Nullable
    @VisibleForTesting
    static String parseHostLine(String rawLine) {
        if (rawLine == null) {
            return null;
        }
        String line = rawLine.trim();
        int comment = line.indexOf('#');
        if (comment >= 0) {
            line = line.substring(0, comment).trim();
        }
        if (line.isEmpty()) {
            return null;
        }
        String[] tokens = line.split("\\s+");
        // "<ip> <host>" redirect lines use the second token; a bare hostname uses the only token.
        String host = (tokens.length == 1 ? tokens[0] : tokens[1]).toLowerCase(Locale.ROOT);
        // Keep only real domains: a dot, and no path/port/IPv6 characters. Drops "localhost",
        // "::1 localhost", bare IPs and other malformed entries without throwing.
        if (host.indexOf('.') < 0 || host.indexOf('/') >= 0 || host.indexOf(':') >= 0) {
            return null;
        }
        return host;
    }

    @WorkerThread
    private static void loadFromAssets(Context context) throws IOException {
        InputStream stream = context.getAssets().open(AD_HOSTS_FILE);
        BufferedSource buffer = Okio.buffer(Okio.source(stream));
        String line;
        while ((line = buffer.readUtf8Line()) != null) {
            String host = parseHostLine(line);
            if (host != null) {
                AD_HOSTS.add(host);
            }
        }
        buffer.close();
        stream.close();
    }

    /**
     * Recursively walking up sub domain chain until we exhaust or find a match,
     * effectively doing a longest substring matching here
     */
    private static boolean isAdHost(String host) {
        if (TextUtils.isEmpty(host)) {
            return false;
        }
        int index = host.indexOf(".");
        return index >= 0 && (AD_HOSTS.contains(host) ||
                index + 1 < host.length() && isAdHost(host.substring(index + 1)));
    }
}
