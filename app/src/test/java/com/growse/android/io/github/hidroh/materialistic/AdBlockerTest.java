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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Parser checks for the bundled AdAway hosts-format blocklist (AdBlocker.parseHostLine). */
public class AdBlockerTest {

    @Test
    public void parsesRedirectLines() {
        assertEquals("ads.example.com", AdBlocker.parseHostLine("127.0.0.1 ads.example.com"));
        assertEquals("ads.example.com", AdBlocker.parseHostLine("0.0.0.0 ads.example.com"));
    }

    @Test
    public void parsesBareHostname() {
        assertEquals("tracker.example.org", AdBlocker.parseHostLine("tracker.example.org"));
    }

    @Test
    public void normalizesCaseAndStripsInlineComment() {
        assertEquals("ads.example.com",
                AdBlocker.parseHostLine("  0.0.0.0   ADS.Example.com   # an ad host"));
    }

    @Test
    public void skipsBlankCommentAndNullLines() {
        assertNull(AdBlocker.parseHostLine(null));
        assertNull(AdBlocker.parseHostLine(""));
        assertNull(AdBlocker.parseHostLine("   "));
        assertNull(AdBlocker.parseHostLine("# AdAway default blocklist"));
    }

    @Test
    public void skipsLocalhostIpv6AndMalformed() {
        assertNull(AdBlocker.parseHostLine("127.0.0.1 localhost"));
        assertNull(AdBlocker.parseHostLine("::1 localhost"));
        assertNull(AdBlocker.parseHostLine("not_a_domain"));
    }
}
