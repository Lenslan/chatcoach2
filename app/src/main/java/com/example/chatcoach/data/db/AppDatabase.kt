package com.example.chatcoach.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.chatcoach.data.db.dao.*
import com.example.chatcoach.data.db.entity.*

@Database(
    entities = [
        Friend::class,
        LlmConfig::class,
        QuickTemplate::class,
        ChatMessage::class,
        TokenUsage::class,
        ChatReview::class,
        ContextSummary::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun friendDao(): FriendDao
    abstract fun llmConfigDao(): LlmConfigDao
    abstract fun quickTemplateDao(): QuickTemplateDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun tokenUsageDao(): TokenUsageDao
    abstract fun chatReviewDao(): ChatReviewDao
    abstract fun contextSummaryDao(): ContextSummaryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chatcoach.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
