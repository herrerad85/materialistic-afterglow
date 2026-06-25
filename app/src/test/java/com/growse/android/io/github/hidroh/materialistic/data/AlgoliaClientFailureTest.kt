/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data

import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Call
import retrofit2.Response

/**
 * Regression guard for the search "fail soft" contract. The blocking [AlgoliaClient.getStories]
 * must distinguish a backend failure from a genuinely empty result: a network error or a non-2xx
 * response returns null (which [StoryListViewModel] maps to an error state), while a successful 2xx
 * response with zero hits returns an empty array (the Empty state). [AlgoliaPopularClient] inherits
 * the same blocking path, so the fix covers both Search and the Popular time ranges.
 *
 * Robolectric supplies a working android.util.Log for the diagnostic breadcrumb; MockK drives the
 * Retrofit boundary so no real request is made.
 */
@RunWith(RobolectricTestRunner::class)
class AlgoliaClientFailureTest {

  private val restService = mockk<AlgoliaClient.RestService>()
  private val call = mockk<Call<AlgoliaClient.AlgoliaHits>>()

  @Before
  fun resetStatics() {
    AlgoliaClient.sSortByTime = true
    AlgoliaClient.sDateRange = null
  }

  private fun factory(): RestServiceFactory =
      mockk<RestServiceFactory>().also {
        every { it.create<AlgoliaClient.RestService>(any(), any()) } returns restService
      }

  private fun searchClient(): AlgoliaClient {
    every { restService.searchByDate(any(), any()) } returns call
    every { restService.search(any(), any()) } returns call
    return AlgoliaClient(factory())
  }

  @Test
  fun `network failure surfaces as error, not empty`() {
    every { call.execute() } throws IOException("no network")

    assertNull(
        "an IOException must map to error (null), not an empty result",
        searchClient().getStories("query", ItemManager.MODE_DEFAULT),
    )
  }

  @Test
  fun `non-2xx response surfaces as error, not empty`() {
    every { call.execute() } returns Response.error(500, "".toResponseBody())

    assertNull(
        "a non-2xx response must map to error (null), not an empty result",
        searchClient().getStories("query", ItemManager.MODE_DEFAULT),
    )
  }

  @Test
  fun `successful empty result stays empty, not error`() {
    val body = AlgoliaClient.AlgoliaHits().apply { hits = emptyArray() }
    every { call.execute() } returns Response.success(body)

    val result = searchClient().getStories("query", ItemManager.MODE_DEFAULT)

    assertNotNull("a successful empty hit set must stay an empty list, not error", result)
    assertEquals(0, result!!.size)
  }

  @Test
  fun `popular client inherits fail soft on network failure`() {
    every { restService.searchByMinTimestamp(any()) } returns call
    every { call.execute() } throws IOException("no network")
    val popular = AlgoliaPopularClient(factory())

    assertNull(
        "AlgoliaPopularClient must inherit the fail-soft contract",
        popular.getStories(AlgoliaClient.LAST_24H, ItemManager.MODE_DEFAULT),
    )
  }
}
