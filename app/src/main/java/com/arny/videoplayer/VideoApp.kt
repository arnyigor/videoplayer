package com.arny.videoplayer

import com.arny.videoplayer.di.DaggerAppComponent
import dagger.android.DaggerApplication

class VideoApp : DaggerApplication() {
    private val applicationInjector = DaggerAppComponent.builder()
        .application(this)
        .build()

    override fun applicationInjector() = applicationInjector
}