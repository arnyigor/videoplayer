package com.arny.mobilecinema

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
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

        logBuildFingerprint()
    }

    private fun logBuildFingerprint() {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Log.i(
            "BuildFingerprint",
            "appId=${BuildConfig.APPLICATION_ID} " +
                "package=$packageName " +
                "version=${packageInfo.versionName}($versionCode) " +
                "debug=${BuildConfig.DEBUG} " +
                "debuggable=$debuggable " +
                "baseLink=${BuildConfig.BASE_LINK} " +
                "sourceDir=${applicationInfo.sourceDir} " +
                "firstInstallTime=${packageInfo.firstInstallTime} " +
                "lastUpdateTime=${packageInfo.lastUpdateTime}"
        )
    }
}
