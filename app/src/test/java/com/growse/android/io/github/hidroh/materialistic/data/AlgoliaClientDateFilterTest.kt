/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data

import android.text.format.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pure characterization of [AlgoliaClient.minCreatedAtFilter], the clock-free helper that turns a
 * selected time range into the Algolia numericFilters expression. Expected lower bounds are
 * computed from the same [DateUtils] constants the code uses, so the asserted seconds stay locked
 * to the real durations rather than hand-typed guesses.
 */
@RunWith(RobolectricTestRunner::class)
class AlgoliaClientDateFilterTest {

  private val now = 1_700_000_000_000L
  private val nowSeconds = now / 1000 // 1_700_000_000

  @Test
  fun nullRangeMeansAnyTime() {
    assertNull(AlgoliaClient.minCreatedAtFilter(null, now))
    assertNull(AlgoliaClient.minCreatedAtFilter(null, 0L))
  }

  @Test
  fun last24hSubtractsOneDay() {
    val expectedSeconds = nowSeconds - DateUtils.DAY_IN_MILLIS / 1000 // 1_700_000_000 - 86_400
    assertEquals(
        "created_at_i>$expectedSeconds",
        AlgoliaClient.minCreatedAtFilter(AlgoliaClient.LAST_24H, now),
    )
  }

  @Test
  fun pastWeekSubtractsOneWeek() {
    val expectedSeconds = nowSeconds - DateUtils.WEEK_IN_MILLIS / 1000 // 1_700_000_000 - 604_800
    assertEquals(
        "created_at_i>$expectedSeconds",
        AlgoliaClient.minCreatedAtFilter(AlgoliaClient.PAST_WEEK, now),
    )
  }

  @Test
  fun pastMonthSubtractsFourWeeks() {
    val expectedSeconds =
        nowSeconds - DateUtils.WEEK_IN_MILLIS * 4 / 1000 // 1_700_000_000 - 2_419_200
    assertEquals(
        "created_at_i>$expectedSeconds",
        AlgoliaClient.minCreatedAtFilter(AlgoliaClient.PAST_MONTH, now),
    )
  }

  @Test
  fun pastYearSubtractsOneYear() {
    val expectedSeconds = nowSeconds - DateUtils.YEAR_IN_MILLIS / 1000 // 1_700_000_000 - 31_449_600
    assertEquals(
        "created_at_i>$expectedSeconds",
        AlgoliaClient.minCreatedAtFilter(AlgoliaClient.PAST_YEAR, now),
    )
  }
}
