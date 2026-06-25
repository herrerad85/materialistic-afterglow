/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The bound [LlmSummaryProvider] (E7 provider-selectable). Reads the user's selected provider per
 * call via [AiProviderSelector] and forwards to the matching concrete impl, so switching providers
 * in settings takes effect on the next summary without rebuilding the graph. Gemini is the default
 * and recommended free path; Anthropic is kept but unselected by default.
 */
@Singleton
class SelectableSummaryProvider
@Inject
constructor(
    private val selector: AiProviderSelector,
    private val gemini: GeminiSummaryProvider,
    private val anthropic: AnthropicSummaryProvider,
) : LlmSummaryProvider {

  override suspend fun summarize(threadText: String): String =
      when (selector.current()) {
        AiProviderIds.ANTHROPIC -> anthropic.summarize(threadText)
        // Gemini is the default for any other/unknown value.
        else -> gemini.summarize(threadText)
      }
}

/**
 * Returns the currently selected provider id (one of [AiProviderIds]). A fun interface so the
 * router stays pure and unit-testable; the production binding reads it from preferences.
 */
fun interface AiProviderSelector {
  fun current(): String
}

/** Stored provider id values; must match the ListPreference entryValues in preferences_ai.xml. */
object AiProviderIds {
  const val GEMINI = "gemini"
  const val ANTHROPIC = "anthropic"
}
