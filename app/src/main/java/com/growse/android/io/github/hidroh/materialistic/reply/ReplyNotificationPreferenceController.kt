/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.snackbar.Snackbar
import com.growse.android.io.github.hidroh.materialistic.Application
import com.growse.android.io.github.hidroh.materialistic.R
import dagger.hilt.android.EntryPointAccessors

/**
 * Scoped controller for the reply-notifications switch (E5-D6 / E5-D8 G3). The generic Java
 * `SettingsFragment` attaches one of these only when the switch is on the loaded screen, so this
 * never runs on preference screens without it.
 *
 * [attach] must be called during the fragment's onCreate (after the prefs inflate), so registering
 * the permission launcher is valid (before STARTED) and the switch already exists.
 *
 * The toggle reads the committed [com.growse.android.io.github.hidroh.materialistic.Preferences]
 * value, so every call to [ReplyNotificationScheduler.onToggleChanged] is deferred with view.post
 * until after the preference framework has written the new value.
 */
class ReplyNotificationPreferenceController(private val fragment: PreferenceFragmentCompat) {

  fun attach() {
    val scheduler =
        EntryPointAccessors.fromApplication(
                fragment.requireContext().applicationContext,
                Application.ReplyNotificationEntryPoint::class.java,
            )
            .replyNotificationScheduler()

    val switch =
        fragment.findPreference<SwitchPreferenceCompat>(
            fragment.getString(R.string.pref_reply_notifications)
        ) ?: return

    // Registered during onCreate (before STARTED), so this is a valid registration point. Granted
    // -> commit the switch ON and schedule; denied -> stay OFF and explain via a Snackbar.
    val launcher =
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
          if (granted) {
            switch.isChecked = true
            scheduler.onToggleChanged()
          } else {
            showDeniedSnackbar()
          }
        }

    switch.setOnPreferenceChangeListener { _, newValue ->
      val enable = newValue as Boolean
      if (!enable) {
        // Turning OFF: let the framework commit OFF, then reconcile (the scheduler cancels work).
        // view.post defers so onToggleChanged reads the committed pref value.
        fragment.requireView().post { scheduler.onToggleChanged() }
        return@setOnPreferenceChangeListener true
      }
      // Turning ON.
      if (
          Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
              ContextCompat.checkSelfPermission(
                  fragment.requireContext(),
                  Manifest.permission.POST_NOTIFICATIONS,
              ) == PackageManager.PERMISSION_GRANTED
      ) {
        // API < 33 or already granted: commit ON and schedule.
        fragment.requireView().post { scheduler.onToggleChanged() }
        true
      } else {
        // API 33+ and not granted: request first. Do NOT commit ON yet; the result handler commits
        // (granted) or leaves the switch OFF (denied).
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        false
      }
    }
  }

  private fun showDeniedSnackbar() {
    val view = fragment.view ?: return
    Snackbar.make(view, R.string.reply_notifications_permission_denied, Snackbar.LENGTH_LONG)
        .setAction(R.string.action_settings) {
          fragment.startActivity(
              Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                  .putExtra(
                      Settings.EXTRA_APP_PACKAGE,
                      fragment.requireContext().packageName,
                  )
          )
        }
        .show()
  }
}
