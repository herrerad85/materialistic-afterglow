/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.accounts

/** A saved Hacker News account, identified by its username. Hides android.accounts.Account. */
data class SavedAccount(val username: String)

/**
 * The active Hacker News account session: which saved account is signed in, the saved-account
 * roster, and the operations over it. The single seam over the active username
 * ([android.content.SharedPreferences] via Preferences) and the stored credentials (Android
 * AccountManager); neither leaks to callers.
 */
interface AccountSession {
  /** Active username, or null when logged out or stale (no matching saved account). */
  val activeUsername: String?

  /** Credentials for the active session, or null when logged out/stale. Synchronous local read. */
  fun credentials(): Credentials?

  /** Saved accounts available to the account chooser. */
  fun savedAccounts(): List<SavedAccount>

  /** Store credentials for [username] and make it the active session (after HN verifies login). */
  fun signIn(username: String, password: String)

  /** Make an already-saved account the active session (account chooser). */
  fun setActive(username: String)

  /** Clear the active session; keep the saved accounts (Logout). */
  fun logout()

  /** Delete a saved account's stored credentials (Remove account). */
  fun removeAccount(username: String)

  /** Begin proactive cleanup: clear the active username if its saved account is removed. */
  fun startAccountMonitor()

  /**
   * Remove the legacy offline-sync account left behind by the deleted SyncAdapter path (Slice 9
   * G2). Only an exact, safely-distinguishable legacy account is removed; real login accounts are
   * never touched. Idempotent (a no-op once the account is gone).
   */
  fun purgeLegacySyncAccount()
}
