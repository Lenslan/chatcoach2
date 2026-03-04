package com.example.chatcoach

import android.app.Application
import com.example.chatcoach.data.db.AppDatabase
import com.example.chatcoach.data.preferences.AppPreferences

class ChatCoachApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val preferences: AppPreferences by lazy { AppPreferences(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: ChatCoachApp
            private set
    }
}
