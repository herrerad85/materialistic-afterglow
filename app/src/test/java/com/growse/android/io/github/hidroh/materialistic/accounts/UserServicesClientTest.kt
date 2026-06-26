/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.accounts

import android.os.Looper
import com.growse.android.io.github.hidroh.materialistic.R
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.Executor
import okhttp3.Call
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Characterizes the non-Rx UserServicesClient: blocking work runs on the injected io executor and
 * the onDone/onError callback is delivered on the main looper. A fake Call.Factory returns canned
 * responses, a direct executor runs the flow synchronously, and the main looper is drained
 * explicitly so the test can assert the callback does not fire until the main thread processes it.
 */
@RunWith(RobolectricTestRunner::class)
class UserServicesClientTest {

  private val directExecutor = Executor { it.run() }
  private val creds = Credentials("pg", "s3cret")

  private class CapturingCallback : UserServices.Callback() {
    var done: Boolean? = null
    var error: Throwable? = null

    override fun onDone(successful: Boolean) {
      done = successful
    }

    override fun onError(throwable: Throwable?) {
      error = throwable
    }
  }

  private fun client(responder: (Request) -> Response) =
      UserServicesClient(
          Call.Factory { request ->
            mockk<Call> { every { execute() } returns responder(request) }
          },
          directExecutor,
      )

  private fun drain() = shadowOf(Looper.getMainLooper()).idle()

  private fun response(
      request: Request,
      code: Int,
      body: String = "",
      location: String? = null,
      setCookie: String? = null,
  ): Response {
    val builder =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("msg")
            .body(body.toResponseBody(null))
    location?.let { builder.header("location", it) }
    setCookie?.let { builder.header("set-cookie", it) }
    return builder.build()
  }

  @Test
  fun login_movedTemporarily_reportsSuccessOnMainThread() {
    val cb = CapturingCallback()
    client { response(it, 302) }.login("pg", "pw", false, cb)
    assertNull("callback must not fire until the main looper drains", cb.done)
    drain()
    assertEquals(true, cb.done)
    assertNull(cb.error)
  }

  @Test
  fun login_ok_reportsLoginError() {
    val cb = CapturingCallback()
    client { response(it, 200, body = "<body>bad login</body>") }.login("pg", "pw", false, cb)
    drain()
    assertNull(cb.done)
    assertTrue(cb.error is UserServices.Exception)
  }

  @Test
  fun voteUp_movedTemporarily_reportsSuccess() {
    val ok = CapturingCallback()
    client { response(it, 302) }.voteUp(creds, "1", ok)
    drain()
    assertEquals(true, ok.done)
    assertNull(ok.error)
  }

  @Test
  fun voteUp_nonRedirect_reportsUnexpectedResponse() {
    // A non-302 vote outcome is an unexpected flow result, not a silent failure.
    val cb = CapturingCallback()
    client { response(it, 200) }.voteUp(creds, "1", cb)
    drain()

    assertNull(cb.done)
    val err = cb.error
    assertTrue(err is UserServices.Exception)
    assertEquals(
        R.string.account_action_unexpected_response,
        (err as UserServices.Exception).messageRes,
    )
  }

  @Test
  fun reply_movedTemporarily_reportsSuccess() {
    val cb = CapturingCallback()
    client { response(it, 302) }.reply(creds, "1", "hi", cb)
    drain()
    assertEquals(true, cb.done)
    assertNull(cb.error)
  }

  @Test
  fun reply_nonRedirect_reportsUnexpectedResponse() {
    // A non-302 reply outcome is an unexpected flow result, not a silent failure.
    val cb = CapturingCallback()
    client { response(it, 200) }.reply(creds, "1", "hi", cb)
    drain()

    assertNull(cb.done)
    val err = cb.error
    assertTrue(err is UserServices.Exception)
    assertEquals(
        R.string.account_action_unexpected_response,
        (err as UserServices.Exception).messageRes,
    )
  }

  @Test
  fun submit_redirectToNewest_reportsSuccess() {
    val cb = CapturingCallback()
    client { req ->
          when (req.url.encodedPath) {
            "/submit" ->
                response(
                    req,
                    200,
                    body = "<input name=\"fnid\" value=\"abc123\">",
                    setCookie = "user=abc",
                )
            "/r" -> response(req, 302, location = "newest")
            else -> response(req, 200)
          }
        }
        .submit(creds, "Title", "https://example.com", true, cb)
    drain()
    assertEquals(true, cb.done)
    assertNull(cb.error)
  }

