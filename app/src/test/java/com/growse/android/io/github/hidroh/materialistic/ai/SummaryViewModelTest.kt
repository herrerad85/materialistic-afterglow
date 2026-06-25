/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deterministic unit tests for the E7 G4 [SummaryViewModel] (mirrors
 * [ com.growse.android.io.github.hidroh.materialistic.StoryListViewModelTest]). A
 * [StandardTestDispatcher] backs `Dispatchers.Main` (which `viewModelScope` uses) so
 * `advanceUntilIdle()` drains the launched summarize. The assembler and provider are mocked so no
 * network or key is involved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `summarize emits Success with summary and disclosure`() = runTest {
    val assembler = mockk<ThreadTextAssembler>()
    coEvery { assembler.assemble("1") } returns
        AssembledThread(text = "body", includedCount = 3, totalDescendants = 5, truncated = true)
    val provider = mockk<LlmSummaryProvider>()
    coEvery { provider.summarize("body") } returns "the summary"
    val vm = SummaryViewModel(assembler, provider)

    vm.summarize("1")
    advanceUntilIdle()

    val state = vm.uiState.value
    assertTrue("expected Success, was $state", state is SummaryUiState.Success)
    state as SummaryUiState.Success
    assertEquals("the summary", state.summary)
    assertEquals("Summarized the top 3 of 5 comments", state.disclosure)
  }

  @Test
  fun `provider failure surfaces its user-facing message`() = runTest {
    val assembler = mockk<ThreadTextAssembler>()
    coEvery { assembler.assemble(any()) } returns
        AssembledThread(text = "body", includedCount = 1, totalDescendants = 1, truncated = false)
    val provider = mockk<LlmSummaryProvider>()
    coEvery { provider.summarize(any()) } throws LlmSummaryException("Rate limited (429)")
    val vm = SummaryViewModel(assembler, provider)

    vm.summarize("1")
    advanceUntilIdle()

    val state = vm.uiState.value
    assertTrue("expected Error, was $state", state is SummaryUiState.Error)
    assertEquals("Rate limited (429)", (state as SummaryUiState.Error).message)
  }

  @Test
  fun `assembly failure yields a generic (null-message) error`() = runTest {
    val assembler = mockk<ThreadTextAssembler>()
    coEvery { assembler.assemble(any()) } throws ThreadAssemblyException("thread fetch failed: 1")
    val provider = mockk<LlmSummaryProvider>()
    val vm = SummaryViewModel(assembler, provider)

    vm.summarize("1")
    advanceUntilIdle()

    val state = vm.uiState.value
    assertTrue("expected Error, was $state", state is SummaryUiState.Error)
    // Internal assembler text must not leak to the UI; the sheet renders a generic string instead.
    assertNull((state as SummaryUiState.Error).message)
  }

  @Test
  fun `summarize is idempotent (does not re-call the API on a second invocation)`() = runTest {
    val assembler = mockk<ThreadTextAssembler>()
    coEvery { assembler.assemble("1") } returns
        AssembledThread(text = "body", includedCount = 1, totalDescendants = 1, truncated = false)
    val provider = mockk<LlmSummaryProvider>()
    coEvery { provider.summarize(any()) } returns "the summary"
    val vm = SummaryViewModel(assembler, provider)

    vm.summarize("1")
    vm.summarize("1")
    advanceUntilIdle()

    coVerify(exactly = 1) { assembler.assemble("1") }
    coVerify(exactly = 1) { provider.summarize(any()) }
  }
}
