/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Validates the Room 4 -> 5 migration ([MaterialisticDatabase.MIGRATION_4_5]) that adds the
 * reply-tracking tables (E5-D5). Rather than pull in `androidx.room:room-testing`, this builds a
 * real v4 database, applies the migration, and asserts the resulting `reply_seen` /
 * `reply_poll_state` schema is byte-for-byte what Room generates from the entities, and that
 * pre-existing data survives.
 */
@RunWith(RobolectricTestRunner::class)
class MaterialisticDatabaseMigrationTest {

  private val dbFile = "migration-test.db"
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.deleteDatabase(dbFile)
  }

  @After fun tearDown() = context.deleteDatabase(dbFile).let {}

  @Test
  fun migrate4to5_addsReplyTablesMatchingEntities_andKeepsExistingData() {
    // 1. A real v4 database with the legacy tables and a row that must survive.
    val v4Helper =
        FrameworkSQLiteOpenHelperFactory()
            .create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(dbFile)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(4) {
                          override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE read (_id INTEGER NOT NULL PRIMARY KEY, itemid TEXT)"
                            )
                            db.execSQL(
                                "CREATE TABLE readable (_id INTEGER NOT NULL PRIMARY KEY, itemid TEXT, content TEXT)"
                            )
                            db.execSQL(
                                "CREATE TABLE saved (_id INTEGER NOT NULL PRIMARY KEY, itemid TEXT, url TEXT, title TEXT, time TEXT)"
                            )
                            db.execSQL(
                                "INSERT INTO saved (_id, itemid, url, title, time) VALUES (1, '8863', 'u', 't', '100')"
                            )
                          }

                          override fun onUpgrade(
                              db: SupportSQLiteDatabase,
                              oldVersion: Int,
                              newVersion: Int,
                          ) = Unit
                        }
                    )
                    .build()
            )
    val migrated = v4Helper.writableDatabase

    // 2. Apply only the 4 -> 5 migration under test.
    MaterialisticDatabase.MIGRATION_4_5.migrate(migrated)

    // 3. The new tables must match the schema Room derives from the entities.
    val fresh =
        Room.inMemoryDatabaseBuilder(context, MaterialisticDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    try {
      val expectedDb = fresh.openHelper.writableDatabase
      assertEquals(tableInfo(expectedDb, "reply_seen"), tableInfo(migrated, "reply_seen"))
      assertEquals(
          tableInfo(expectedDb, "reply_poll_state"),
          tableInfo(migrated, "reply_poll_state"),
      )
    } finally {
      fresh.close()
    }

    // 4. Pre-existing data is untouched by the migration.
    migrated.query("SELECT itemid FROM saved").use { c ->
      assertTrue(c.moveToFirst())
      assertEquals("8863", c.getString(0))
    }
    v4Helper.close()
  }

  @Test
  fun migrate5to6_addsCommentSeenTableMatchingEntity_andKeepsExistingData() {
    // 1. A real v5 database (saved + the reply tables from the 4 -> 5 migration) with a surviving
    // row.
    val v5Helper =
        FrameworkSQLiteOpenHelperFactory()
            .create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(dbFile)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(5) {
                          override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE saved (_id INTEGER NOT NULL PRIMARY KEY, itemid TEXT, url TEXT, title TEXT, time TEXT)"
                            )
                            db.execSQL(
                                "INSERT INTO saved (_id, itemid, url, title, time) VALUES (1, '8863', 'u', 't', '100')"
                            )
                            db.execSQL(
                                "CREATE TABLE IF NOT EXISTS `reply_seen` (`username` TEXT NOT NULL, `parent_id` TEXT NOT NULL, `kid_id` TEXT NOT NULL, PRIMARY KEY(`username`, `kid_id`))"
                            )
                            db.execSQL(
                                "CREATE TABLE IF NOT EXISTS `reply_poll_state` (`username` TEXT NOT NULL, `last_polled_at` INTEGER NOT NULL, PRIMARY KEY(`username`))"
                            )
                          }

                          override fun onUpgrade(
                              db: SupportSQLiteDatabase,
                              oldVersion: Int,
                              newVersion: Int,
                          ) = Unit
                        }
                    )
                    .build()
            )
    val migrated = v5Helper.writableDatabase

    // 2. Apply only the 5 -> 6 migration under test.
    MaterialisticDatabase.MIGRATION_5_6.migrate(migrated)

    // 3. The new table must match the schema Room derives from the entity.
    val fresh =
        Room.inMemoryDatabaseBuilder(context, MaterialisticDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    try {
      val expectedDb = fresh.openHelper.writableDatabase
      assertEquals(tableInfo(expectedDb, "comment_seen"), tableInfo(migrated, "comment_seen"))
    } finally {
      fresh.close()
    }

    // 4. Pre-existing data is untouched by the migration.
    migrated.query("SELECT itemid FROM saved").use { c ->
      assertTrue(c.moveToFirst())
      assertEquals("8863", c.getString(0))
    }
    v5Helper.close()
  }

  private data class Col(val name: String, val type: String, val notNull: Boolean, val pk: Int)

  private fun tableInfo(db: SupportSQLiteDatabase, table: String): List<Col> {
    val cols = mutableListOf<Col>()
    db.query("PRAGMA table_info(`$table`)").use { c ->
      val iName = c.getColumnIndex("name")
      val iType = c.getColumnIndex("type")
      val iNotNull = c.getColumnIndex("notnull")
      val iPk = c.getColumnIndex("pk")
      while (c.moveToNext()) {
        cols.add(
            Col(c.getString(iName), c.getString(iType), c.getInt(iNotNull) == 1, c.getInt(iPk))
        )
      }
    }
    return cols.sortedBy { it.name }
  }
}
