package com.example.roombackupapplication

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Database(entities = arrayOf(User::class), version = 1, exportSchema = false)
abstract class MyDb : RoomDatabase() {

    abstract fun userDao(): UserDao

    companion object {
        val dbName = "user_db"
        // Singleton prevents multiple instances of database opening at the
        // same time.
        @Volatile
        private var INSTANCE: MyDb? = null

        fun getDatabase(context: Context, scope: CoroutineScope): MyDb {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MyDb::class.java,
                    dbName
                ).build()
                INSTANCE = instance
                return instance
            }
        }
    }

    //Callback
    private class MyDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            INSTANCE?.let { database ->
                scope.launch {
                    populateDatabase(database.userDao())
                }
            }
        }

        suspend fun populateDatabase(userDao: UserDao) {
            // Delete all content here.
            userDao.deleteAll()

            userDao.insertUser(User(0, "sameep@example.com", "password"))

        }
    }
}