/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

import android.content.DialogInterface
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.growse.android.io.github.hidroh.materialistic.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog

/**
 * Robolectric characterization of [AiPreferenceController] (E7-D4/D5 G2). Exercises the real
 * [attach] wiring against an inflated `preferences_ai.xml` screen, with an in-test
 * [FakeSecretStore] supplied via the internal constructor (no Hilt component, no Keystore/Tink, no
 * network).
 *
 * The load-bearing security guarantee: the plaintext API key NEVER lands in SharedPreferences. The
 * [EditTextPreference] is non-persistent and routes the entered value to the [SecretStore] only.
 */
@RunWith(RobolectricTestRunner::class)
class AiPreferenceControllerTest {

  private lateinit var controllerActivity: ActivityController<HostActivity>
  private lateinit var fragment: PreferenceFragmentCompat
  private lateinit var fakeStore: FakeSecretStore

  @Before
  fun setUp() {
    fakeStore = FakeSecretStore()
    controllerActivity = Robolectric.buildActivity(HostActivity::class.java).setup()
    fragment = controllerActivity.get().fragment
    // Clear any prior state so consent starts un-accepted and no key entry is persisted.
    PreferenceManager.getDefaultSharedPreferences(fragment.requireContext()).edit().clear().apply()
    AiPreferenceController(fragment) { fakeStore }.attach()
  }

  private fun switchPref(): SwitchPreferenceCompat =
      fragment.findPreference<SwitchPreferenceCompat>(
          fragment.getString(R.string.pref_ai_summaries_enabled)
      )!!

  private fun keyPref(): EditTextPreference =
      fragment.findPreference<EditTextPreference>(fragment.getString(R.string.pref_ai_api_key))!!

  private fun sharedPrefs() =
      PreferenceManager.getDefaultSharedPreferences(fragment.requireContext())

  private fun consentAccepted(): Boolean =
      sharedPrefs().getBoolean(fragment.getString(R.string.pref_ai_consent_accepted), false)

  @Test
  fun toggleOn_withoutPriorConsent_showsConsentAndRevertsOnCancel() {
    val switch = switchPref()

    // The listener returns false on the first ON (consent not yet accepted), so the framework does
    // not commit ON; the dialog gates it.
    val accepted = switch.onPreferenceChangeListener!!.onPreferenceChange(switch, true)
    assertFalse("first ON must be gated by consent (listener returns false)", accepted)

    val dialog = ShadowDialog.getLatestDialog() as AlertDialog
    assertNotNull("consent dialog must be shown", dialog)
    assertTrue(dialog.isShowing)

    // Cancel -> stay OFF, consent NOT recorded. Idle the main looper so the AppCompat AlertDialog
    // button handler dispatches the click callback (it routes through a main-looper Handler).
    dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
    shadowOf(Looper.getMainLooper()).idle()
    assertFalse("switch must remain OFF after cancel", switch.isChecked)
    assertFalse("consent must not be recorded after cancel", consentAccepted())
  }

  @Test
  fun toggleOn_acceptConsent_persistsFlagAndLeavesSwitchOn() {
    val switch = switchPref()

    switch.onPreferenceChangeListener!!.onPreferenceChange(switch, true)
    val dialog = ShadowDialog.getLatestDialog() as AlertDialog
    dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
    // Idle the main looper so the AppCompat AlertDialog button handler dispatches the positive
    // callback (setConsentAccepted + switch ON) before we assert.
    shadowOf(Looper.getMainLooper()).idle()

    assertTrue("switch must be ON after accepting consent", switch.isChecked)
    assertTrue("consent flag must persist after accept", consentAccepted())
  }

  @Test
  fun toggleOn_afterConsentAccepted_allowsOnWithoutDialog() {
    // Pre-accept consent.
    sharedPrefs()
        .edit()
        .putBoolean(fragment.getString(R.string.pref_ai_consent_accepted), true)
        .apply()
    fakeStore.putApiKey("sk-already-set")
    // Re-attach so the switch listener observes the accepted-consent state.
    AiPreferenceController(fragment) { fakeStore }.attach()

    val switch = switchPref()
    val accepted = switch.onPreferenceChangeListener!!.onPreferenceChange(switch, true)

    assertTrue("ON must be allowed directly once consent is accepted", accepted)
  }

  @Test
  fun keyEntry_routesToSecretStore_andNeverPersistsToSharedPreferences() {
    val key = keyPref()
    val keyName = fragment.getString(R.string.pref_ai_api_key)

    val persisted = key.onPreferenceChangeListener!!.onPreferenceChange(key, "  sk-ant-secret  ")

    // Returning false is what stops the framework from persisting the plaintext value.
    assertFalse("change listener must return false so the framework does not persist", persisted)
    // The trimmed value reached the encrypted store.
    assertEquals("sk-ant-secret", fakeStore.getApiKey())
    // The plaintext key is NOT in SharedPreferences (the security-critical guarantee).
    assertFalse(
        "plaintext key must never be written to SharedPreferences",
        sharedPrefs().contains(keyName),
    )
    assertNull(sharedPrefs().getString(keyName, null))
    // The EditTextPreference is configured non-persistent.
    assertFalse("key preference must be non-persistent", key.isPersistent)
  }

  @Test
  fun keyEntry_blankInput_isIgnoredAndNotStored() {
    val key = keyPref()

    val persisted = key.onPreferenceChangeListener!!.onPreferenceChange(key, "   ")

    assertFalse(persisted)
    assertNull("blank input must not be stored", fakeStore.getApiKey())
  }

  @Test
  fun keySummary_reflectsPresence_neverTheValue() {
    val key = keyPref()
    // No key yet -> "Not set".
    assertEquals(fragment.getString(R.string.ai_key_not_set), key.summary)

    key.onPreferenceChangeListener!!.onPreferenceChange(key, "sk-ant-secret")

    val summary = key.summary?.toString().orEmpty()
    assertEquals(fragment.getString(R.string.ai_key_set), summary)
    assertFalse("summary must never reveal the key value", summary.contains("sk-ant-secret"))
  }

  /** Minimal AppCompat host that inflates the real `preferences_ai.xml` into a fragment. */
  class HostActivity : AppCompatActivity() {
    lateinit var fragment: AiPrefsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
      setTheme(R.style.AppTheme)
      super.onCreate(savedInstanceState)
      fragment = AiPrefsFragment()
      supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
    }
  }

  class AiPrefsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
      addPreferencesFromResource(R.xml.preferences_ai)
    }
  }
}
