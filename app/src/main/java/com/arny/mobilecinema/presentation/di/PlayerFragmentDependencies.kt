package com.arny.mobilecinema.presentation.di

import android.content.Context
import com.arny.mobilecinema.data.api.KtorClient
import com.arny.mobilecinema.data.network.YouTubeVideoInfoRetriever
import com.arny.mobilecinema.presentation.player.PlayerSource
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
interface PlayerFragmentDependencies {
    companion object {
        @Provides
        fun providePlayerSource(context: Context, retriever: YouTubeVideoInfoRetriever) =
            PlayerSource(context, retriever)
    }
}
