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
 * The live [LlmSummaryProvider], backed by the Anthropic Messages API via a dedicated Retrofit
 * client ([AnthropicService]). The key is pre-checked here so a missing key is a typed error for
 * the G4 UI; the [AnthropicAuthInterceptor] is the hard backstop that stops a keyless request even
 * if this check were bypassed. All network work runs on the shared [IoDispatcher].
 *
 * Failures are normalized to [LlmSummaryException]: a non-2xx response maps the HTTP code to a
 * clear message (preferring the parsed error.message when the body parses), an empty or blank body
 * throws, and a network [IOException] is wrapped. [CancellationException] is rethrown so coroutine
 * cancellation still propagates.
 */
@Singleton
class AnthropicSummaryProvider
@Inject
constructor(
    private val service: AnthropicService,
    private val secretStore: SecretStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LlmSummaryProvider {

  override suspend fun summarize(threadText: String): String {
    // Typed pre-check for the UI; the interceptor still hard-stops a keyless request.
    if (!secretStore.hasApiKey()) {
      throw LlmSummaryException("No API key set")
    }

    val request =
        AnthropicRequest(
            model = MODEL,
            maxTokens = MAX_TOKENS,
            system = SYSTEM_PROMPT,
            messages = listOf(AnthropicMessage("user", threadText)),
        )

    return withContext(ioDispatcher) {
      val response =
          try {
            service.messages(request)
          } catch (e: CancellationException) {
            // Never swallow cancellation: let coroutine cancellation propagate.
            throw e
          } catch (e: IOException) {
            throw LlmSummaryException("Network error", e)
          }

      if (!response.isSuccessful) {
        throw LlmSummaryException(errorMessageFor(response))
      }

      val body = response.body() ?: throw LlmSummaryException("Empty response")
      val text =
          body.content
              .orEmpty()
              .filter { it.type == "text" }
              .mapNotNull { it.text }
              .joinToString("\n")
      if (text.isBlank()) {
        throw LlmSummaryException("Empty summary")
      }
      text.trim()
    }
  }

  /**
   * Map a non-2xx response to a user-facing message. Best-effort: prefer the parsed error.message
   * from the error body, falling back to a code-specific label if the body is absent or
   * unparseable.
   */
  private fun errorMessageFor(response: retrofit2.Response<AnthropicResponse>): String {
    val parsedMessage =
        try {
          response.errorBody()?.string()?.let { raw ->
            Gson().fromJson(raw, AnthropicResponse::class.java)?.error?.message
          }
        } catch (e: Exception) {
          // Best-effort parse only: a malformed error body must not mask the HTTP code.
          null
        }
    if (!parsedMessage.isNullOrBlank()) {
      return parsedMessage
    }
    return when (response.code()) {
      401 -> "Invalid API key (401)"
      429 -> "Rate limited (429)"
      400 -> "Request rejected (400)"
      else -> "HTTP ${response.code()}"
    }
  }

  companion object {
    const val MODEL = "claude-haiku-4-5-20251001"
    // Pinned dated snapshot of Claude Haiku 4.5 (alias "claude-haiku-4-5"). Confirmed current and
    // Active via the official Anthropic model catalog/docs (2026-06-24); owner decision E7-D2.
    const val MAX_TOKENS = 1024
    const val SYSTEM_PROMPT =
        "You summarize Hacker News comment threads. Produce a concise, " +
            "neutral summary of the main points, the areas of agreement and disagreement, and any " +
            "notable insights or corrections. Prefer short paragraphs or bullet points. Do not invent " +
            "information that is not present in the comments."
  }
}
