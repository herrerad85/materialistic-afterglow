/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

import android.content.Context
import com.growse.android.io.github.hidroh.materialistic.Preferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The reply-notifications opt-in read (E5-D3), behind an interface so the poller and scheduler can
 * be tested without Robolectric. The default reads the single [Preferences] toggle (default OFF).
 */
fun interface ReplyNotificationToggle {
  fun isEnabled(): Boolean
}

/** [ReplyNotificationToggle] backed by the [Preferences] reply-notifications switch. */
class PreferencesReplyNotificationToggle
@Inject
constructor(@ApplicationContext private val context: Context) : ReplyNotificationToggle {
  override fun isEnabled(): Boolean = Preferences.isReplyNotificationsEnabled(context)
}
