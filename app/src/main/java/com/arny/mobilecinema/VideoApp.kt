package com.arny.mobilecinema

import android.app.Application
import com.arny.mobilecinema.di.dataModule
import com.arny.mobilecinema.di.domainModule
import com.arny.mobilecinema.di.presentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import timber.log.Timber

class VideoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@VideoApp)
            modules(
                dataModule,
                domainModule,
                presentationModule
            )
        }
        
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
