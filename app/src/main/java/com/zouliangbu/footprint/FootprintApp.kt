package com.zouliangbu.footprint

import android.app.Application
import com.zouliangbu.footprint.data.db.AppDatabase
import com.zouliangbu.footprint.data.repo.TrackRepository

class FootprintApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: TrackRepository by lazy {
        TrackRepository(database.sessionDao(), database.pointDao())
    }
}