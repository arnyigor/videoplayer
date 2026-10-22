package com.arny.mobilecinema.presentation.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.utils.printTime
import com.arny.mobilecinema.databinding.IHistoryRowBinding
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.domain.playback.PlaybackProgress
import com.arny.mobilecinema.presentation.utils.diffItemCallback
import com.arny.mobilecinema.presentation.utils.getWithDomain
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import org.joda.time.DateTime

/**
 * Список истории как «продолжение просмотра», а не каталог (HIST-01, план v3 §8).
 *
 * Прогресс приходит отдельным потоком (карта `movieDbId -> PlaybackProgress`), поэтому
 * каждая строка получает серию/остаток/дату без дополнительных запросов к базе.
 */
class HistoryRowAdapter(
    private val baseUrl: String,
    private val thumbWidthPx: Int,
    private val progressByMovie: () -> Map<Long, PlaybackProgress>,
    private val onOpenDetails: (ViewMovie) -> Unit,
    private val onResume: (ViewMovie) -> Unit,
) : PagingDataAdapter<ViewMovie, HistoryRowAdapter.HistoryViewHolder>(
    diffItemCallback(
        itemsTheSame = { m1, m2 -> m1.dbId == m2.dbId },
        contentsTheSame = { m1, m2 -> m1 == m2 }
    )
) {

    /** Вызывается, когда приехал новый прогресс: перерисовываем уже показанные строки. */
    fun refreshProgress() {
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder =
        HistoryViewHolder(
            IHistoryRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    class HistoryViewHolder(val binding: IHistoryRowBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = getItem(position) ?: return
        val row = HistoryRowMapper.map(item, progressByMovie()[item.dbId])
        val context = holder.binding.root.context

        holder.binding.apply {
            root.setOnClickListener { onOpenDetails(item) }
            tvTitle.text = row.title

            val statusText = when (val res = HistoryRowMapper.statusLabelRes(row)) {
                R.string.mobile_remaining_minutes -> context.getString(
                    res,
                    row.remainingMinutes ?: 0
                )

                else -> context.getString(res)
            }
            tvMeta.text = listOfNotNull(
                row.episodeLabel ?: row.year.takeIf { it > 0 }?.toString(),
                statusText
            ).joinToString(" · ")

            tvLastWatched.isVisible = row.lastWatchedAt > 0L
            if (row.lastWatchedAt > 0L) {
                tvLastWatched.text = context.getString(
                    R.string.history_last_watched,
                    DateTime(row.lastWatchedAt).printTime("dd.MM.yy")
                )
            }

            val fraction = row.progressFraction
            pbProgress.isVisible = fraction != null
            if (fraction != null) {
                pbProgress.progress = (fraction * 100).toInt().coerceIn(0, 100)
            }

            // Полоса «Продолжить» есть только когда действительно есть что продолжать.
            val canResume = row.state != HistoryProgressState.NOT_STARTED
            ivResume.isVisible = canResume
            ivResume.setOnClickListener { if (canResume) onResume(item) }

            loadThumb(ivPoster, item.img, baseUrl, thumbWidthPx)
        }
    }

    private fun loadThumb(
        view: android.widget.ImageView,
        path: String,
        baseUrl: String,
        widthPx: Int,
    ) {
        val request = Glide.with(view)
            .load(path.getWithDomain(baseUrl))
            .placeholder(R.drawable.placeholder_movie)
            .error(R.drawable.placeholder_movie)
            .transition(DrawableTransitionOptions.withCrossFade(200))
            .centerCrop()
        if (widthPx > 0) {
            // Превью 16:9 в строке: просим декодировать ровно этот размер.
            request.override(widthPx, widthPx * 9 / 16)
        }
        request.into(view)
    }
}
