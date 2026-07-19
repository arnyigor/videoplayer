package com.arny.mobilecinema.domain.interactors.jsoupupdate

import android.content.Context
import androidx.core.os.bundleOf
import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.models.DataResultWithProgress
import com.arny.mobilecinema.data.models.DownloadFileResult
import com.arny.mobilecinema.data.models.FfmpegResult
import com.arny.mobilecinema.domain.models.LoadingData
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.UpdateType
import com.arny.mobilecinema.domain.repository.MoviesRepository
import com.arny.mobilecinema.domain.repository.UpdateRepository
import com.arny.mobilecinema.presentation.MainActivity
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class VenomHdLinkIntegrationTest {

    @Test
    fun forceUpdateOpenDetailsGetHdLinkAndPlayWithExoPlayer() = runBlocking {
        val interactor: JsoupUpdateInteractor = get(JsoupUpdateInteractor::class.java)
        val moviesRepository: MoviesRepository = get(MoviesRepository::class.java)

        var movieProgressSeen = false
        interactor.getPageData(FILM_URL, updateToNow = true, flowCollector = object : FlowCollector<DataResultWithProgress<LoadingData>> {
            override suspend fun emit(value: DataResultWithProgress<LoadingData>) {
                if (value is DataResultWithProgress.Progress && value.result.progress.containsKey(UpdateType.MOVIE)) {
                    movieProgressSeen = true
                }
            }
        })
        assertTrue("Force update did not emit movie progress", movieProgressSeen)

        val updatedMovie = moviesRepository.getMovie(FILM_PAGE_URL)
        assertNotNull("Updated movie was not saved in database", updatedMovie)
        assertTrue("Updated movie dbId must be valid", updatedMovie!!.dbId > 0L)

        openDetailsScreen(updatedMovie.dbId)

        val hdUrl = updatedMovie.cinemaUrlData?.hdUrl?.urls.orEmpty()
            .firstOrNull { it.contains(".m3u8", ignoreCase = true) }
            ?: updatedMovie.cinemaUrlData?.hdUrl?.urls.orEmpty().firstOrNull()
        assertNotNull("HD URL was not extracted", hdUrl)
        assertTrue("HD URL must contain Venom access token: $hdUrl", hdUrl!!.substringAfter('?').split('&').any { it.matches(Regex("[a-f0-9]{8,}")) })

        assertExoPlayerPlaybackSucceeds(hdUrl)
    }

    private fun openDetailsScreen(movieDbId: Long) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val destinationRef = AtomicReference<Int>()
            scenario.onActivity { activity ->
                val navController = activity.findNavController(R.id.nav_host_fragment)
                navController.navigate(R.id.nav_details, bundleOf("id" to movieDbId))
                destinationRef.set(navController.currentDestination?.id)
            }
            assertEquals(R.id.nav_details, destinationRef.get())
        }
    }

    private suspend fun assertExoPlayerPlaybackSucceeds(url: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val playerSource = PlayerSource(context, FakeUpdateRepository())
        val mediaSource = playerSource.getSource(url, "Venom HD integration")
        assertNotNull(mediaSource)

        val latch = CountDownLatch(1)
        val errorRef = AtomicReference<PlaybackException?>()
        val stateRef = AtomicReference<Int>()
        val playerRef = AtomicReference<ExoPlayer>()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val player = ExoPlayer.Builder(context).build()
            playerRef.set(player)
            player.volume = 0f
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    stateRef.set(playbackState)
                    if (playbackState == Player.STATE_READY) latch.countDown()
                }

                override fun onPlayerError(error: PlaybackException) {
                    errorRef.set(error)
                    latch.countDown()
                }
            })
            player.setMediaSource(mediaSource!!)
            player.prepare()
            player.playWhenReady = true
        }

        assertTrue("ExoPlayer did not reach READY", latch.await(45, TimeUnit.SECONDS))
        errorRef.get()?.let { throw AssertionError("ExoPlayer failed: ${it.message}", it) }
        assertEquals(Player.STATE_READY, stateRef.get())

        val positionBefore = AtomicReference<Long>()
        val positionAfter = AtomicReference<Long>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            positionBefore.set(playerRef.get()?.currentPosition ?: 0L)
        }
        Thread.sleep(3_000)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            positionAfter.set(playerRef.get()?.currentPosition ?: 0L)
            playerRef.get()?.release()
        }
        assertTrue(
            "ExoPlayer reached READY but playback position did not advance: ${positionBefore.get()} -> ${positionAfter.get()}",
            positionAfter.get() > positionBefore.get()
        )
    }

    private class FakeUpdateRepository : UpdateRepository {
        override var checkUpdate: Boolean = false
        override var newUpdate: String = ""
        override var updateDownloadId: Long = -1L
        override var lastUpdate: String = ""
        override var baseUrl: String = "https://mm.anwap.media"
        override val newUrlFlow: Flow<String> = emptyFlow()
        override suspend fun onNewUrl(url: String) = Unit
        override suspend fun downloadFile(url: String, name: String): File = error("Not used")
        override fun setLastUpdate() = Unit
        override suspend fun updateMovies(movies: List<Movie>, hasLastYearUpdate: Boolean, forceAll: Boolean, onUpdate: (ind: Int) -> Unit) = Unit
        override suspend fun checkBaseUrl(): Boolean = true
        override suspend fun checkPath(url: String): Boolean = true
        override fun hasLastUpdates(): Boolean = false
        override fun hasMovies(): Boolean = false
        override fun downloadUpdates(url: String, forceUpdate: Boolean) = Unit
        override suspend fun copyFileToDownloadFolder(file: File, fileName: String): Boolean = false
        override suspend fun downloadFileWithProgress(url: String, fileName: String): Flow<DataResultWithProgress<DownloadFileResult>> = emptyFlow()
        override suspend fun removeOldMP4Downloads() = Unit
        override suspend fun downloadLinkWithProgress(url: String, file: File): Flow<DataResultWithProgress<FfmpegResult>> = emptyFlow()
        override fun updateAll() = Unit
        override fun cancelUpdate() = Unit
    }

    private companion object {
        const val FILM_URL = "https://my.anwap.love/films/47737"
        const val FILM_PAGE_URL = "films/47737"
    }
}
