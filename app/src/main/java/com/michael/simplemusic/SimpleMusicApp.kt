package com.michael.simplemusic

import android.app.Application
import com.michael.simplemusic.data.AppDatabase

class SimpleMusicApp : Application() {
    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }
}
