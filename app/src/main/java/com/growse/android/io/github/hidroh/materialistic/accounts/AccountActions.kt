/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.accounts

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates account actions: reads the active session and either reports that login is needed or
 * starts the request via [UserServices] with explicit credentials. UI owns
 * dialogs/toasts/navigation.
 */
@Singleton
class AccountActions
@Inject
constructor(val session: AccountSession, private val userServices: UserServices) {

  enum class Result {
    /** No active session; the caller should prompt login. */
    NeedsLogin,
    /** Request dispatched to [UserServices]; its callback reports the outcome. */
    Started,
  }

  fun vote(itemId: String, callback: UserServices.Callback): Result {
    val credentials = session.credentials() ?: return Result.NeedsLogin
    userServices.voteUp(credentials, itemId, callback)
    return Result.Started
  }

  /** Retract a prior upvote on the item. Same session gate as [vote]. */
  fun unvote(itemId: String, callback: UserServices.Callback): Result {
    val credentials = session.credentials() ?: return Result.NeedsLogin
    userServices.unvote(credentials, itemId, callback)
    return Result.Started
  }

  fun reply(parentId: String, text: String, callback: UserServices.Callback): Result {
    val credentials = session.credentials() ?: return Result.NeedsLogin
    userServices.reply(credentials, parentId, text, callback)
    return Result.Started
  }

  fun submit(
      title: String,
      content: String,
      isUrl: Boolean,
      callback: UserServices.Callback,
  ): Result {
    val credentials = session.credentials() ?: return Result.NeedsLogin
    userServices.submit(credentials, title, content, isUrl, callback)
    return Result.Started
  }
}
