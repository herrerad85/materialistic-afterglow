/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException

/**
 * The reply-notification background poll (E5-D7 G2). A thin [CoroutineWorker] that delegates to the
 * testable [ReplyPoller] and maps the outcome to a WorkManager result; all logic lives in the
 * poller. @HiltWorker so the poller (and its dependency graph) is injected; the app's
 * [androidx.work.Configuration.Provider] + [androidx.hilt.work.HiltWorkerFactory] supply it.
 *
 * On a data-source fetch failure the poller throws; we map that to [Result.retry] so WorkManager
 * backs off and re-runs rather than silently dropping the poll (E5-D3: distinguish empty-window
 * from fetch error).
 */
@HiltWorker
class ReplyNotificationWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val poller: ReplyPoller,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result =
      try {
        poller.pollOnce()
        Result.success()
      } catch (e: CancellationException) {
        // The worker was stopped: let cancellation propagate, don't turn it into a retry.
        throw e
      } catch (_: Exception) {
        Result.retry()
      }
}
