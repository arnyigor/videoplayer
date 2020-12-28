package com.arny.homecinema

import com.arny.homecinema.di.DaggerAppComponent
import com.facebook.stetho.Stetho
import dagger.android.DaggerApplication

class VideoApp : DaggerApplication() {
    private val applicationInjector = DaggerAppComponent.builder()
        .application(this)
        .build()

    override fun onCreate() {
        super.onCreate()
        Stetho.initializeWithDefaults(this)
    }

    override fun applicationInjector() = applicationInjector
}