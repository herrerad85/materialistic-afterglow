/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * E7 G4: composes the two G3 pieces for the "Summarize thread" sheet, mirroring
 * [ com.growse.android.io.github.hidroh.materialistic.StoryListViewModel]: `@HiltViewModel` + a
 * single sealed [SummaryUiState] over a [StateFlow]. It assembles the thread text
 * ([ThreadTextAssembler]) then summarizes it ([LlmSummaryProvider]); any failure becomes one
 * [SummaryUiState.Error] so the UI renders a single error state, not a leaking exception.
 *
 * Scoped to the bottom sheet via `by viewModels()`, so dismissing the sheet cancels viewModelScope
 * and the in-flight request (cancel-on-dismiss, D5). [SummaryUiState] survives config change.
 */
@HiltViewModel
class SummaryViewModel
@Inject
constructor(
    private val assembler: ThreadTextAssembler,
    private val provider: LlmSummaryProvider,
) : ViewModel() {

  private val _uiState = MutableStateFlow<SummaryUiState>(SummaryUiState.Loading)
  val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

  // One summarize per VM instance: retained VM + StateFlow survive config change, so the sheet
  // re-collects the last result instead of re-calling the (paid) API on rotation.
  private var started = false

  fun summarize(rootItemId: String) {
    if (started) return
    started = true
    viewModelScope.launch {
      _uiState.value = SummaryUiState.Loading
      val next =
          try {
            val assembled = assembler.assemble(rootItemId)
            val summary = provider.summarize(assembled.text)
            SummaryUiState.Success(summary, assembled.disclosure)
          } catch (e: CancellationException) {
            // Sheet dismissed mid-flight: let cancellation propagate, do not flash an error.
            throw e
          } catch (e: LlmSummaryException) {
            // Provider messages are user-facing by design (invalid key, rate limited, etc.).
            SummaryUiState.Error(e.message)
          } catch (e: Exception) {
            // Assembly / unexpected failures carry internal text; fall back to a generic message.
            SummaryUiState.Error(null)
          }
      _uiState.value = next
    }
  }
}

/** The single rendered state of the summary sheet. */
sealed interface SummaryUiState {
  data object Loading : SummaryUiState

  /** [disclosure] is the assembler's "top N of M" line (D6: no silent truncation). */
  data class Success(val summary: String, val disclosure: String) : SummaryUiState

  /** [message] is a user-facing provider message, or null to render a generic error string. */
  data class Error(val message: String?) : SummaryUiState
}
