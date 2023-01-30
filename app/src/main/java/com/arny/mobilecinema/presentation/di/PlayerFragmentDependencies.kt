package com.arny.mobilecinema.presentation.di

import android.content.Context
import com.arny.mobilecinema.data.api.KtorClient
import com.arny.mobilecinema.presentation.player.PlayerSource
import dagger.Module
import dagger.Provides

@Module
interface PlayerFragmentDependencies {
    companion object {
        @Provides
        fun providePlayerSource(
            context: Context,
            ktorClient: KtorClient
        ) = PlayerSource(ktorClient.client, context)
    }
}
