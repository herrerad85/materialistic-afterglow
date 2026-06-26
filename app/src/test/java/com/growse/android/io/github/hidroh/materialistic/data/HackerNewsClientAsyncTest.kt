/*
 * Copyright (c) 2026 Afterglow contributors
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
package com.growse.android.io.github.hidroh.materialistic.data

import android.os.Looper
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.Executor
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Characterizes the de-rx'd async paths of [HackerNewsClient] (G4e). A short-circuiting OkHttp
 * interceptor returns canned bodies (no network) and records the outgoing requests; the client runs
 * its io work on a synchronous executor (so it happens inline) then posts the result to the main
 * looper, which the test drains. Proves: list id->ranked-item mapping, item viewed/favorite
 * stamping from the de-rx'd stores, the MODE_CACHE only-if-cached -> normal fallback, and
 * force-network vs cached user routing.
 */
@RunWith(RobolectricTestRunner::class)
class HackerNewsClientAsyncTest {

  private val requests = mutableListOf<Request>()
  private var responder: (Request) -> Response = { json(it, 200, "null") }
  private val viewedItemStore = mockk<ViewedItemStore>(relaxed = true)
  private val favoriteManager = mockk<FavoriteManager>(relaxed = true)
  private lateinit var client: HackerNewsClient

  private fun load(name: String): String =
      checkNotNull(javaClass.classLoader!!.getResourceAsStream(name)) { "missing fixture $name" }
          .bufferedReader()
          .use { it.readText() }

  private fun json(req: Request, code: Int, body: String): Response =
      Response.Builder()
          .request(req)
          .protocol(Protocol.HTTP_1_1)
          .code(code)
          .message(if (code in 200..299) "OK" else "Error")
          .body(body.toResponseBody("application/json".toMediaType()))
          .build()

  @Before
  fun setUp() {
    requests.clear()
    val callFactory =
        OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                  requests.add(chain.request())
                  responder(chain.request())
                }
            )
            .build()
    val factory = RestServiceFactory.Impl(callFactory)
    // Synchronous io executor: dispatch runs inline, then posts onResponse to the main looper.
    client = HackerNewsClient(factory, viewedItemStore, favoriteManager, Executor { it.run() })
  }

  private fun <T> capture(call: (ResponseListener<T>) -> Unit): T? {
    var result: T? = null
    var responded = false
    call(
        object : ResponseListener<T> {
          override fun onResponse(response: T?) {
            result = response
            responded = true
          }

          override fun onError(errorMessage: String?) {
            responded = true
          }
        }
    )
    shadowOf(Looper.getMainLooper()).idle()
    assertTrue("listener never fired", responded)
    return result
  }

  @Test
  fun getStoriesMapsIdsToRankedItemsOnMainThread() {
    responder = { json(it, 200, "[1,2,3]") }

    val stories =
        capture<Array<Item>> {
          client.getStories(ItemManager.TOP_FETCH_MODE, ItemManager.MODE_DEFAULT, it)
        }

    assertEquals(listOf("1", "2", "3"), stories!!.map { it.id })
  }

  @Test
  fun getItemStampsViewedAndFavoriteOntoFetchedItem() {
    every { viewedItemStore.isViewed("8863") } returns true
    every { favoriteManager.check("8863") } returns true
    responder = { json(it, 200, load("hn_item_story.json")) }

    val item = capture<Item> { client.getItem("8863", ItemManager.MODE_DEFAULT, it) }

    assertEquals("8863", item!!.id)
    assertTrue((item as HackerNewsItem).isViewed)
    assertTrue(item.isFavorite)
  }

  @Test
  fun getItemCacheModeFallsBackToNormalFetchOnCacheMiss() {
    // only-if-cached miss -> 504; the client must retry the normal (max-age) endpoint.
    responder = { req ->
      if ((req.header("Cache-Control") ?: "").contains("only-if-cached")) json(req, 504, "")
      else json(req, 200, load("hn_item_story.json"))
    }

    val item = capture<Item> { client.getItem("8863", ItemManager.MODE_CACHE, it) }

    assertEquals("8863", item!!.id)
    assertEquals(2, requests.size)
    assertTrue(requests[0].header("Cache-Control")!!.contains("only-if-cached"))
    assertTrue(requests[1].header("Cache-Control")!!.contains("max-age"))
  }

  @Test
  fun getItemCacheOnlyDoesNotFallBackToNetworkOnCacheMiss() {
    // Strict offline read (#22): only-if-cached miss -> 504, and the client must NOT retry a normal
    // fetch. Distinct from MODE_CACHE, which does fall back (see the test above).
    responder = { req ->
      if ((req.header("Cache-Control") ?: "").contains("only-if-cached")) json(req, 504, "")
      else json(req, 200, load("hn_item_story.json"))
    }

    val item = capture<Item> { client.getItem("8863", ItemManager.MODE_CACHE_ONLY, it) }

    assertNull("strict cache-only must not fall back to network on a miss", item)
    assertEquals(1, requests.size) // exactly one request, no fallback
    assertTrue(requests[0].header("Cache-Control")!!.contains("only-if-cached"))
  }

  @Test
  fun getStoriesCacheOnlyUsesOnlyIfCachedEndpointNotNetwork() {
    // Strict offline read (#22): the story list must hit an only-if-cached endpoint, never a
    // no-cache
    // network endpoint.
    responder = { json(it, 200, "[1,2,3]") }

    val stories =
        capture<Array<Item>> {
          client.getStories(ItemManager.TOP_FETCH_MODE, ItemManager.MODE_CACHE_ONLY, it)
        }

    assertEquals(listOf("1", "2", "3"), stories!!.map { it.id })
    assertEquals(1, requests.size)
    assertTrue(requests[0].url.encodedPath.endsWith("topstories.json"))
    val cacheControl = requests[0].header("Cache-Control") ?: ""
    assertTrue(
        "expected only-if-cached, was '$cacheControl'",
        cacheControl.contains("only-if-cached"),
    )
    assertTrue("must not be a no-cache network read", !cacheControl.contains("no-cache"))
  }

  @Test
  fun getStoriesCacheOnlyDoesNotFallBackToNetworkOnCacheMiss() {
    // An only-if-cached story-list miss is a 504 (null body -> no stories); the client makes
    // exactly
    // one request and never falls back to a network story endpoint.
    responder = { json(it, 504, "") }

    val stories =
        capture<Array<Item>> {
          client.getStories(ItemManager.BEST_FETCH_MODE, ItemManager.MODE_CACHE_ONLY, it)
        }

    assertNull(stories)
    assertEquals(1, requests.size)
    assertTrue(requests[0].url.encodedPath.endsWith("beststories.json"))
    assertTrue(requests[0].header("Cache-Control")!!.contains("only-if-cached"))
  }

  @Test
  fun getUserForceNetworkUsesNoCacheEndpointAndCachedDoesNot() {
    responder = { json(it, 200, """{"id":"norvig","karma":100,"submitted":[8863,9999]}""") }

    val user = capture<UserManager.User> { client.getUser("norvig", it, true) }
    assertEquals("no-cache", requests.last().header("Cache-Control"))
    assertEquals(2, user!!.items.size) // submitted ids mapped onto the user

    requests.clear()
    capture<UserManager.User> { client.getUser("norvig", it) }
    assertNull(requests.last().header("Cache-Control"))
  }
}
