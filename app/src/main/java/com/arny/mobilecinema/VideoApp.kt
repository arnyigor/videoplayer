package com.arny.mobilecinema

import com.arny.mobilecinema.di.DaggerAppComponent
import dagger.android.DaggerApplication
import ru.ok.tracer.CoreTracerConfiguration
import ru.ok.tracer.HasTracerConfiguration
import ru.ok.tracer.TracerConfiguration
import ru.ok.tracer.crash.report.CrashFreeConfiguration
import ru.ok.tracer.crash.report.CrashReportConfiguration
import ru.ok.tracer.disk.usage.DiskUsageConfiguration
import ru.ok.tracer.heap.dumps.HeapDumpConfiguration
import timber.log.Timber

class VideoApp : DaggerApplication(), HasTracerConfiguration {
    override val tracerConfiguration: List<TracerConfiguration>
        get() = listOf(
            CoreTracerConfiguration.build {
                setDebugUpload(BuildConfig.DEBUG)
            },
            CrashReportConfiguration.build {
                // опции сборщика крэшей
                setEnabled(true)
                setNativeEnabled(true)
                setSendAnr(true)
            },
            CrashFreeConfiguration.build {
                // опции подсчета crash free
                setEnabled(true) // default false
            },
            HeapDumpConfiguration.build {
                // опции сборщика хипдампов при ООМ
                setEnabled(true)
            },
            DiskUsageConfiguration.build {
                // опции анализатора дискового пространства
                setEnabled(true)
                setInterestingSize(3L * 1024 * 1024 * 1024) // 3GB. Default 10GB
                setProbability(100) // ( 1 / 100 ) * 100% = 1%
            },
        )
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