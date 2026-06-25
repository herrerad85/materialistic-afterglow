/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.growse.android.io.github.hidroh.materialistic.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * E7 G4: the dismissible result sheet for "Summarize thread" (D5: one quiet sheet, no persistent
 * UI). Owns a [SummaryViewModel] scoped to itself, so dismissing the sheet cancels the in-flight
 * request. Renders the single [SummaryUiState]: a spinner while working, the summary plus the "top
 * N of M" disclosure on success, a copy action, or an error message.
 */
@AndroidEntryPoint
class SummaryBottomSheet : BottomSheetDialogFragment() {

  private val viewModel: SummaryViewModel by viewModels()

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View = inflater.inflate(R.layout.bottom_sheet_summary, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val progress = view.findViewById<ProgressBar>(R.id.summary_progress)
    val summaryText = view.findViewById<TextView>(R.id.summary_text)
    val disclosure = view.findViewById<TextView>(R.id.summary_disclosure)
    val copyButton = view.findViewById<View>(R.id.summary_copy)
    // Close also serves as cancel: dismissing destroys this fragment and cancels viewModelScope.
    view.findViewById<View>(R.id.summary_close).setOnClickListener { dismiss() }

    val rootItemId = requireArguments().getString(ARG_ITEM_ID)
    if (rootItemId.isNullOrEmpty()) {
      dismiss()
      return
    }
    viewModel.summarize(rootItemId)

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect { state ->
          when (state) {
            SummaryUiState.Loading -> {
              progress.visibility = View.VISIBLE
              summaryText.visibility = View.GONE
              disclosure.visibility = View.GONE
              copyButton.visibility = View.GONE
            }
            is SummaryUiState.Success -> {
              progress.visibility = View.GONE
              summaryText.visibility = View.VISIBLE
              summaryText.text = state.summary
              disclosure.visibility = View.VISIBLE
              disclosure.text = state.disclosure
              copyButton.visibility = View.VISIBLE
              copyButton.setOnClickListener { copyToClipboard(state.summary) }
            }
            is SummaryUiState.Error -> {
              progress.visibility = View.GONE
              summaryText.visibility = View.VISIBLE
              summaryText.text = state.message ?: getString(R.string.ai_summary_error)
              disclosure.visibility = View.GONE
              copyButton.visibility = View.GONE
            }
          }
        }
      }
    }
  }

  private fun copyToClipboard(summary: String) {
    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.summarize_thread), summary))
    Toast.makeText(requireContext(), R.string.summary_copied, Toast.LENGTH_SHORT).show()
  }

  companion object {
    private const val ARG_ITEM_ID = "arg:itemId"

    @JvmStatic
    fun newInstance(itemId: String): SummaryBottomSheet =
        SummaryBottomSheet().apply { arguments = Bundle().apply { putString(ARG_ITEM_ID, itemId) } }
  }
}
