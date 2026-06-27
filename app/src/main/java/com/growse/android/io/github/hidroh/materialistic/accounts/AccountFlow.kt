/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.accounts

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.growse.android.io.github.hidroh.materialistic.AlertDialogBuilder
import com.growse.android.io.github.hidroh.materialistic.reply.ReplyNotificationScheduler
import javax.inject.Inject

/**
 * Injectable seam for the account/login flow, extracted from AppUtils. Hilt-managed callers (the
 * compose / item / submit / drawer activities) depend on this interface; [DefaultAccountFlow] binds
 * the activity-scoped [AlertDialogBuilder] and the [ReplyNotificationScheduler] so neither has to
 * be self-sourced through a service locator (this removes the old `EntryPointAccessors` use). The
 * behavior lives in [AccountFlowLogic], which adapters call directly with explicit dependencies.
 */
interface AccountFlow {
  /** Routes to the login screen or the account chooser per saved-account / active-session state. */
  fun showLogin(context: Context, session: AccountSession)

  /** Shows the saved-account chooser (activate / add / remove). */
  fun showAccountChooser(context: Context, session: AccountSession)
}

/** Binds the activity-scoped dialog builder and the reply scheduler into the account-flow seam. */
class DefaultAccountFlow
@Inject
constructor(
    @JvmSuppressWildcards private val alertDialogBuilder: AlertDialogBuilder<AlertDialog>,
    private val scheduler: ReplyNotificationScheduler,
) : AccountFlow {
  override fun showLogin(context: Context, session: AccountSession) =
      AccountFlowLogic.showLogin(context, alertDialogBuilder, session, scheduler)

  override fun showAccountChooser(context: Context, session: AccountSession) =
      AccountFlowLogic.showAccountChooser(context, alertDialogBuilder, session, scheduler)
}
