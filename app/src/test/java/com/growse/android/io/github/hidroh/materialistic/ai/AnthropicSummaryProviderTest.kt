/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Plain JUnit (no Robolectric): a real OkHttpClient with [AnthropicAuthInterceptor] wired to a fake
 * [SecretStore], pointed at a [MockWebServer], exercises the provider end to end including the
 * auth-header attachment and the HTTP-error mapping. No request ever reaches the real Anthropic
 * API.
 */
class AnthropicSummaryProviderTest {

  private lateinit var server: MockWebServer

  /** In-memory test double; production [SecretStore] (Keystore-backed) is never used here. */
  private class FakeSecretStore(private var key: String?) : SecretStore {
    override fun putApiKey(value: String) {
      key = value
    }

    override fun getApiKey(): String? = key

    override fun hasApiKey(): Boolean = !key.isNullOrBlank()

    override fun clearApiKey() {
      key = null
    }
  }

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  private fun providerWith(secretStore: SecretStore): AnthropicSummaryProvider {
    val client =
        OkHttpClient.Builder().addInterceptor(AnthropicAuthInterceptor(secretStore)).build()
    val service =
        Retrofit.Builder()
            .baseUrl(server.url("/"))
            .callFactory(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AnthropicService::class.java)
    return AnthropicSummaryProvider(service, secretStore, UnconfinedTestDispatcher())
  }

  @Test
  fun success_returnsTextAndSendsAuthHeaders() = runTest {
    val store = FakeSecretStore("test-key-abc")
    server.enqueue(
        MockResponse()
            .setResponseCode(200)
            .setBody("""{"content":[{"type":"text","text":"SUMMARY"}],"stop_reason":"end_turn"}""")
    )

    val result = providerWith(store).summarize("thread text")

    assertEquals("SUMMARY", result)
    val recorded = server.takeRequest()
    assertEquals("test-key-abc", recorded.getHeader("x-api-key"))
    assertEquals("2023-06-01", recorded.getHeader("anthropic-version"))
    assertEquals("/v1/messages", recorded.path)
  }

  @Test
  fun success_multipleTextBlocks_joinedWithNewline() = runTest {
    val store = FakeSecretStore("test-key-abc")
    server.enqueue(
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """{"content":[{"type":"text","text":"first"},{"type":"text","text":"second"}]}"""
            )
    )

    val result = providerWith(store).summarize("thread text")

    assertEquals("first\nsecond", result)
  }

  @Test
  fun noKey_throwsAndMakesNoRequest() = runTest {
    val store = FakeSecretStore(null)

    try {
      providerWith(store).summarize("thread text")
      fail("expected LlmSummaryException")
    } catch (e: LlmSummaryException) {
      // expected
    }
    assertEquals(0, server.requestCount)
  }

  @Test
  fun http401_throwsWithCodeInMessage() = runTest {
    val store = FakeSecretStore("test-key-abc")
    server.enqueue(
        MockResponse()
            .setResponseCode(401)
            .setBody("""{"type":"error","error":{"type":"authentication_error","message":""}}""")
    )

    try {
      providerWith(store).summarize("thread text")
      fail("expected LlmSummaryException")
    } catch (e: LlmSummaryException) {
      val message = e.message ?: ""
      assertTrue(message.contains("401") || message.contains("Invalid"))
    }
  }

  @Test
  fun emptyContentArray_throws() = runTest {
    val store = FakeSecretStore("test-key-abc")
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"content":[]}"""))

    try {
      providerWith(store).summarize("thread text")
      fail("expected LlmSummaryException")
    } catch (e: LlmSummaryException) {
      // expected
    }
  }
}
