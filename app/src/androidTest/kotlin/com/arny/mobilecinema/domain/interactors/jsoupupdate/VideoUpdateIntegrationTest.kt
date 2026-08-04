package com.arny.mobilecinema.domain.interactors.jsoupupdate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.CinemaUrlData
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieInfo
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.repository.JsoupUpdateRepository
import com.arny.mobilecinema.domain.repository.MoviesRepository
import com.arny.mobilecinema.presentation.services.UpdateService
import com.arny.mobilecinema.presentation.utils.sendServiceMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@LargeTest
@RunWith(AndroidJUnit4::class)
class VideoUpdateIntegrationTest {

    @Test(timeout = 120_000)
    fun detailsUpdateServiceByFilmUrlSavesPlayableVideoLinks() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationContext
        val jsoupUpdateRepository: JsoupUpdateRepository = get(JsoupUpdateRepository::class.java)
        val moviesRepository: MoviesRepository = get(MoviesRepository::class.java)
        prepareExistingDetailsMovie(jsoupUpdateRepository)

        val updateStatus = startDetailsStyleUpdateAndWait(context)

        assertEquals(AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS, updateStatus)

        val updatedMovie = moviesRepository.getMovie(FILM_PAGE_URL)
        assertNotNull("Movie was not saved or updated in database: $FILM_PAGE_URL", updatedMovie)
        assertTrue("Movie dbId must be valid", updatedMovie!!.dbId > 0L)
        assertTrue("Movie title must not be blank", updatedMovie.title.isNotBlank())

        val videoUrls = updatedMovie.cinemaUrlData.videoUrls()
        assertTrue("No video URLs were extracted for ${updatedMovie.title}", videoUrls.isNotEmpty())
        assertTrue(
            "Extracted video URLs must be absolute URLs: $videoUrls",
            videoUrls.all { it.startsWith("http://") || it.startsWith("https://") }
        )
    }

    private fun prepareExistingDetailsMovie(repository: JsoupUpdateRepository) {
        val existingMovie = repository.selectMovieByUrl(FILM_PAGE_URL)
        val detailsMovieWithoutVideo = Movie(
            dbId = existingMovie?.dbId ?: 0L,
            movieId = 47737,
            title = existingMovie?.title?.ifBlank { "Project Hail Mary" } ?: "Project Hail Mary",
            type = MovieType.CINEMA,
            pageUrl = FILM_PAGE_URL,
            img = "films/screen/47737.jpg",
            info = MovieInfo(
                year = 2026,
                updated = 0L
            ),
            cinemaUrlData = CinemaUrlData()
        )

        if (existingMovie == null) {
            assertTrue(
                "Failed to seed existing movie for details-style update",
                repository.insertMovie(detailsMovieWithoutVideo)
            )
        } else {
            assertTrue(
                "Failed to reset existing movie for details-style update",
                repository.updateMovie(detailsMovieWithoutVideo, existingMovie.dbId)
            )
        }
    }

    private fun startDetailsStyleUpdateAndWait(context: Context): String {
        val latch = CountDownLatch(1)
        val statusRef = AtomicReference<String>()
        val localBroadcastManager = LocalBroadcastManager.getInstance(context)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val status = intent?.getStringExtra(AppConstants.ACTION_UPDATE_STATUS) ?: return
                when (status) {
                    AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS,
                    AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR,
                    AppConstants.ACTION_UPDATE_STATUS_CANCELLED -> {
                        statusRef.set(status)
                        latch.countDown()
                    }
                }
            }
        }

        localBroadcastManager.registerReceiver(
            receiver,
            IntentFilter(AppConstants.ACTION_UPDATE_STATUS)
        )
        try {
            context.sendServiceMessage(
                Intent(context, UpdateService::class.java),
                AppConstants.ACTION_UPDATE_BY_URL
            ) {
                putString(AppConstants.SERVICE_PARAM_UPDATE_URL, FILM_PAGE_URL)
            }

            assertTrue(
                "UpdateService did not send completion status",
                latch.await(90, TimeUnit.SECONDS)
            )
            return statusRef.get()
        } finally {
            localBroadcastManager.unregisterReceiver(receiver)
            context.stopService(Intent(context, UpdateService::class.java))
        }
    }

    private fun CinemaUrlData?.videoUrls(): List<String> {
        return listOfNotNull(this?.hdUrl, this?.cinemaUrl)
            .flatMap { anwapUrl -> anwapUrl.urls + listOfNotNull(anwapUrl.url, anwapUrl.file) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private companion object {
        const val FILM_PAGE_URL = "films/47737"
    }
}
