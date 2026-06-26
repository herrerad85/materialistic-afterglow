/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

import android.os.Bundle
import android.text.format.Formatter
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quiet offline-storage screen (#24), reached from the Offline settings entry. Shows the
 * regenerable cache footprint (article archives, reader text, network response cache) plus the
 * saved-story count (user data, shown for context only), and lets the user clear each cache. Stats
 * and clears run off the main thread; saved stories are never touched by the clear controls.
 */
@AndroidEntryPoint
class OfflineStorageActivity : ThemedActivity() {

  @Inject lateinit var manager: OfflineStorageManager
  @Inject @JvmSuppressWildcards lateinit var alertDialogBuilder: AlertDialogBuilder<AlertDialog>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_offline_storage)
    setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
    setTitle(R.string.title_activity_offline_storage)
    supportActionBar!!.displayOptions =
        ActionBar.DISPLAY_SHOW_HOME or ActionBar.DISPLAY_HOME_AS_UP or ActionBar.DISPLAY_SHOW_TITLE
    AppUtils.padTopSystemBars(findViewById(R.id.toolbar))
    AppUtils.padBottomSystemBars(findViewById(R.id.offline_storage_scroll), false)

    findViewById<TextView>(R.id.button_clear_archives).setOnClickListener {
      confirmClear(R.string.offline_storage_clear_archives_confirm) {
        manager.clearArticleArchives()
      }
    }
    findViewById<TextView>(R.id.button_clear_reader).setOnClickListener {
      confirmClear(R.string.offline_storage_clear_reader_confirm) { manager.clearReaderText() }
    }
    findViewById<TextView>(R.id.button_clear_http).setOnClickListener {
      confirmClear(R.string.offline_storage_clear_http_confirm) { manager.clearHttpCache() }
    }

    refreshStats()
  }

  private fun refreshStats() {
    lifecycleScope.launch {
      val stats = withContext(Dispatchers.IO) { manager.computeStats() }
      bindStats(stats)
    }
  }

  private fun bindStats(stats: OfflineStorageStats) {
    findViewById<TextView>(R.id.value_archives).text =
        resources.getQuantityString(
            R.plurals.offline_storage_archives_value,
            stats.archiveCount,
            stats.archiveCount,
            size(stats.archiveBytes),
        )
    findViewById<TextView>(R.id.value_reader).text = size(stats.readerTextBytes)
    findViewById<TextView>(R.id.value_http).text = size(stats.httpCacheBytes)
    findViewById<TextView>(R.id.value_total).text = size(stats.cacheBytes)
    findViewById<TextView>(R.id.value_saved).text =
        resources.getQuantityString(
            R.plurals.offline_storage_saved_count,
            stats.savedStoryCount,
            stats.savedStoryCount,
        )
  }

  /** Confirms before running [clear] (which returns the bytes freed) off the main thread. */
  private fun confirmClear(messageRes: Int, clear: () -> Long) {
    alertDialogBuilder
        .init(this)
        .setMessage(messageRes)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(android.R.string.ok) { _, _ -> runClear(clear) }
        .show()
  }

  private fun runClear(clear: () -> Long) {
    lifecycleScope.launch {
      val freed = withContext(Dispatchers.IO) { clear() }
      val stats = withContext(Dispatchers.IO) { manager.computeStats() }
      bindStats(stats)
      Snackbar.make(
              findViewById(R.id.content_frame),
              getString(R.string.offline_storage_freed, size(freed)),
              Snackbar.LENGTH_SHORT,
          )
          .show()
    }
  }

  private fun size(bytes: Long): String = Formatter.formatShortFileSize(this, bytes)

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      finish()
      return true
    }
    return super.onOptionsItemSelected(item)
  }
}
