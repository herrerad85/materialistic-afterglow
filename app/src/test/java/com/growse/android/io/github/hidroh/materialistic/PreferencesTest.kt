/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric characterization of [Preferences] read/write round-trips against a real
 * SharedPreferences (an Android Context is required for PreferenceManager + resource keys).
 */
@RunWith(RobolectricTestRunner::class)
class PreferencesTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    Preferences.reset(context)
  }

  @Test
  fun username_roundTripsAndClears() {
    // Default is empty before anything is written.
    assertEquals("", Preferences.getUsername(context))

    Preferences.setUsername(context, "pg")
    assertEquals("pg", Preferences.getUsername(context))

    Preferences.setUsername(context, null)
    assertEquals("", Preferences.getUsername(context))
  }

  @Test
  fun sortByRecent_roundTrips() {
    // Default value passed to get() is pref_search_sort_value_recent ("recent"), so
    // unset search sort defaults to recent == true.
    assertTrue(Preferences.isSortByRecent(context))

    Preferences.setSortByRecent(context, false)
    assertFalse(Preferences.isSortByRecent(context))

    Preferences.setSortByRecent(context, true)
    assertTrue(Preferences.isSortByRecent(context))
  }

  @Test
  fun colorCodeOpacity_defaultsTo100AndPersists() {
    assertEquals(100, Preferences.colorCodeOpacity(context))
  }

  @Test
  fun commentMaxLines_defaultIsTen() {
    // Default "10" parses to 10.
    assertEquals(10, Preferences.getCommentMaxLines(context))
  }

  @Test
  fun draft_roundTripsAndDeletes() {
    assertEquals(null, Preferences.getDraft(context, "42"))

    Preferences.saveDraft(context, "42", "hello world")
    assertEquals("hello world", Preferences.getDraft(context, "42"))

    Preferences.deleteDraft(context, "42")
    assertEquals(null, Preferences.getDraft(context, "42"))
  }
}
