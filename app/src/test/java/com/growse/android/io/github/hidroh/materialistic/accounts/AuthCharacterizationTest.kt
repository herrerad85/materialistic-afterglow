/*
 * Copyright (c) 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.accounts

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.growse.android.io.github.hidroh.materialistic.AppUtils
import com.growse.android.io.github.hidroh.materialistic.BuildConfig
import com.growse.android.io.github.hidroh.materialistic.Preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Robolectric characterization of the logout/auth gate: account actions (vote/reply/submit) proceed
 * only while [AppUtils.getCredentials] returns a non-null pair, which it does only when
 * [Preferences.getUsername] matches a saved AccountManager account.
 *
 * Logging out clears the username preference; the saved account itself remains in AccountManager
 * but getCredentials now returns null, so the gate closes.
 */
@RunWith(RobolectricTestRunner::class)
class AuthCharacterizationTest {

  private lateinit var context: Context
  private lateinit var accountManager: AccountManager
  private val accountType = BuildConfig.APPLICATION_ID
  private val account = Account("pg", accountType)

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    Preferences.reset(context)
    accountManager = AccountManager.get(context)
    // Seed a saved account of the app's account type, as a real login would.
    shadowOf(accountManager).addAccount(account)
    accountManager.setPassword(account, "s3cret")
  }

  @Test
  fun loggedIn_getCredentialsReturnsUsernameAndPassword() {
    Preferences.setUsername(context, "pg")

    val credentials = AppUtils.getCredentials(context)

    assertEquals("pg", credentials!!.first)
    assertEquals("s3cret", credentials.second)
  }

  @Test
  fun loggedOut_getCredentialsNullButAccountRemains() {
    Preferences.setUsername(context, "pg")
    assertEquals("pg", AppUtils.getCredentials(context)!!.first)

    // Log out: clear the username preference (as AppUtils.registerAccountsUpdatedListener does).
    Preferences.setUsername(context, null)

    // Account actions cannot proceed: no credentials.
    assertNull(AppUtils.getCredentials(context))

    // But the saved account is still present in AccountManager for re-login.
    val accounts = accountManager.getAccountsByType(accountType)
    assertEquals(1, accounts.size)
    assertEquals("pg", accounts[0].name)
  }

  @Test
  fun usernameWithoutMatchingAccount_getCredentialsNull() {
    // A username preference that does not match any saved account yields no credentials.
    Preferences.setUsername(context, "someone-else")

    assertNull(AppUtils.getCredentials(context))
  }
}
