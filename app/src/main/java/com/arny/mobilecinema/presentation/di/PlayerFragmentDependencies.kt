package com.arny.mobilecinema.presentation.di

import android.content.Context
import com.arny.mobilecinema.data.network.YouTubeVideoInfoRetriever
import com.arny.mobilecinema.presentation.player.PlayerSource
import dagger.Module
import dagger.Provides

@Module
interface PlayerFragmentDependencies {
    companion object {
        @Provides
        fun providePlayerSource(context: Context, retriever: YouTubeVideoInfoRetriever) =
            PlayerSource(context, retriever)
    }
}
