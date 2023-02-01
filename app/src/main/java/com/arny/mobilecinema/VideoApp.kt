package com.arny.mobilecinema

import com.arny.mobilecinema.di.DaggerAppComponent
import dagger.android.DaggerApplication
import timber.log.Timber

class VideoApp : DaggerApplication() {
    private val applicationInjector = DaggerAppComponent.builder()
        .application(this)
        .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
    override fun applicationInjector() = applicationInjector
}