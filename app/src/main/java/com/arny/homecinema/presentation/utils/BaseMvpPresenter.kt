package com.arny.homecinema.presentation.utils

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import moxy.MvpPresenter
import moxy.MvpView
import kotlin.coroutines.CoroutineContext

abstract class BaseMvpPresenter<V : MvpView> : MvpPresenter<V>(), CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + SupervisorJob()
    private val compositeDisposable: CompositeDisposable = CompositeDisposable()
    private val compositeJob = CompositeJob()

    fun getScope() = CoroutineScope(coroutineContext)
    fun Job.addTo() {
        compositeJob.add(this)
    }

    fun Disposable.add() = compositeDisposable.add(this)

    private fun onDispose() {
        compositeDisposable.dispose()
        compositeJob.cancel()
    }

    fun <T : Any> Observable<T>.subscribeFromPresenter(
        onNext: (T) -> Unit = {},
        onError: (Throwable) -> Unit = { it.printStackTrace() },
        onComplete: () -> Unit = {},
        scheduler: Scheduler = Schedulers.io(),
        observeOn: Scheduler = AndroidSchedulers.mainThread()
    ) = subscribeOn(scheduler)
        .observeOn(observeOn)
        .subscribe(onNext, onError, onComplete)
        .add()

    fun <T : Any> Single<T>.subscribeFromPresenter(
        onSucces: (T) -> Unit = {},
        onError: (Throwable) -> Unit = { it.printStackTrace() },
        scheduler: Scheduler = Schedulers.io(),
        observeOn: Scheduler = AndroidSchedulers.mainThread()
    ) = subscribeOn(scheduler)
        .observeOn(observeOn)
        .subscribe(onSucces, onError)
        .add()

    fun Completable.subscribeFromPresenter(
        onComplete: () -> Unit = {},
        onError: (Throwable) -> Unit = { it.printStackTrace() },
        scheduler: Scheduler = Schedulers.io(),
        observeOn: Scheduler = AndroidSchedulers.mainThread()
    ) = subscribeOn(scheduler)
        .observeOn(observeOn)
        .subscribe(onComplete, onError)
        .add()

    override fun onDestroy() {
        super.onDestroy()
        onDispose()
    }
}