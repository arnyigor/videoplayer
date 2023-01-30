package com.arny.mobilecinema.domain.di

import com.arny.mobilecinema.domain.interactors.MainInteractor
import com.arny.mobilecinema.domain.interactors.MainInteractorImpl
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
interface DomainModule {

    @Binds
    @Singleton
    fun bindMainInteractor(impl: MainInteractorImpl): MainInteractor
}
