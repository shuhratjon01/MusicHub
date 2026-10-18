package com.musichub.app

import android.app.Application
import com.musichub.app.di.AppContainer

class MusicHubApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
