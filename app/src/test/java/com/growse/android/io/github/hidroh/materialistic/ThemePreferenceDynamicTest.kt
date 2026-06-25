/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

import com.google.android.material.color.DynamicColors
import com.growse.android.io.github.hidroh.materialistic.preference.ThemePreference
import com.growse.android.io.github.hidroh.materialistic.preference.ThemePreference.DayNightSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pure (device-free) characterization of how [ThemePreference.getTheme] resolves the "dynamic"
 * (Material You) value, exercising the persisted-but-unavailable normalization that the API-36
 * device QA cannot reach (DynamicColors IS available there). On API 30 [DynamicColors] reports
 * itself unavailable (it short-circuits below API 31), so a persisted "dynamic" must fall back to a
 * non-dynamic spec rather than leave an invisible selected state.
 */
@RunWith(RobolectricTestRunner::class)
class ThemePreferenceDynamicTest {

  @Test
  @Config(sdk = [30])
  fun persistedDynamic_whenUnavailable_fallsBackToNonDynamicLight() {
    // Guard: this assertion only means something while dynamic colour is genuinely unavailable.
    assumeFalse(DynamicColors.isDynamicColorAvailable())

    val spec = ThemePreference.getTheme("dynamic", false)
    // Normalized away from dynamic so apply() never tries to overlay an unsupported palette and the
    // picker summary stays honest (no invisible selected state).
    assertFalse("unavailable dynamic must not resolve to a dynamic spec", spec.dynamic)
    // The fallback is the light DayNight theme.
    assertTrue(spec is DayNightSpec)
    assertEquals(R.string.theme_light, summaryOf(spec))
  }

  @Test
  @Config(sdk = [30])
  fun persistedDynamicTranslucent_whenUnavailable_isNotDynamic() {
    assumeFalse(DynamicColors.isDynamicColorAvailable())

    val spec = ThemePreference.getTheme("dynamic", true)
    assertFalse(spec.dynamic)
  }

  @Test
  fun persistedDynamic_whenAvailable_resolvesToDynamicSpec() {
    // The default Robolectric SDK follows targetSdk (36), where DynamicColors is available.
    assumeTrue(DynamicColors.isDynamicColorAvailable())

    val spec = ThemePreference.getTheme("dynamic", false)
    assertTrue("available dynamic must resolve to a dynamic spec", spec.dynamic)
    // Dynamic extends DayNightSpec so day-night follow comes for free.
    assertTrue(spec is DayNightSpec)
  }

  @Test
  fun persistedDynamicTranslucent_whenAvailable_staysDynamic() {
    assumeTrue(DynamicColors.isDynamicColorAvailable())

    // The translucent variant (UserActivity path) must not silently revert to a static theme.
    val spec = ThemePreference.getTheme("dynamic", true)
    assertTrue("translucent dynamic must stay dynamic", spec.dynamic)
  }

  @Test
  fun unknownValue_fallsBackToNonDynamicLight() {
    val spec = ThemePreference.getTheme("not_a_real_theme", false)
    assertFalse(spec.dynamic)
    assertTrue(spec is DayNightSpec)
    assertEquals(R.string.theme_light, summaryOf(spec))
  }

  // summary is package-private on ThemeSpec; read it reflectively to avoid widening visibility.
  private fun summaryOf(spec: ThemePreference.ThemeSpec): Int {
    val field = ThemePreference.ThemeSpec::class.java.getDeclaredField("summary")
    field.isAccessible = true
    return field.getInt(spec)
  }
}
