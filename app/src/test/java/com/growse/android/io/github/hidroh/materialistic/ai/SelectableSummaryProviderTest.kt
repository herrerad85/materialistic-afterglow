/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The router picks the concrete provider by the selector's value, defaulting to Gemini. The two
 * concrete providers are mocked; only routing is under test (no network).
 */
class SelectableSummaryProviderTest {

  private val gemini = mockk<GeminiSummaryProvider>()
  private val anthropic = mockk<AnthropicSummaryProvider>()

  private fun router(providerId: String) =
      SelectableSummaryProvider(AiProviderSelector { providerId }, gemini, anthropic)

  @Test
  fun gemini_routesToGemini() = runBlocking {
    coEvery { gemini.summarize(any()) } returns "G"

    val result = router(AiProviderIds.GEMINI).summarize("text")

    assertEquals("G", result)
    coVerify(exactly = 1) { gemini.summarize("text") }
    coVerify(exactly = 0) { anthropic.summarize(any()) }
  }

  @Test
  fun anthropic_routesToAnthropic() = runBlocking {
    coEvery { anthropic.summarize(any()) } returns "A"

    val result = router(AiProviderIds.ANTHROPIC).summarize("text")

    assertEquals("A", result)
    coVerify(exactly = 1) { anthropic.summarize("text") }
    coVerify(exactly = 0) { gemini.summarize(any()) }
  }

  @Test
  fun unknownValue_defaultsToGemini() = runBlocking {
    coEvery { gemini.summarize(any()) } returns "G"

    val result = router("something-else").summarize("text")

    assertEquals("G", result)
    coVerify(exactly = 1) { gemini.summarize("text") }
    coVerify(exactly = 0) { anthropic.summarize(any()) }
  }
}
