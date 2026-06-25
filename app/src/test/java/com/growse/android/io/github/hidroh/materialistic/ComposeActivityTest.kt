/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.growse.android.io.github.hidroh.materialistic.accounts.UserServices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A failed/auth-rejected reply preserves the saved draft; only a successful send clears it. */
@RunWith(RobolectricTestRunner::class)
class ComposeActivityTest {

  private val parentId = "42"
  private lateinit var context: Context
  private lateinit var activity: ComposeActivity
  private lateinit var callback: UserServices.Callback

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    // A bare ComposeActivity subclass (no Hilt, no mockk inline-instrumentation): supplies the app
    // context the callback writes drafts against, and reports itself destroyed so onDone never
    // dereferences the un-injected UI in onSent.
    activity =
        object : ComposeActivity() {
          override fun getApplicationContext(): Context = context

          override fun isActivityDestroyed(): Boolean = true
        }
    callback = ComposeActivity.ComposeCallback(activity, parentId)
    Preferences.saveDraft(context, parentId, "body")
  }

  @Test
  fun onDone_failed_keepsDraft() {
    callback.onDone(false)
    assertEquals("body", Preferences.getDraft(context, parentId))
  }

  @Test
  fun onDone_successful_deletesDraft() {
    callback.onDone(true)
    assertNull(Preferences.getDraft(context, parentId))
  }

  @Test
  fun onError_keepsDraft() {
    callback.onError(null)
    assertEquals("body", Preferences.getDraft(context, parentId))
  }
}
