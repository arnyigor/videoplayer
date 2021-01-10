package com.arny.mobilecinema.presentation.utils

import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.Nullable
import androidx.lifecycle.*
import java.util.concurrent.atomic.AtomicBoolean


fun <T> mutableLiveData(defaultValue: T? = null): MutableLiveData<T> {
    val data = MutableLiveData<T>()

    if (defaultValue != null) {
        data.value = defaultValue
    }

    return data
}

fun <T> mediatorLiveData(
    vararg liveDataItems: LiveData<*>,
    predicate: () -> T
): MediatorLiveData<T> {
    val mediator = MediatorLiveData<T>()

    liveDataItems.forEach { liveData ->
        mediator.addSource(liveData) {
            mediator.value = predicate()
        }
    }

    mediator.observeForever { }

    return mediator
}

class SingleLiveEvent<T> : MutableLiveData<T?>() {
    companion object {
        private const val TAG = "SingleLiveEvent"
    }

    private val mPending = AtomicBoolean(false)

    @MainThread
    override fun observe(owner: LifecycleOwner, observer: Observer<in T?>) {
        if (hasActiveObservers()) {
            Log.w(TAG, "Multiple observers registered but only one will be notified of changes.");
        }
        super.observe(owner, { t ->
            if (mPending.compareAndSet(true, false)) {
                observer.onChanged(t)
            }
        })
    }

    @MainThread
    override fun setValue(@Nullable t: T?) {
        mPending.set(true)
        super.setValue(t)
    }

    @MainThread
    fun call() {
        value = null
    }
}