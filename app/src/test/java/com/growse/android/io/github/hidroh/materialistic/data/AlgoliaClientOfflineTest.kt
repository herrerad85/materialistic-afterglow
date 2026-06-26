/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proves explicit Offline mode (#22) does not trigger Algolia network calls: a strict cache-only
 * (MODE_CACHE_ONLY) story/search read returns an empty result without hitting the search backend. A
 * recording interceptor would capture any outgoing request, so an empty request list proves no
 * network call was made. Covers Search and, via inheritance, AlgoliaPopularClient (Popular tab).
 */
@RunWith(RobolectricTestRunner::class)
class AlgoliaClientOfflineTest {

  private val requests = mutableListOf<Request>()
  private lateinit var client: AlgoliaClient

  @Before
  fun setUp() {
    requests.clear()
    val callFactory =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
              requests.add(chain.request())
              Response.Builder()
                  .request(chain.request())
                  .protocol(Protocol.HTTP_1_1)
                  .code(200)
                  .message("OK")
                  .body("{}".toResponseBody("application/json".toMediaType()))
                  .build()
            }
            .build()
    client = AlgoliaClient(RestServiceFactory.Impl(callFactory))
  }

  @Test
  fun syncStrictCacheOnlyReturnsEmptyWithoutNetworkCall() {
    val stories = client.getStories("query", ItemManager.MODE_CACHE_ONLY)

    assertEquals(0, stories.size)
    assertTrue("explicit offline mode must not make an Algolia request", requests.isEmpty())
  }

  @Test
  fun asyncStrictCacheOnlyReturnsEmptyWithoutNetworkCall() {
    var result: Array<Item>? = null
    client.getStories(
        "query",
        ItemManager.MODE_CACHE_ONLY,
        object : ResponseListener<Array<Item>> {
          override fun onResponse(response: Array<Item>?) {
            result = response
          }

          override fun onError(errorMessage: String?) {}
        },
    )

    assertEquals(0, result!!.size)
    assertTrue("explicit offline mode must not make an Algolia request", requests.isEmpty())
  }
}
