/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.accounts

import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.content.Context
import com.growse.android.io.github.hidroh.materialistic.Preferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AccountSession] backed by Android [AccountManager] (credentials) and [Preferences] (active
 * username). Touches only the app's own account type, so no GET_ACCOUNTS permission is required.
 */
@Singleton
@SuppressLint("MissingPermission")
class AndroidAccountSession @Inject constructor(@ApplicationContext private val context: Context) :
    AccountSession {

  // The authenticator-owned account type (authenticator.xml), NOT the rebrandable applicationId.
  private val accountType: String = AccountAuthenticator.ACCOUNT_TYPE

  private val accountManager: AccountManager
    get() = AccountManager.get(context)

  override val activeUsername: String?
    get() {
      val username = Preferences.getUsername(context)
      // Pull-on-read: never report an active account that no longer has saved credentials.
      return if (username.isNotEmpty() && savedNames().contains(username)) username else null
    }

  override fun credentials(): Credentials? {
    val username = activeUsername ?: return null
    val account = androidAccounts().firstOrNull { it.name == username } ?: return null
    val password = accountManager.getPassword(account) ?: return null
    return Credentials(username, password)
  }

  override fun savedAccounts(): List<SavedAccount> = savedNames().map(::SavedAccount)

  override fun signIn(username: String, password: String) {
    val account = Account(username, accountType)
    // addAccountExplicitly returns false if the account already exists; update its password then.
    if (!accountManager.addAccountExplicitly(account, password, null)) {
      accountManager.setPassword(account, password)
    }
    Preferences.setUsername(context, username)
  }

  override fun setActive(username: String) {
    Preferences.setUsername(context, username)
  }

  override fun logout() {
    // Clear the active session; saved accounts remain for re-login.
    Preferences.setUsername(context, null)
  }

  override fun removeAccount(username: String) {
    androidAccounts()
        .firstOrNull { it.name == username }
        ?.let { accountManager.removeAccountExplicitly(it) }
    if (Preferences.getUsername(context) == username) {
      Preferences.setUsername(context, null)
    }
  }

  override fun startAccountMonitor() {
    accountManager.addOnAccountsUpdatedListener(
        { accounts ->
          val username = Preferences.getUsername(context)
          if (
              username.isNotEmpty() &&
                  accounts.none { it.name == username && it.type == accountType }
          ) {
            Preferences.setUsername(context, null)
          }
        },
        null,
        true,
    )
  }

  override fun purgeLegacySyncAccount() {
    // The deleted SyncAdapter path created this account with addAccountExplicitly(.., null, null),
    // so
    // a null password under the exact legacy name uniquely identifies it. signIn() always stores a
    // real password, so a real HN login (even one literally named "Materialistic") never matches
    // and
    // is never removed.
    androidAccounts()
        .firstOrNull {
          it.name == LEGACY_SYNC_ACCOUNT_NAME && accountManager.getPassword(it) == null
        }
        ?.let { accountManager.removeAccountExplicitly(it) }
  }

  private fun androidAccounts(): Array<Account> = accountManager.getAccountsByType(accountType)

  private fun savedNames(): List<String> = androidAccounts().map(Account::name)

  private companion object {
    // The legacy offline-sync account name (was SyncDelegate.SYNC_ACCOUNT_NAME, deleted in G2).
    const val LEGACY_SYNC_ACCOUNT_NAME = "Materialistic"
  }
}
