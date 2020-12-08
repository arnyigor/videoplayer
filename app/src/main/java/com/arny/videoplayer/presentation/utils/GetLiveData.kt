package com.arny.videoplayer.presentation.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData

fun <T> mutableLiveData(defaultValue: T? = null): MutableLiveData<T> {
    val data = MutableLiveData<T>()

    if (defaultValue != null) {
        data.value = defaultValue
    }

    return data
}

fun <T> mediatorLiveData(vararg liveDataItems: LiveData<*>, predicate: () -> T): MediatorLiveData<T> {
    val mediator = MediatorLiveData<T>()

    liveDataItems.forEach { liveData ->
        mediator.addSource(liveData) {
            mediator.value = predicate()
        }
    }

    mediator.observeForever { }

    return mediator
}