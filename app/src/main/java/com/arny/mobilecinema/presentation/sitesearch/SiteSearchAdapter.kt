package com.arny.mobilecinema.presentation.sitesearch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.ISiteSearchBinding
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SiteSearchItem
import com.arny.mobilecinema.presentation.home.loadMoviePoster

/**
 * Список результатов поиска по сайту. Карточки ещё не в БД — показываем только предпросмотр,
 * разбор страницы происходит при клике.
 */
class SiteSearchAdapter(
    private val onItemClick: (SiteSearchItem) -> Unit
) : ListAdapter<SiteSearchItem, SiteSearchAdapter.SiteSearchViewHolder>(
    object : DiffUtil.ItemCallback<SiteSearchItem>() {
        override fun areItemsTheSame(oldItem: SiteSearchItem, newItem: SiteSearchItem) =
            oldItem.pageUrl == newItem.pageUrl

        override fun areContentsTheSame(oldItem: SiteSearchItem, newItem: SiteSearchItem) =
            oldItem == newItem
    }
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SiteSearchViewHolder =
        SiteSearchViewHolder(
            ISiteSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: SiteSearchViewHolder, position: Int) {
        val item = getItem(position) ?: return
        holder.binding.apply {
            root.setOnClickListener { onItemClick(item) }
            tvSiteSearchTitle.text = item.title
            ivSiteSearchIcon.loadMoviePoster(item.img, "")
            tvSiteSearchInfo.text = buildList {
                if (item.year > 0) add(item.year.toString())
                add(
                    when (item.type) {
                        MovieType.SERIAL -> root.context.getString(R.string.serial)
                        MovieType.CINEMA -> root.context.getString(R.string.cinema)
                        else -> ""
                    }
                )
                if (item.rating > 0f) add(String.format("%.1f", item.rating))
            }.filter { it.isNotBlank() }.joinToString(" • ")
            tvSiteSearchDescription.text = item.description
            tvSiteSearchDescription.isVisible = item.description.isNotBlank()
            tvSiteSearchDescription.isVisible = item.description.isNotBlank()
        }
    }

    class SiteSearchViewHolder(val binding: ISiteSearchBinding) :
        RecyclerView.ViewHolder(binding.root)
}
