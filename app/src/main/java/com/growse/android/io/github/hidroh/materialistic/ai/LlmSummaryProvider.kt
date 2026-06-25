/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

/**
 * The seam over the LLM summarizer (E7 G3). Takes already-assembled thread text (the thread-text
 * assembler is a separate component, composed by the G4 ViewModel) and returns the summary text.
 * Only plain types cross this boundary so it stays fakeable without Retrofit, Gson, or a real key:
 * the live implementations ([GeminiSummaryProvider], the default, and [AnthropicSummaryProvider])
 * each talk to a dedicated client and are chosen per call by [SelectableSummaryProvider], while
 * tests bind a fake. Any failure (no key, HTTP error, empty body, network) surfaces as a typed
 * [LlmSummaryException] so the G4 UI can render a single error state rather than catch IOException
 * / HttpException leaking from the network layer.
 */
interface LlmSummaryProvider {

  /**
   * Summarize the given assembled thread text. Suspends on the IO dispatcher inside the
   * implementation. Throws [LlmSummaryException] on any failure; never returns a blank string.
   */
  suspend fun summarize(threadText: String): String
}

/** A summarization failure (no key, HTTP error, empty body, or network). */
class LlmSummaryException(message: String, cause: Throwable? = null) : Exception(message, cause)
