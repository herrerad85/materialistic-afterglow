/*
 * Copyright (c) 2015 Ha Duy Trung
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.accounts

import android.net.Uri
import androidx.annotation.StringRes
import java.io.IOException

interface UserServices {
  abstract class Callback {
    open fun onDone(successful: Boolean) {}

    open fun onError(throwable: Throwable?) {}
  }

  class Exception : IOException {
    @JvmField @StringRes val messageRes: Int
    @JvmField var data: Uri? = null

    constructor(@StringRes messageRes: Int) : super() {
      this.messageRes = messageRes
    }

    constructor(message: String?) : super(message) {
      this.messageRes = 0
    }
  }

  fun login(username: String, password: String, createAccount: Boolean, callback: Callback)

  fun voteUp(credentials: Credentials, itemId: String, callback: Callback)

  /** Retract a prior upvote (HN's own unvote action, `how=un` on the vote endpoint). */
  fun unvote(credentials: Credentials, itemId: String, callback: Callback)

  fun reply(credentials: Credentials, parentId: String, text: String, callback: Callback)

  fun submit(
      credentials: Credentials,
      title: String,
      content: String,
      isUrl: Boolean,
      callback: Callback,
  )
}
