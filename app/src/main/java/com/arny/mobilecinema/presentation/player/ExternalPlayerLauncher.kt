package com.arny.mobilecinema.presentation.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.arny.mobilecinema.R

/** Only directly reachable network URLs are shared; private files and headers stay in the app. */
class ExternalPlayerLauncher(private val context: Context) {

    fun open(url: String, title: String? = null): Boolean {
        val candidates = externalMimeCandidates(url)
        if (candidates.isEmpty()) return false

        // Точный тип первым, но если такое приложение не объявлено —
        // пробуем video/*, иначе HLS/MPD ложно считались невоспроизводимыми.
        val intent = candidates.asSequence()
            .mapNotNull { mime -> createIntent(url, title, mime) }
            .firstOrNull { it.resolveActivity(context.packageManager) != null }
            ?: return false

        return try {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.open_external_player)))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    companion object {
        fun createIntent(url: String, title: String? = null): Intent? =
            externalVideoMimeType(url)?.let { createIntent(url, title, it) }

        private fun createIntent(url: String, title: String?, mimeType: String): Intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), mimeType)
                if (!title.isNullOrBlank()) putExtra(Intent.EXTRA_TITLE, title)
            }
    }
}
