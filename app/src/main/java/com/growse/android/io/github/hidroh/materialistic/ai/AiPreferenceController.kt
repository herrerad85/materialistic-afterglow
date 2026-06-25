/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

import android.text.InputType
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.snackbar.Snackbar
import com.growse.android.io.github.hidroh.materialistic.R
import dagger.hilt.android.EntryPointAccessors

/**
 * Scoped controller for the AI-summaries settings screen (E7-D4 / E7-D5 G2). The generic Java
 * `SettingsFragment` attaches one of these only when the AI summaries switch is on the loaded
 * screen, so this never runs on preference screens without it.
 *
 * [attach] must be called during the fragment's onCreate (after the prefs inflate), so the switch
 * and key preference already exist.
 *
 * Privacy/security posture (E7-D4):
 * - The switch is OFF by default; turning it ON the first time shows a one-time consent dialog that
 *   discloses the egress of thread comments to the user's chosen provider. Declining reverts to
 *   OFF.
 * - The BYO API key NEVER persists to SharedPreferences: the [EditTextPreference] is
 *   non-persistent, masked as a password, and every change is routed to the encrypted
 *   [SecretStore]. The key's summary only ever reflects presence, never the value.
 */
class AiPreferenceController
internal constructor(
    private val fragment: PreferenceFragmentCompat,
    private val secretStoreProvider: () -> SecretStore,
) {

  constructor(
      fragment: PreferenceFragmentCompat
  ) : this(
      fragment,
      {
        EntryPointAccessors.fromApplication(
                fragment.requireContext().applicationContext,
                AiSecretEntryPoint::class.java,
            )
            .secretStore()
      },
  )

  fun attach() {
    val secretStore = secretStoreProvider()

    val switch =
        fragment.findPreference<SwitchPreferenceCompat>(
            fragment.getString(R.string.pref_ai_summaries_enabled)
        ) ?: return

    val keyPref =
        fragment.findPreference<EditTextPreference>(fragment.getString(R.string.pref_ai_api_key))
            ?: return

    attachSwitch(switch, secretStore)
    attachKeyEntry(keyPref, secretStore)
  }

  private fun attachSwitch(switch: SwitchPreferenceCompat, secretStore: SecretStore) {
    switch.setOnPreferenceChangeListener { _, newValue ->
      val enable = newValue as Boolean
      if (!enable) {
        // Turning OFF: just allow it. Do NOT clear the stored key (the user may re-enable later).
        return@setOnPreferenceChangeListener true
      }
      // Turning ON.
      if (isConsentAccepted()) {
        // Already consented: allow ON, and nudge for a key if one is not set yet.
        if (!secretStore.hasApiKey()) {
          showKeyRequiredSnackbar()
        }
        true
      } else {
        // First enable: gate on the one-time consent disclosure. Do NOT commit ON yet; the dialog
        // commits ON (accept) or leaves the switch OFF (decline/cancel).
        showConsentDialog(switch, secretStore)
        false
      }
    }
  }

  private fun showConsentDialog(switch: SwitchPreferenceCompat, secretStore: SecretStore) {
    // Use the fragment's themed context directly (AlertDialogBuilder.Impl wraps exactly this); the
    // controller is not Hilt-injected, so it does not take the injectable builder seam.
    AlertDialog.Builder(fragment.requireContext())
        .setTitle(R.string.ai_consent_title)
        .setMessage(R.string.ai_consent_message)
        .setNegativeButton(android.R.string.cancel) { _, _ -> /* stay OFF */ }
        .setPositiveButton(R.string.ai_consent_enable) { _, _ ->
          setConsentAccepted()
          switch.isChecked = true
          if (!secretStore.hasApiKey()) {
            showKeyRequiredSnackbar()
          }
        }
        .show()
  }

  private fun attachKeyEntry(keyPref: EditTextPreference, secretStore: SecretStore) {
    // SECURITY CRITICAL: the plaintext key must never persist to SharedPreferences.
    keyPref.isPersistent = false
    keyPref.setOnBindEditTextListener {
      it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }
    // Presence-only summary (never the value). Updated manually because notifyChanged() is
    // protected
    // on Preference; a SummaryProvider would only re-render on a framework-driven change anyway.
    refreshKeySummary(keyPref, secretStore)
    keyPref.setOnPreferenceChangeListener { _, newValue ->
      val entered = (newValue as String).trim()
      if (entered.isEmpty()) {
        // Ignore blank input; nothing to route, and never persist.
        return@setOnPreferenceChangeListener false
      }
      secretStore.putApiKey(entered)
      refreshKeySummary(keyPref, secretStore)
      // Returning false keeps the framework from persisting the plaintext value to
      // SharedPreferences.
      false
    }
  }

  private fun refreshKeySummary(keyPref: EditTextPreference, secretStore: SecretStore) {
    keyPref.summary =
        fragment.getString(
            if (secretStore.hasApiKey()) R.string.ai_key_set else R.string.ai_key_not_set
        )
  }

  private fun isConsentAccepted(): Boolean =
      PreferenceManager.getDefaultSharedPreferences(fragment.requireContext())
          .getBoolean(consentKey(), false)

  private fun setConsentAccepted() {
    PreferenceManager.getDefaultSharedPreferences(fragment.requireContext())
        .edit()
        .putBoolean(consentKey(), true)
        .apply()
  }

  private fun consentKey(): String = fragment.getString(R.string.pref_ai_consent_accepted)

  private fun showKeyRequiredSnackbar() {
    val view = fragment.view ?: return
    Snackbar.make(view, R.string.ai_key_required, Snackbar.LENGTH_LONG).show()
  }
}
