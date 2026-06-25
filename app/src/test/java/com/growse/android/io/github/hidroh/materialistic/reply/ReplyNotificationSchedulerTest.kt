/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.growse.android.io.github.hidroh.materialistic.accounts.AccountSession
import com.growse.android.io.github.hidroh.materialistic.accounts.Credentials
import com.growse.android.io.github.hidroh.materialistic.accounts.SavedAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric characterization of [ReplyNotificationScheduler] against a real WorkManager (test
 * driver). Asserts E5-D3: reconcile with toggle ON + session enqueues the unique periodic AND seed
 * work; reconcile with the toggle OFF cancels both; cancel() cancels both.
 */
@RunWith(RobolectricTestRunner::class)
class ReplyNotificationSchedulerTest {

  private lateinit var workManager: WorkManager
  private val toggle = SettableToggle(enabled = true)
  private val session = FakeAccountSession(username = "me")

  @Before
  fun setUp() {
    val context: Context = ApplicationProvider.getApplicationContext()
    WorkManagerTestInitHelper.initializeTestWorkManager(
        context,
        Configuration.Builder().build(),
    )
    workManager = WorkManager.getInstance(context)
  }

  private fun scheduler(): ReplyNotificationScheduler =
      ReplyNotificationScheduler(workManager, toggle, session)

  private fun isQueued(uniqueName: String): Boolean {
    val infos = workManager.getWorkInfosForUniqueWork(uniqueName).get()
    return infos.any {
      it.state != WorkInfo.State.CANCELLED && it.state != WorkInfo.State.SUCCEEDED
    }
  }

  @Test
  fun reconcile_onWithSession_enqueuesPeriodicAndSeed() {
    scheduler().reconcile()

    assertTrue(isQueued(ReplyNotificationScheduler.UNIQUE_PERIODIC))
    assertTrue(isQueued(ReplyNotificationScheduler.UNIQUE_SEED))
  }

  @Test
  fun reconcile_toggleOff_cancelsBoth() {
    scheduler().reconcile()
    assertTrue(isQueued(ReplyNotificationScheduler.UNIQUE_PERIODIC))

    toggle.enabled = false
    scheduler().reconcile()

    assertEquals(
        WorkInfo.State.CANCELLED,
        workManager
            .getWorkInfosForUniqueWork(ReplyNotificationScheduler.UNIQUE_PERIODIC)
            .get()
            .single()
            .state,
    )
    assertEquals(
        WorkInfo.State.CANCELLED,
        workManager
            .getWorkInfosForUniqueWork(ReplyNotificationScheduler.UNIQUE_SEED)
            .get()
            .single()
            .state,
    )
  }

  @Test
  fun reconcile_noSession_cancelsBoth() {
    scheduler().reconcile()
    assertTrue(isQueued(ReplyNotificationScheduler.UNIQUE_PERIODIC))

    session.username = null
    scheduler().reconcile()

    assertEquals(
        WorkInfo.State.CANCELLED,
        workManager
            .getWorkInfosForUniqueWork(ReplyNotificationScheduler.UNIQUE_PERIODIC)
            .get()
            .single()
            .state,
    )
  }

  @Test
  fun cancel_cancelsBoth() {
    scheduler().reconcile()

    scheduler().cancel()

    assertEquals(
        WorkInfo.State.CANCELLED,
        workManager
            .getWorkInfosForUniqueWork(ReplyNotificationScheduler.UNIQUE_PERIODIC)
            .get()
            .single()
            .state,
    )
    assertEquals(
        WorkInfo.State.CANCELLED,
        workManager
            .getWorkInfosForUniqueWork(ReplyNotificationScheduler.UNIQUE_SEED)
            .get()
            .single()
            .state,
    )
  }

  // --- fakes -------------------------------------------------------------------------------------

  private class SettableToggle(var enabled: Boolean) : ReplyNotificationToggle {
    override fun isEnabled(): Boolean = enabled
  }

  private class FakeAccountSession(var username: String?) : AccountSession {
    override val activeUsername: String?
      get() = username

    override fun credentials(): Credentials? = username?.let { Credentials(it, "pw") }

    override fun savedAccounts(): List<SavedAccount> =
        username?.let { listOf(SavedAccount(it)) } ?: emptyList()

    override fun signIn(username: String, password: String) {
      this.username = username
    }

    override fun setActive(username: String) {
      this.username = username
    }

    override fun logout() {
      username = null
    }

    override fun purgeLegacySyncAccount() {}

    override fun removeAccount(username: String) {
      if (this.username == username) this.username = null
    }

    override fun startAccountMonitor() = Unit
  }
}
