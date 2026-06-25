/*
 * Copyright (c) 2018 Ha Duy Trung
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.growse.android.io.github.hidroh.materialistic.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.CursorWrapper
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.growse.android.io.github.hidroh.materialistic.FavoriteActivity
import com.growse.android.io.github.hidroh.materialistic.IoDispatcher
import com.growse.android.io.github.hidroh.materialistic.R
import com.growse.android.io.github.hidroh.materialistic.ktx.closeQuietly
import com.growse.android.io.github.hidroh.materialistic.ktx.getUri
import com.growse.android.io.github.hidroh.materialistic.ktx.setChannel
import com.growse.android.io.github.hidroh.materialistic.ktx.toSendIntentChooser
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink

/** Data repository for {@link Favorite} */
@Singleton
class FavoriteManager
@Inject
constructor(
    private val cache: LocalCache,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val dao: MaterialisticDatabase.SavedStoriesDao,
) : LocalItemManager<Favorite> {

  companion object {
    private const val CHANNEL_EXPORT = "export"
    private const val URI_PATH_ADD = "add"
    private const val URI_PATH_REMOVE = "remove"
    private const val URI_PATH_CLEAR = "clear"
    private const val PATH_SAVED = "saved"
    private const val FILENAME_EXPORT = "materialistic-export.txt"
    private const val FILE_AUTHORITY = "com.herrerad85.afterglow.fileprovider"

    fun isAdded(uri: Uri) = uri.toString().startsWith(buildAdded().toString())

    fun isRemoved(uri: Uri) = uri.toString().startsWith(buildRemoved().toString())

    fun isCleared(uri: Uri) = uri.toString().startsWith(buildCleared().toString())

    private fun buildAdded(): Uri.Builder =
        MaterialisticDatabase.getBaseSavedUri().buildUpon().appendPath(URI_PATH_ADD)

    private fun buildCleared(): Uri.Builder =
        MaterialisticDatabase.getBaseSavedUri().buildUpon().appendPath(URI_PATH_CLEAR)

    private fun buildRemoved(): Uri.Builder =
        MaterialisticDatabase.getBaseSavedUri().buildUpon().appendPath(URI_PATH_REMOVE)
  }

  private val notificationId = System.currentTimeMillis().toInt()
  private val syncScheduler = SyncScheduler()
  private var cursor: Cursor? = null
  private var loader: FavoriteRoomLoader? = null
  // Runs the fire-and-forget DB work off the main thread (was subscribeOn(ioScheduler)); LiveData
  // and
  // observer notifications are delivered back on Dispatchers.Main (was observeOn(mainThread())).
  private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

  override fun getSize() = cursor?.count ?: 0

  override fun getItem(position: Int) =
      if (cursor?.moveToPosition(position) == true) {
        cursor!!.favorite
      } else {
        null
      }

  override fun attach(observer: LocalItemManager.Observer, filter: String?) {
    loader = FavoriteRoomLoader(filter, observer)
    loader!!.load()
  }

  override fun detach() {
    if (cursor != null) {
      cursor = null
    }
    loader = null
  }

  /**
   * Exports all favorites matched given query to file
   *
   * @param context an instance of {@link android.content.Context}
   * @param query query to filter stories to be retrieved
   */
  fun export(context: Context, query: String?) {
    val appContext = context.applicationContext
    notifyExportStart(appContext)
    scope.launch {
      val uri: Uri? =
          try {
            val cursor = query(query)
            if (cursor.moveToFirst()) {
              try {
                toFile(appContext, Cursor(cursor))
              } catch (e: IOException) {
                null
              } finally {
                cursor.close()
              }
            } else {
              cursor.close()
              null
            }
          } catch (e: Throwable) {
            null
          }
      withContext(Dispatchers.Main) { notifyExportDone(appContext, uri) }
    }
  }

  /**
   * Adds given story as favorite
   *
   * @param context an instance of {@link android.content.Context}
   * @param story story to be added as favorite
   */
  fun add(context: Context, story: WebItem) {
    scope.launch {
      insert(story)
      val uri = buildAdded().appendPath(story.id).build()
      withContext(Dispatchers.Main) { MaterialisticDatabase.getInstance(context).setLiveValue(uri) }
    }
    syncScheduler.scheduleSync(context, story.id)
  }

  /**
   * Clears all stories matched given query from favorites will be sent upon completion
   *
   * @param context an instance of {@link android.content.Context}
   * @param query query to filter stories to be cleared
   */
  fun clear(context: Context, query: String?) {
    scope.launch {
      deleteMultiple(query)
      withContext(Dispatchers.Main) {
        MaterialisticDatabase.getInstance(context).setLiveValue(buildCleared().build())
      }
    }
  }

  /**
   * Removes story with given ID from favorites upon completion
   *
   * @param context an instance of {@link android.content.Context}
   * @param itemId story ID to be removed from favorites
   */
  fun remove(context: Context, itemId: String?) {
    if (itemId == null) return
    scope.launch {
      delete(itemId)
      val uri = buildRemoved().appendPath(itemId).build()
      withContext(Dispatchers.Main) { MaterialisticDatabase.getInstance(context).setLiveValue(uri) }
    }
  }

  /**
   * Removes multiple stories with given IDs from favorites be sent upon completion
   *
   * @param context an instance of {@link android.content.Context}
   * @param itemIds array of story IDs to be removed from favorites
   */
  fun remove(context: Context, itemIds: Collection<String>?) {
    if (itemIds.orEmpty().isEmpty()) return
    scope.launch {
      itemIds.orEmpty().forEach { itemId ->
        delete(itemId)
        val uri = buildRemoved().appendPath(itemId).build()
        withContext(Dispatchers.Main) {
          MaterialisticDatabase.getInstance(context).setLiveValue(uri)
        }
      }
    }
  }

  @WorkerThread
  fun check(itemId: String?): Boolean =
      if (itemId.isNullOrEmpty()) {
        false
      } else {
        cache.isFavorite(itemId)
      }

  @WorkerThread
  private fun toFile(context: Context, cursor: Cursor): Uri? {
    if (cursor.count == 0) return null
    val dir = File(context.filesDir, PATH_SAVED)
    if (!dir.exists() && !dir.mkdir()) return null
    val file = File(dir, FILENAME_EXPORT)
    if (!file.exists() && !file.createNewFile()) return null
    val bufferedSink = file.sink().buffer()
    with(bufferedSink) {
      do {
        val item = cursor.favorite
        writeUtf8(item.displayedTitle)
        writeByte('\n'.code)
        writeUtf8(item.url)
        writeByte('\n'.code)
        writeUtf8(HackerNewsClient.WEB_ITEM_PATH.format(item.id))
        if (!cursor.isLast) {
          writeByte('\n'.code)
          writeByte('\n'.code)
        }
      } while (cursor.moveToNext())
      flush()
      closeQuietly()
    }
    return file.getUri(context, FILE_AUTHORITY)
  }

  private fun notifyExportStart(context: Context) {
    if (
        ActivityCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
    )
        return
    NotificationManagerCompat.from(context)
        .notify(
            notificationId,
            createNotificationBuilder(context)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setProgress(0, 0, true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, FavoriteActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        when {
                          Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                              PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                          else -> PendingIntent.FLAG_UPDATE_CURRENT
                        },
                    )
                )
                .build(),
        )
  }

  private fun notifyExportDone(context: Context, uri: Uri?) {
    if (
        ActivityCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
    )
        return
    val manager = NotificationManagerCompat.from(context)
    with(manager) {
      cancel(notificationId)
      if (uri == null) return
      context.grantUriPermission(context.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
      notify(
          notificationId,
          createNotificationBuilder(context)
              .setPriority(NotificationCompat.PRIORITY_HIGH)
              .setVibrate(longArrayOf(0L))
              .setContentText(context.getString(R.string.export_notification))
              .setContentIntent(
                  PendingIntent.getActivity(
                      context,
                      0,
                      uri.toSendIntentChooser(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                      when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        else -> PendingIntent.FLAG_UPDATE_CURRENT
                      },
                  )
              )
              .build(),
      )
    }
  }

  private fun createNotificationBuilder(context: Context) =
      NotificationCompat.Builder(context, CHANNEL_EXPORT)
          .setChannel(context, CHANNEL_EXPORT, context.getString(R.string.export_saved_stories))
          .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
          .setSmallIcon(R.drawable.ic_notification)
          .setContentTitle(context.getString(R.string.export_saved_stories))
          .setAutoCancel(true)

  @WorkerThread
  private fun query(filter: String?): android.database.Cursor =
      if (filter.isNullOrEmpty()) {
        dao.selectAllToCursor()
      } else {
        dao.searchToCursor(filter)
      }

  @WorkerThread
  private fun insert(story: WebItem) {
    dao.insert(MaterialisticDatabase.SavedStory.from(story))
    loader?.load()
  }

  @WorkerThread
  private fun delete(itemId: String?) {
    dao.deleteByItemId(itemId)
    loader?.load()
  }

  @WorkerThread
  private fun deleteMultiple(query: String?): Int {
    val deleted = if (query.isNullOrEmpty()) dao.deleteAll() else dao.deleteByTitle(query)
    loader?.load()
    return deleted
  }

  /** A cursor wrapper to retrieve associated {@link Favorite} */
  private class Cursor(cursor: android.database.Cursor) : CursorWrapper(cursor) {
    val favorite: Favorite
      get() =
          Favorite(
              getString(
                  getColumnIndexOrThrow(MaterialisticDatabase.FavoriteEntry.COLUMN_NAME_ITEM_ID)
              ),
              getString(getColumnIndexOrThrow(MaterialisticDatabase.FavoriteEntry.COLUMN_NAME_URL)),
              getString(getColumnIndex(MaterialisticDatabase.FavoriteEntry.COLUMN_NAME_TITLE)),
              getString(getColumnIndex(MaterialisticDatabase.FavoriteEntry.COLUMN_NAME_TIME))
                  .toLong(),
          )
  }

  inner class FavoriteRoomLoader(
      private val filter: String?,
      private val observer: LocalItemManager.Observer,
  ) {
    @AnyThread
    fun load() {
      scope.launch {
        val result = query(filter)
        withContext(Dispatchers.Main) {
          cursor = Cursor(result)
          observer.onChanged()
        }
      }
    }
  }
}
