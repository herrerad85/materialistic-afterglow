/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

import com.google.gson.Gson
import com.growse.android.io.github.hidroh.materialistic.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * A live [LlmSummaryProvider] backed by the Gemini generateContent API ([GeminiService]). The
 * recommended free-tier path (E7 provider-selectable): a Google AI Studio key calls a Flash model
 * at no cost. Mirrors [AnthropicSummaryProvider]: key pre-check, all work on [IoDispatcher],
 * failures normalized to [LlmSummaryException] (HTTP code mapped, preferring the parsed
 * error.message), and [CancellationException] rethrown. A safety block (no candidate text, a
 * promptFeedback.blockReason) is surfaced as a typed error rather than an empty summary.
 */
@Singleton
class GeminiSummaryProvider
@Inject
constructor(
    private val service: GeminiService,
    private val secretStore: SecretStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LlmSummaryProvider {

  override suspend fun summarize(threadText: String): String {
    // Typed pre-check for the UI; the interceptor still hard-stops a keyless request.
    if (!secretStore.hasApiKey()) {
      throw LlmSummaryException("No API key set")
    }

    val request =
        GeminiRequest(
            contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(threadText)))),
            systemInstruction =
                GeminiContent(role = null, parts = listOf(GeminiPart(SYSTEM_PROMPT))),
            generationConfig = GeminiGenerationConfig(maxOutputTokens = MAX_TOKENS),
        )

    return withContext(ioDispatcher) {
      val response =
          try {
            service.generateContent("v1beta/models/$MODEL:generateContent", request)
          } catch (e: CancellationException) {
            throw e
          } catch (e: IOException) {
            throw LlmSummaryException("Network error", e)
          }

      if (!response.isSuccessful) {
        throw LlmSummaryException(errorMessageFor(response))
      }

      val body = response.body() ?: throw LlmSummaryException("Empty response")
      // A safety filter returns 200 with no candidate text but a block reason; surface it.
      body.promptFeedback?.blockReason?.let {
        throw LlmSummaryException("Blocked by provider: $it")
      }

      val text =
          body.candidates
              .orEmpty()
              .flatMap { it.content?.parts.orEmpty() }
              .mapNotNull { it.text }
              .joinToString("\n")
      if (text.isBlank()) {
        throw LlmSummaryException("Empty summary")
      }
      text.trim()
    }
  }

  /**
   * Map a non-2xx response to a user-facing message. Best-effort: prefer the parsed error.message,
   * falling back to a code-specific label. Gemini uses 400 for a malformed key and 403 for a
   * permission/disabled key, so both map to the invalid-key message.
   */
  private fun errorMessageFor(response: retrofit2.Response<GeminiResponse>): String {
    val parsedMessage =
        try {
          response.errorBody()?.string()?.let { raw ->
            Gson().fromJson(raw, GeminiResponse::class.java)?.error?.message
          }
        } catch (e: Exception) {
          null
        }
    if (!parsedMessage.isNullOrBlank()) {
      return parsedMessage
    }
    return when (response.code()) {
      400,
      401,
      403 -> "Invalid API key (${response.code()})"
      429 -> "Rate limited (429)"
      else -> "HTTP ${response.code()}"
    }
  }

  companion object {
    // Flash model on the Google AI Studio free tier (verified free + generateContent-compatible,
    // 2026-06-24). Raise alongside the cost cap if summaries miss late-thread context.
    const val MODEL = "gemini-2.5-flash"
    const val MAX_TOKENS = 1024
    const val SYSTEM_PROMPT =
        "You summarize Hacker News comment threads. Produce a concise, " +
            "neutral summary of the main points, the areas of agreement and disagreement, and any " +
            "notable insights or corrections. Prefer short paragraphs or bullet points. Do not invent " +
            "information that is not present in the comments."
  }
}
