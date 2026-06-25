/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM characterization of AppUtils helpers that do not touch the Android runtime.
 *
 * - getAbbreviatedTimeSpan reads the inlined DateUtils.*_IN_MILLIS compile-time constants plus
 *   System.currentTimeMillis(); no Android methods execute, so it runs on a plain JVM.
 * - urlEquals delegates to the app's own AndroidUtils.TextUtils (pure Java), not
 *   android.text.TextUtils, so it is likewise Android-free.
 *
 * (isHackerNewsUrl hits android.text.TextUtils; the auth/credentials gate now lives in
 * AccountSession and is characterized under Robolectric in AuthCharacterizationTest /
 * AccountSessionTest.)
 */
class AppUtilsTest {

  private companion object {
    const val MINUTE = 60_000L
    const val HOUR = 3_600_000L
    const val DAY = 86_400_000L
    const val WEEK = 604_800_000L
    const val YEAR = 52L * WEEK // DateUtils.YEAR_IN_MILLIS == 52 weeks
  }

  @Test
  fun getAbbreviatedTimeSpan_bucketsAndSuffixes() {
    val now = System.currentTimeMillis()
    // Use mid-bucket offsets so the integer division lands on the asserted magnitude.
    assertEquals("5m", AppUtils.getAbbreviatedTimeSpan(now - 5 * MINUTE - 1_000))
    assertEquals("3h", AppUtils.getAbbreviatedTimeSpan(now - 3 * HOUR - MINUTE))
    assertEquals("2d", AppUtils.getAbbreviatedTimeSpan(now - 2 * DAY - HOUR))
    assertEquals("4w", AppUtils.getAbbreviatedTimeSpan(now - 4 * WEEK - DAY))
    assertEquals("1y", AppUtils.getAbbreviatedTimeSpan(now - YEAR - WEEK))
  }

  @Test
  fun getAbbreviatedTimeSpan_futureTimeClampsToZeroMinutes() {
    // span is clamped to >= 0, so a future timestamp reports "0m".
    val future = System.currentTimeMillis() + 10 * DAY
    assertEquals("0m", AppUtils.getAbbreviatedTimeSpan(future))
  }

  @Test
  fun urlEquals_normalizesTrailingSlash() {
    assertTrue(AppUtils.urlEquals("https://example.com", "https://example.com/"))
    assertTrue(AppUtils.urlEquals("https://example.com/", "https://example.com/"))
    assertTrue(AppUtils.urlEquals("http://a.test/path", "http://a.test/path"))
  }

  @Test
  fun urlEquals_differentUrlsAndEmptyInputs() {
    assertFalse(AppUtils.urlEquals("https://example.com", "https://other.com"))
    assertFalse(AppUtils.urlEquals("", "https://example.com"))
    assertFalse(AppUtils.urlEquals("https://example.com", ""))
    assertFalse(AppUtils.urlEquals(null, "https://example.com"))
    assertFalse(AppUtils.urlEquals("https://example.com", null))
  }
}
