package com.arny.mobilecinema.presentation.di

import com.arny.mobilecinema.presentation.player.PlayerSource
import dagger.Module
import dagger.Provides

@Module
interface PlayerFragmentDependencies {
    companion object {
        @Provides
        fun providePlayerSource() = PlayerSource()
    }
}
