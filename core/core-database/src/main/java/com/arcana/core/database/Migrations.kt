package com.arcana.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * How a journal written by an older version of the app is brought up to date. Every version that
 * was ever released has a way forward, so an update never costs anyone their readings.
 *
 * The statements are the ones Room generates for the version being moved to; they can be read
 * out of the files in `core/core-database/schemas`.
 */
internal object Migrations {

    /** 0.4.0 added spreads the user designs. */
    val FROM_2_TO_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `custom_spreads` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`description` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `custom_spread_positions` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`spreadId` TEXT NOT NULL, `positionIndex` INTEGER NOT NULL, `label` TEXT NOT NULL, `meaning` TEXT NOT NULL, " +
                    "`x` REAL NOT NULL, `y` REAL NOT NULL, `rotationDegrees` REAL NOT NULL, " +
                    "FOREIGN KEY(`spreadId`) REFERENCES `custom_spreads`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_spread_positions_spreadId` ON `custom_spread_positions` (`spreadId`)")
        }
    }

    /** 0.5.0 began keeping a copy of the spread's positions with each reading. */
    val FROM_3_TO_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `readings` ADD COLUMN `spreadPositionsJson` TEXT")
        }
    }

    val ALL = arrayOf(FROM_2_TO_3, FROM_3_TO_4)
}
