/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.accounts

import android.accounts.AccountManager
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.growse.android.io.github.hidroh.materialistic.Preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Robolectric tests for [AndroidAccountSession] against a real shadowed AccountManager. */
@RunWith(RobolectricTestRunner::class)
class AccountSessionTest {

  private lateinit var context: Context
  private lateinit var accountManager: AccountManager
  private lateinit var session: AccountSession

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    Preferences.reset(context)
    accountManager = AccountManager.get(context)
    session = AndroidAccountSession(context)
  }

  @Test
  fun signIn_storesCredentialsAndActivates() {
    session.signIn("pg", "s3cret")

    assertEquals("pg", session.activeUsername)
    assertEquals(Credentials("pg", "s3cret"), session.credentials())
    assertEquals(listOf(SavedAccount("pg")), session.savedAccounts())
  }

  @Test
  fun signIn_existingAccountUpdatesPassword() {
    session.signIn("pg", "old")
    session.signIn("pg", "new")

    assertEquals(Credentials("pg", "new"), session.credentials())
    assertEquals(1, session.savedAccounts().size)
  }

  @Test
  fun setActive_switchesAmongSavedAccounts() {
    session.signIn("pg", "p1")
    session.signIn("dang", "p2") // signIn also activates dang
    assertEquals("dang", session.activeUsername)

    session.setActive("pg")

    assertEquals("pg", session.activeUsername)
    assertEquals(Credentials("pg", "p1"), session.credentials())
  }

  @Test
  fun logout_clearsActiveButKeepsSavedAccount() {
    session.signIn("pg", "s3cret")

    session.logout()

    assertNull(session.activeUsername)
    assertNull(session.credentials())
    assertEquals(listOf(SavedAccount("pg")), session.savedAccounts())
  }

  @Test
  fun removeAccount_deletesCredentialsAndClearsActive() {
    session.signIn("pg", "s3cret")

    session.removeAccount("pg")

    assertTrue(session.savedAccounts().isEmpty())
    assertNull(session.activeUsername)
    assertNull(session.credentials())
  }

  @Test
  fun activeUsername_nullWhenStale() {
    Preferences.setUsername(context, "ghost") // no matching saved account

    assertNull(session.activeUsername)
    assertNull(session.credentials())
  }

  @Test
  fun accountMonitor_clearsStaleActiveUsername() {
    // Active username with no matching saved account; the monitor's immediate pass clears it.
    Preferences.setUsername(context, "ghost")

    session.startAccountMonitor()
    shadowOf(Looper.getMainLooper()).idle()

    assertTrue(Preferences.getUsername(context).isEmpty())
    assertNull(session.activeUsername)
  }
}