  @Test
  fun submit_redirectToItem_reportsExceptionWithData() {
    val cb = CapturingCallback()
    client { req ->
          when (req.url.encodedPath) {
            "/submit" ->
                response(
                    req,
                    200,
                    body = "<input name=\"fnid\" value=\"abc123\">",
                    setCookie = "user=abc",
                )
            "/r" -> response(req, 302, location = "item?id=999")
            else -> response(req, 200)
          }
        }
        .submit(creds, "Title", "dup", false, cb)
    drain()
    assertNull(cb.done)
    val err = cb.error
    assertTrue(err is UserServices.Exception)
    assertNotNull(
        "an item redirect should carry the item Uri as data",
        (err as UserServices.Exception).data,
    )
  }

  @Test
  fun submit_authRedirect_reportsAuthFailed() {
    // A 302 from the submit form is HN redirecting to /login: credentials were not accepted.
    val cb = CapturingCallback()
    client { response(it, 302) }.submit(creds, "Title", "x", false, cb)
    drain()

    assertNull(cb.done)
    val err = cb.error
    assertTrue(err is UserServices.Exception)
    assertEquals(
        R.string.account_action_auth_failed,
        (err as UserServices.Exception).messageRes,
    )
  }

  @Test
  fun submit_missingFnid_reportsUnexpectedResponse() {
    // The submit form is missing the hidden fnid token: HN markup changed (parse failure).
    val cb = CapturingCallback()
    client { req ->
          when (req.url.encodedPath) {
            "/submit" ->
                response(
                    req,
                    200,
                    body = "<input name=\"other\" value=\"x\">",
                    setCookie = "user=abc",
                )
            else -> response(req, 200)
          }
        }
        .submit(creds, "Title", "https://example.com", true, cb)
    drain()

    assertNull(cb.done)
    val err = cb.error
    assertTrue(err is UserServices.Exception)
    assertEquals(
        R.string.account_action_unexpected_response,
        (err as UserServices.Exception).messageRes,
    )
  }

  @Test
  fun submit_unexpectedStatusOnPost_reportsUnexpectedResponse() {
    // The submit POST did not redirect as the scraped flow expects: HN flow changed.
    val cb = CapturingCallback()
    client { req ->
          when (req.url.encodedPath) {
            "/submit" ->
                response(
                    req,
                    200,
                    body = "<input name=\"fnid\" value=\"abc123\">",
                    setCookie = "user=abc",
                )
            "/r" -> response(req, 200)
            else -> response(req, 200)
          }
        }
        .submit(creds, "Title", "https://example.com", true, cb)
    drain()

    assertNull(cb.done)
    assertEquals(
        R.string.account_action_unexpected_response,
        (cb.error as UserServices.Exception).messageRes,
    )
  }

  @Test
  fun submit_redirectWithoutLocation_reportsUnexpectedResponse() {
    // The submit POST redirected (302) but carried no Location to follow: the flow changed.
    val cb = CapturingCallback()
    client { req ->
          when (req.url.encodedPath) {
            "/submit" ->
                response(
                    req,
                    200,
                    body = "<input name=\"fnid\" value=\"abc123\">",
                    setCookie = "user=abc",
                )
            "/r" -> response(req, 302)
            else -> response(req, 200)
          }
        }
        .submit(creds, "Title", "https://example.com", true, cb)
    drain()

    assertNull(cb.done)
    val err = cb.error
    assertTrue(err is UserServices.Exception)
    assertEquals(
        R.string.account_action_unexpected_response,
        (err as UserServices.Exception).messageRes,
    )
  }

  @Test
  fun login_unparseableBody_reportsUnexpectedResponse() {
    // A 200 whose body lacks the expected login error markup: the login page markup changed.
    val cb = CapturingCallback()
    client { response(it, 200, body = "<div>unexpected</div>") }.login("pg", "pw", false, cb)
    drain()

    assertNull(cb.done)
    val err = cb.error
    assertTrue(err is UserServices.Exception)
    assertEquals(
        R.string.account_action_unexpected_response,
        (err as UserServices.Exception).messageRes,
    )
  }

  @Test
  fun login_unexpectedStatus_reportsUnexpectedResponse() {
    // Neither a 302 success nor a 200 rejected-login page: an unexpected status is a flow failure.
    val cb = CapturingCallback()
    client { response(it, 500) }.login("pg", "pw", false, cb)
    drain()

    assertNull(cb.done)
    val err = cb.error
    assertTrue(err is UserServices.Exception)
    assertEquals(
        R.string.account_action_unexpected_response,
        (err as UserServices.Exception).messageRes,
    )
  }
}
