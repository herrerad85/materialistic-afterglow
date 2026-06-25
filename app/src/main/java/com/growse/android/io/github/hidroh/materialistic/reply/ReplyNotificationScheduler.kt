/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.growse.android.io.github.hidroh.materialistic.accounts.AccountSession
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single place that enqueues or cancels reply-poll work (E5-D3). Callers (app start, login,
 * logout, toggle change) only call [reconcile] / [cancel] / the convenience routers; they never
 * touch WorkManager directly, so the enqueue policy lives in exactly one spot.
 *
 * [reconcile] is the source of truth: when the feature is on and a session is active it
 * (re)enqueues a fixed 30-minute periodic poll (KEEP, so an existing schedule is preserved) plus a
 * one-time seed so a freshly enabled feature seeds promptly instead of waiting up to 30 minutes;
 * otherwise it cancels both. The only network constraint is CONNECTED (E5-D3) so Doze can batch.
 */
@Singleton
class ReplyNotificationScheduler
@Inject
constructor(
    private val workManager: WorkManager,
    private val toggle: ReplyNotificationToggle,
    private val accountSession: AccountSession,
) {

  /**
   * Bring scheduled work in line with the current toggle + session state. Idempotent: safe to call
   * from app start on every launch, and from each lifecycle hook.
   */
  fun reconcile() {
    if (toggle.isEnabled() && accountSession.activeUsername != null) {
      val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

      val periodic =
          PeriodicWorkRequestBuilder<ReplyNotificationWorker>(
                  POLL_INTERVAL_MINUTES,
                  TimeUnit.MINUTES,
              )
              .setConstraints(constraints)
              .build()
      workManager.enqueueUniquePeriodicWork(
          UNIQUE_PERIODIC,
          ExistingPeriodicWorkPolicy.KEEP,
          periodic,
      )

      val seed =
          OneTimeWorkRequestBuilder<ReplyNotificationWorker>().setConstraints(constraints).build()
      workManager.enqueueUniqueWork(UNIQUE_SEED, ExistingWorkPolicy.KEEP, seed)
    } else {
      cancel()
    }
  }

  /** Cancel both the periodic poll and any pending seed (toggle-off / logout). */
  fun cancel() {
    workManager.cancelUniqueWork(UNIQUE_PERIODIC)
    workManager.cancelUniqueWork(UNIQUE_SEED)
  }

  /** Login / account activation: a new active session may now need polling. */
  fun onLogin() = reconcile()

  /** Logout: no active session, so stop polling. */
  fun onLogout() = cancel()

  /** The reply-notifications switch flipped (G3 wires the UI to this). */
  fun onToggleChanged() = reconcile()

  companion object {
    const val UNIQUE_PERIODIC = "reply_notifications_periodic"
    const val UNIQUE_SEED = "reply_notifications_seed"
    /** Fixed cadence (E5-D3); WorkManager's periodic minimum is 15 min, this is well above it. */
    const val POLL_INTERVAL_MINUTES = 30L
  }
}
