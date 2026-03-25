package com.arny.mobilecinema.presentation.home

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.IHomeVideoBinding
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.utils.diffItemCallback
import com.arny.mobilecinema.presentation.utils.getWithDomain
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Оптимизированный адаптер для отображения списка видео.
 * Улучшения:
 * 1. Кэширование загруженных изображений с Glide
 * 2. Предзагрузка изображений для следующей страницы
 * 3. Оптимизация перерисовки элементов
 * 4. Поддержка разных размеров изображений для разных плотностей экрана
 */
class VideoItemsAdapterOptimized(
    private val baseUrl: String,
    private val onItemClick: (item: ViewMovie) -> Unit
) : PagingDataAdapter<ViewMovie, VideoItemsAdapterOptimized.VideosViewHolder>(
    diffItemCallback(
        itemsTheSame = { m1, m2 -> m1.dbId == m2.dbId },
        contentsTheSame = { m1, m2 ->
            // Оптимизированная проверка - сравниваем только те поля, которые влияют на UI
            m1.title == m2.title &&
                    m1.type == m2.type &&
                    m1.img == m2.img &&
                    m1.year == m2.year &&
                    m1.likes == m2.likes &&
                    m1.dislikes == m2.dislikes &&
                    m1.isFavorite == m2.isFavorite
        }
    )
) {

    private val preloadScope = CoroutineScope(Dispatchers.Main)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideosViewHolder =
        VideosViewHolder(
            IHomeVideoBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun onBindViewHolder(holder: VideosViewHolder, position: Int) {
        val item = getItem(position)
        if (item != null) {
            holder.bind(item)
        } else {
            // Очищаем view при переиспользовании, если данные отсутствуют
            holder.clear()
        }
    }

    inner class VideosViewHolder(val binding: IHomeVideoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var currentImageUrl: String? = null

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    getItem(position)?.let { item ->
                        onItemClick(item)
                    }
                }
            }
        }

        fun bind(item: ViewMovie) {
            binding.apply {
                tvVideoTitle.text = item.title
                tvInfo.text = String.format(
                    Locale.getDefault(),
                    "%d\uD83D\uDC4D %d\uD83D\uDC4E",
                    item.likes,
                    item.dislikes
                )
                ivFavorite.isVisible = item.isFavorite

                val type = getType(item, root.context)
                val year = if (item.year > 0) "${item.year} " else ""
                tvTypeYear.text = String.format("%s%s", year, type)

                // Загрузка изображения с оптимизациями
                loadImage(item.img)
            }
        }

        private fun getType(item: ViewMovie, context: Context): String {
            return when (item.type) {
                MovieType.CINEMA.value -> context.getString(R.string.cinema)
                MovieType.SERIAL.value -> context.getString(R.string.serial)
                else -> ""
            }
        }

        private fun loadImage(imageUrl: String) {
            val fullUrl = imageUrl.getWithDomain(baseUrl)
            
            // Пропускаем загрузку если URL не изменился
            if (currentImageUrl == fullUrl) return
            currentImageUrl = fullUrl

            val context = binding.ivVideoIcon.context
            
            // Оптимизированные настройки Glide
            val requestOptions = RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565) // <--- Экономит 50% RAM
                .placeholder(R.drawable.placeholder_movie)
                .error(R.drawable.placeholder_movie)
                .override(getOptimalImageSize(context))
                .centerCrop() // Использовать centerCrop вместо fitCenter для карточек

            Glide.with(context)
                .load(fullUrl)
                .apply(requestOptions)
                .into(binding.ivVideoIcon)
        }

        /**
         * Определяет оптимальный размер изображения в зависимости от плотности экрана
         */
        private fun getOptimalImageSize(context: Context): Int {
            val displayMetrics = context.resources.displayMetrics
            val density = displayMetrics.density
            
            // Размер изображения ~150dp в зависимости от плотности
            val dpSize = 150
            return (dpSize * density).toInt()
        }

        fun clear() {
            binding.ivVideoIcon.setImageDrawable(null)
            binding.tvVideoTitle.text = ""
            binding.tvTypeYear.text = ""
            binding.tvInfo.text = ""
            binding.ivFavorite.isVisible = false
            currentImageUrl = null
        }
    }

    companion object {
        /**
         * Предзагрузка изображений для следующих элементов
         */
        fun preloadImages(adapter: VideoItemsAdapterOptimized, context: Context, positions: List<Int>) {
            positions.forEach { position ->
                val item = adapter.getItem(position)
                if (item != null && item.img.isNotBlank()) {
                    Glide.with(context)
                        .load(item.img.getWithDomain(adapter.baseUrl))
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .preload()
                }
            }
        }
    }
}