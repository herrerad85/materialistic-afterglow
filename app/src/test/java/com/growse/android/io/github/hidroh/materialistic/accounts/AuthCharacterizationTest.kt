/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.accounts

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.growse.android.io.github.hidroh.materialistic.Preferences
import com.growse.android.io.github.hidroh.materialistic.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.xmlpull.v1.XmlPullParser

/**
 * Robolectric characterization of the logout/auth gate, now expressed through [AccountSession]:
 * account actions proceed only while [AccountSession.credentials] is non-null, which holds only
 * when the active username (Preferences) matches a saved AccountManager account.
 *
 * Logging out clears the active username; the saved account itself remains in AccountManager, but
 * credentials() then returns null, so the gate closes.
 */
@RunWith(RobolectricTestRunner::class)
class AuthCharacterizationTest {

  private lateinit var context: Context
  private lateinit var accountManager: AccountManager
  private lateinit var session: AccountSession
  private val accountType = AccountAuthenticator.ACCOUNT_TYPE
  private val account = Account("pg", accountType)

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    Preferences.reset(context)
    accountManager = AccountManager.get(context)
    // Seed a saved account of the app's account type, as a real login would.
    shadowOf(accountManager).addAccount(account)
    accountManager.setPassword(account, "s3cret")
    session = AndroidAccountSession(context)
  }

  @Test
  fun loggedIn_credentialsReturnUsernameAndPassword() {
    Preferences.setUsername(context, "pg")

    val credentials = session.credentials()

    assertEquals("pg", credentials!!.username)
    assertEquals("s3cret", credentials.password)
  }

  @Test
  fun loggedOut_credentialsNullButAccountRemains() {
    Preferences.setUsername(context, "pg")
    assertEquals("pg", session.credentials()!!.username)

    // Log out: clears the active username, keeps the saved account.
    session.logout()

    // Account actions cannot proceed: no credentials.
    assertNull(session.credentials())

    // But the saved account is still present in AccountManager for re-login.
    val accounts = accountManager.getAccountsByType(accountType)
    assertEquals(1, accounts.size)
    assertEquals("pg", accounts[0].name)
  }

  @Test
  fun usernameWithoutMatchingAccount_credentialsNull() {
    // A stale active username matching no saved account yields no credentials.
    Preferences.setUsername(context, "someone-else")

    assertNull(session.credentials())
  }

  @Test
  fun signIn_createsAccountUnderAuthenticatorType_andSurvivesPullOnRead() {
    session.signIn("alice", "pw")

    // Created under the authenticator-owned type (not applicationId), so the pull-on-read keeps it
    // and the active session resolves end to end.
    val saved = accountManager.getAccountsByType(AccountAuthenticator.ACCOUNT_TYPE)
    assertEquals(1, saved.count { it.name == "alice" })
    assertEquals("alice", session.activeUsername)
    assertEquals("pw", session.credentials()!!.password)
  }

  @Test
  fun accountType_matchesAuthenticatorXml() {
    // The real bug: the code account type diverged from what the authenticator declares, so
    // AccountManager could not match a saved account and login silently persisted nothing. Pin the
    // constant to the XML so a future applicationId/type drift fails here instead. (The syncadapter
    // XML was deleted with the dead SyncAdapter path in Slice 9 G2.)
    assertEquals(xmlAccountType(R.xml.authenticator), AccountAuthenticator.ACCOUNT_TYPE)
  }

  @Test
  fun purgeLegacySyncAccount_removesNullPasswordMaterialisticAccount() {
    // The deleted SyncAdapter path created this with a null password.
    shadowOf(accountManager).addAccount(Account("Materialistic", accountType))

    session.purgeLegacySyncAccount()

    assertEquals(
        0,
        accountManager.getAccountsByType(accountType).count { it.name == "Materialistic" },
    )
  }

  @Test
  fun purgeLegacySyncAccount_keepsRealLoginEvenIfNamedMaterialistic() {
    val realNamedLikeSync = Account("Materialistic", accountType)
    shadowOf(accountManager).addAccount(realNamedLikeSync)
    accountManager.setPassword(realNamedLikeSync, "s3cret") // a real login always sets a password

    session.purgeLegacySyncAccount()

    // Non-null password means it is not the legacy sync account, so it is preserved.
    assertEquals(
        1,
        accountManager.getAccountsByType(accountType).count { it.name == "Materialistic" },
    )
  }

  @Test
  fun purgeLegacySyncAccount_keepsRealLoginAccounts() {
    // The setUp() account "pg" (with password) must survive the purge.
    session.purgeLegacySyncAccount()

    assertEquals(1, accountManager.getAccountsByType(accountType).count { it.name == "pg" })
  }

  private fun xmlAccountType(xmlRes: Int): String {
    val parser = context.resources.getXml(xmlRes)
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      if (event == XmlPullParser.START_TAG) {
        parser.getAttributeValue(ANDROID_NS, "accountType")?.let {
          return it
        }
      }
      event = parser.next()
    }
    throw AssertionError("no android:accountType in xml resource")
  }

  private companion object {
    const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
  }
}
