package com.arcana.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A journal written by each released version of the database opens in this one with its readings
 * intact. Run on a device or emulator: `./gradlew :core:core-database:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ArcanaDatabase::class.java)

    private val reading = "INSERT INTO readings (id, timestampEpochMs, spreadId, spreadName, question, interpretation, notes, deckArtId, kind) " +
        "VALUES ('r1', 1700000000000, 'three_card_ppf', 'Past, Present, Future', 'What now?', 'The reading.', 'My notes.', 'rider-waite', 'DIGITAL')"
    private val card = "INSERT INTO drawn_cards (readingId, cardId, orientation, positionIndex) VALUES ('r1', 'major_09_hermit', 'REVERSED', 1)"

    @Test
    fun a_journal_from_before_custom_spreads_keeps_its_readings() {
        helper.createDatabase(NAME, 2).use { db ->
            db.execSQL(reading)
            db.execSQL(card)
        }
        helper.runMigrationsAndValidate(NAME, 4, true, *Migrations.ALL).use { db ->
            db.query("SELECT question, notes, spreadPositionsJson FROM readings").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("What now?", c.getString(0))
                assertEquals("My notes.", c.getString(1))
                assertTrue(c.isNull(2))
                assertEquals(1, c.count)
            }
            db.query("SELECT cardId FROM drawn_cards WHERE readingId = 'r1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("major_09_hermit", c.getString(0))
            }
            db.query("SELECT COUNT(*) FROM custom_spreads").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
        }
    }

    @Test
    fun a_journal_from_before_position_snapshots_keeps_its_readings_and_spreads() {
        helper.createDatabase(NAME, 3).use { db ->
            db.execSQL(reading)
            db.execSQL(card)
            db.execSQL("INSERT INTO custom_spreads (id, name, description, createdAtEpochMs) VALUES ('custom_1', 'Mine', 'A spread of my own.', 1700000000000)")
            db.execSQL(
                "INSERT INTO custom_spread_positions (spreadId, positionIndex, label, meaning, x, y, rotationDegrees) " +
                    "VALUES ('custom_1', 1, 'Here', 'Where I stand.', 0.5, 0.5, 0)",
            )
        }
        helper.runMigrationsAndValidate(NAME, 4, true, *Migrations.ALL).use { db ->
            db.query("SELECT COUNT(*) FROM readings").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
            db.query("SELECT label FROM custom_spread_positions WHERE spreadId = 'custom_1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Here", c.getString(0))
            }
        }
    }

    private companion object {
        const val NAME = "migration-test.db"
    }
}
