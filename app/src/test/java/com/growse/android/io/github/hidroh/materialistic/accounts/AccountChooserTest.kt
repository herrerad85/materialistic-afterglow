/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.accounts

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import androidx.test.core.app.ApplicationProvider
import com.growse.android.io.github.hidroh.materialistic.AlertDialogBuilder
import com.growse.android.io.github.hidroh.materialistic.reply.ReplyNotificationScheduler
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The account chooser's positive action activates an account; its neutral action removes one. */
@RunWith(RobolectricTestRunner::class)
class AccountChooserTest {

  private lateinit var context: Context
  private val session = mockk<AccountSession>(relaxed = true)
  private val scheduler = mockk<ReplyNotificationScheduler>(relaxed = true)
  private val builder = mockk<AlertDialogBuilder<AlertDialog>>()
  private val dialog = mockk<DialogInterface>(relaxed = true)
  private val listenerSlot = slot<DialogInterface.OnClickListener>()

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    every { session.savedAccounts() } returns listOf(SavedAccount("pg"), SavedAccount("dang"))
    every { session.activeUsername } returns "pg"
    every { builder.init(any()) } returns builder
    every { builder.setTitle(any()) } returns builder
    every { builder.setSingleChoiceItems(any(), any(), capture(listenerSlot)) } returns builder
    every { builder.setPositiveButton(any(), any()) } returns builder
    every { builder.setNegativeButton(any(), any()) } returns builder
    every { builder.setNeutralButton(any(), any()) } returns builder
    every { builder.show() } returns mockk(relaxed = true)
  }

  @Test
  fun positive_activatesSelectedAccount() {
    AccountFlowLogic.showAccountChooser(context, builder, session, scheduler)
    val listener = listenerSlot.captured
    listener.onClick(dialog, 0) // select "pg"
    listener.onClick(dialog, DialogInterface.BUTTON_POSITIVE)
    verify { session.setActive("pg") }
    verify { scheduler.reconcile() } // E5-FU-01: switch seeds the new active account now
  }

  @Test
  fun positive_withNoSelection_doesNothing() {
    every { session.activeUsername } returns null // nothing pre-checked -> selection stays -1
    AccountFlowLogic.showAccountChooser(context, builder, session, scheduler)
    val listener = listenerSlot.captured
    listener.onClick(dialog, DialogInterface.BUTTON_POSITIVE) // tap OK without picking
    verify(exactly = 0) { session.setActive(any()) }
    verify(exactly = 0) { scheduler.reconcile() }
  }

  @Test
  fun neutral_removesSelectedAccount() {
    AccountFlowLogic.showAccountChooser(context, builder, session, scheduler)
    val listener = listenerSlot.captured
    listener.onClick(dialog, 1) // select "dang"
    listener.onClick(dialog, DialogInterface.BUTTON_NEUTRAL)
    verify { session.removeAccount("dang") }
    verify { scheduler.reconcile() } // E5-FU-01: remove reconciles polling
  }
}
