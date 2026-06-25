/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.accounts

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountActionsTest {

  private val session = mockk<AccountSession>()
  private val userServices = mockk<UserServices>(relaxed = true)
  private val actions = AccountActions(session, userServices)
  private val callback = mockk<UserServices.Callback>(relaxed = true)

  @Test
  fun vote_noSession_needsLoginAndDoesNotCallUserServices() {
    every { session.credentials() } returns null
    assertEquals(AccountActions.Result.NeedsLogin, actions.vote("123", callback))
    verify(exactly = 0) { userServices.voteUp(any(), any(), any()) }
  }

  @Test
  fun vote_withSession_startedAndCallsUserServicesWithCredentials() {
    val creds = Credentials("pg", "s3cret")
    every { session.credentials() } returns creds
    assertEquals(AccountActions.Result.Started, actions.vote("123", callback))
    verify { userServices.voteUp(creds, "123", callback) }
  }

  @Test
  fun reply_noSession_needsLogin() {
    every { session.credentials() } returns null
    assertEquals(AccountActions.Result.NeedsLogin, actions.reply("1", "hi", callback))
    verify(exactly = 0) { userServices.reply(any(), any(), any(), any()) }
  }

  @Test
  fun reply_withSession_started() {
    val creds = Credentials("pg", "s3cret")
    every { session.credentials() } returns creds
    assertEquals(AccountActions.Result.Started, actions.reply("1", "hi", callback))
    verify { userServices.reply(creds, "1", "hi", callback) }
  }

  @Test
  fun submit_noSession_needsLogin() {
    every { session.credentials() } returns null
    assertEquals(AccountActions.Result.NeedsLogin, actions.submit("t", "c", true, callback))
    verify(exactly = 0) { userServices.submit(any(), any(), any(), any(), any()) }
  }

  @Test
  fun submit_withSession_started() {
    val creds = Credentials("pg", "s3cret")
    every { session.credentials() } returns creds
    assertEquals(AccountActions.Result.Started, actions.submit("t", "c", false, callback))
    verify { userServices.submit(creds, "t", "c", false, callback) }
  }
}
