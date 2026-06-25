/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.growse.android.io.github.hidroh.materialistic.ItemActivity
import com.growse.android.io.github.hidroh.materialistic.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The real reply notifier (E5-D4 / E5-D8 G3). Posts one grouped notification per reply plus a group
 * summary on the "replies" channel. Tapping a per-reply notification opens the parent thread in
 * comment mode via an ACTION_VIEW deep link ([ItemActivity] reads the id from the web URI and
 * EXTRA_OPEN_COMMENTS forces comment mode), so no package-private Java model has to be touched.
 * Tapping the summary opens the app launcher.
 *
 * Every post is guarded by [canPost] (the POST_NOTIFICATIONS grant on API 33+, the app
 * notifications-enabled state below 33). Belt-and-suspenders: the toggle-on flow already gates the
 * permission, but a later system revocation or a pre-33 device must still resolve correctly. The
 * per-reply id is stable from the kid id so a re-post is idempotent; the seen-baseline already
 * prevents re-notifying.
 */
class SystemReplyNotifier @Inject constructor(@ApplicationContext private val context: Context) :
    ReplyNotifier {

  // Both notify() calls are guarded by the canPost() early-return above (POST_NOTIFICATIONS on
  // API 33+, app-notifications-enabled below 33). Lint can't follow that guard across the call,
  // so suppress at this function level only, not file-wide.
  @SuppressLint("MissingPermission")
  override fun notify(replies: List<NotifiableReply>) {
    if (replies.isEmpty()) return
    // Skip the work when nothing would show anyway. POST_NOTIFICATIONS is a runtime permission only
    // on API 33+, so checking it below 33 wrongly reports DENIED and would drop EVERY reply
    // notification on the whole pre-33 base; there we fall back to the app-level enabled state.
    if (!canPost()) return

    val channelName = context.getString(R.string.notification_channel_replies)
    createRepliesChannel(channelName)
    val manager = NotificationManagerCompat.from(context)

    replies.forEach { reply ->
      val id = reply.kidId.toIntOrNull() ?: reply.kidId.hashCode()
      val notification =
          NotificationCompat.Builder(context, CHANNEL_REPLIES)
              .setSmallIcon(R.drawable.ic_notification)
              .setAutoCancel(true)
              .setGroup(GROUP_REPLIES)
              // Only the summary alerts, so N new replies make one sound, not N+1.
              .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
              .setContentTitle(context.getString(R.string.reply_notification_title, reply.author))
              .setContentText(replyBody(reply))
              .setContentIntent(threadPendingIntent(reply.parentId, id))
              .build()
      manager.notify(id, notification)
    }

    // The group summary holds the per-reply notifications together and shows the aggregate count.
    val summary =
        NotificationCompat.Builder(context, CHANNEL_REPLIES)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setGroup(GROUP_REPLIES)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setContentTitle(
                context.resources.getQuantityString(
                    R.plurals.reply_notification_summary,
                    replies.size,
                    replies.size,
                )
            )
            .setContentIntent(summaryPendingIntent())
            .build()
    manager.notify(SUMMARY_ID, summary)
  }

  /**
   * Whether a post would actually surface. On API 33+ that is the POST_NOTIFICATIONS runtime grant;
   * below 33 the permission does not exist (checkSelfPermission returns DENIED), so use the
   * app-level notifications-enabled state, which also respects a user who turned notifications off
   * in settings.
   */
  private fun canPost(): Boolean =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
          ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
              PackageManager.PERMISSION_GRANTED
      else NotificationManagerCompat.from(context).areNotificationsEnabled()

  private fun replyBody(reply: NotifiableReply): String =
      reply.parentTitleOrText?.let { context.getString(R.string.reply_notification_text, it) }
          ?: context.getString(R.string.reply_notification_text_generic)

  /** Tap a reply -> open the parent thread in comment mode. Public API only (E5-D8). */
  private fun threadPendingIntent(parentId: String, requestCode: Int): PendingIntent {
    val intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(HN_ITEM_URL + parentId))
            .setClass(context, ItemActivity::class.java)
            .putExtra(ItemActivity.EXTRA_OPEN_COMMENTS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return PendingIntent.getActivity(context, requestCode, intent, pendingIntentFlags())
  }

  /**
   * Tap the summary -> open the app launcher (story list), per E5-D4. Null if there is no launcher.
   */
  private fun summaryPendingIntent(): PendingIntent? {
    val launch =
        context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return PendingIntent.getActivity(context, SUMMARY_ID, launch, pendingIntentFlags())
  }

  /**
   * Create the Replies channel at DEFAULT importance so an opted-in user actually hears a reply
   * arrive. Idempotent; channel importance is fixed at first creation and cannot be raised later,
   * so it is set deliberately here rather than via the LOW-importance shared helper.
   */
  private fun createRepliesChannel(name: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(
          NotificationChannel(CHANNEL_REPLIES, name, NotificationManager.IMPORTANCE_DEFAULT)
      )
    }
  }

  private fun pendingIntentFlags(): Int =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      else PendingIntent.FLAG_UPDATE_CURRENT

  private companion object {
    const val CHANNEL_REPLIES = "replies"
    const val GROUP_REPLIES = "com.growse.android.io.github.hidroh.materialistic.REPLIES"
    /** Fixed summary id, distinct from any kid-derived per-reply id. */
    const val SUMMARY_ID = 0x52455059
    const val HN_ITEM_URL = "https://news.ycombinator.com/item?id="
  }
}
