/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

/**
 * Decides whether to surface the one-time "saving does not download" hint (#67).
 *
 * Saving a story keeps it in Saved Stories but does not download the article or comments unless
 * "Save for offline reading" (`pref_saved_item_sync`) is on, so a user who saves expecting an
 * offline copy gets none. This shows a single, low-noise hint the first time someone saves while
 * that sync is off; it never fires on a remove, never when sync is already on (downloads already
 * happen), and never again once shown. Pure so the policy is unit-tested without a fragment or
 * SharedPreferences.
 */
object OfflineSaveHint {
  @JvmStatic
  fun shouldShow(wasAdded: Boolean, syncEnabled: Boolean, hintShown: Boolean): Boolean =
      wasAdded && !syncEnabled && !hintShown
}
