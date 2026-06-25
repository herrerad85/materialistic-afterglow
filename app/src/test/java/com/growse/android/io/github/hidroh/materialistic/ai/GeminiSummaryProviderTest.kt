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
 * Plain JUnit (no Robolectric): a real OkHttpClient with [GeminiAuthInterceptor] wired to a fake
 * [SecretStore], pointed at a [MockWebServer], exercises the Gemini provider end to end including
 * the x-goog-api-key attachment, the generateContent path, and the HTTP-error mapping. No request
 * ever reaches the real Gemini API. Mirrors AnthropicSummaryProviderTest.
 */
class GeminiSummaryProviderTest {

  private lateinit var server: MockWebServer

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

  private fun providerWith(secretStore: SecretStore): GeminiSummaryProvider {
    val client = OkHttpClient.Builder().addInterceptor(GeminiAuthInterceptor(secretStore)).build()
    val service =
        Retrofit.Builder()
            .baseUrl(server.url("/"))
            .callFactory(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiService::class.java)
    return GeminiSummaryProvider(service, secretStore, UnconfinedTestDispatcher())
  }

  @Test
  fun success_returnsTextAndSendsAuthHeaderAndPath() = runTest {
    val store = FakeSecretStore("test-key-abc")
    server.enqueue(
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """{"candidates":[{"content":{"role":"model","parts":[{"text":"SUMMARY"}]},"finishReason":"STOP"}]}"""
            )
    )

    val result = providerWith(store).summarize("thread text")

    assertEquals("SUMMARY", result)
    val recorded = server.takeRequest()
    assertEquals("test-key-abc", recorded.getHeader("x-goog-api-key"))
    assertEquals("/v1beta/models/${GeminiSummaryProvider.MODEL}:generateContent", recorded.path)
  }

  @Test
  fun success_multipleParts_joinedWithNewline() = runTest {
    val store = FakeSecretStore("test-key-abc")
    server.enqueue(
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """{"candidates":[{"content":{"parts":[{"text":"first"},{"text":"second"}]}}]}"""
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
  fun http400_throwsWithCodeOrInvalidInMessage() = runTest {
    val store = FakeSecretStore("bad-key")
    server.enqueue(
        MockResponse()
            .setResponseCode(400)
            .setBody("""{"error":{"code":400,"message":"","status":"INVALID_ARGUMENT"}}""")
    )

    try {
      providerWith(store).summarize("thread text")
      fail("expected LlmSummaryException")
    } catch (e: LlmSummaryException) {
      val message = e.message ?: ""
      assertTrue(message.contains("400") || message.contains("Invalid"))
    }
  }

  @Test
  fun safetyBlock_throws() = runTest {
    val store = FakeSecretStore("test-key-abc")
    server.enqueue(
        MockResponse()
            .setResponseCode(200)
            .setBody("""{"candidates":[],"promptFeedback":{"blockReason":"SAFETY"}}""")
    )

    try {
      providerWith(store).summarize("thread text")
      fail("expected LlmSummaryException")
    } catch (e: LlmSummaryException) {
      // expected
    }
  }

  @Test
  fun emptyCandidates_throws() = runTest {
    val store = FakeSecretStore("test-key-abc")
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"candidates":[]}"""))

    try {
      providerWith(store).summarize("thread text")
      fail("expected LlmSummaryException")
    } catch (e: LlmSummaryException) {
      // expected
    }
  }
}
