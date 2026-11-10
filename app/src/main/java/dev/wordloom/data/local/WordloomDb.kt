package dev.wordloom.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CachedWordEntity::class, CardEntity::class, WordListEntity::class, ListWordEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class WordloomDb : RoomDatabase() {
    abstract fun dao(): WordloomDao

    companion object {
        fun build(context: Context): WordloomDb =
            Room.databaseBuilder(context.applicationContext, WordloomDb::class.java, "wordloom.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_words ADD COLUMN recentAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS lists (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS list_words (" +
                        "listId INTEGER NOT NULL, word TEXT NOT NULL, addedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(listId, word))"
                )
            }
        }
    }
}
