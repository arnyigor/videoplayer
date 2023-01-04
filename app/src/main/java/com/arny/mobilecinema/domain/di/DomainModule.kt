package com.arny.mobilecinema.domain.di

import com.arny.mobilecinema.domain.interactor.MobileCinemaInteractor
import com.arny.mobilecinema.domain.interactor.MobileCinemaInteractorImpl
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
interface DomainModule {
    @Binds
    @Singleton
    fun bindInteractor(impl: MobileCinemaInteractorImpl): MobileCinemaInteractor
}
