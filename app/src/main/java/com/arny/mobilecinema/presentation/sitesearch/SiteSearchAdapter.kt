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
import com.arny.mobilecinema.domain.search.SearchHit
import com.arny.mobilecinema.presentation.home.loadMoviePoster

/**
 * Список результатов единого поиска (SRCH-02).
 *
 * Локальная карточка открывает детали сразу, внешняя — сначала разбирается и сохраняется
 * в базу. Пометка «В каталоге» показывает, что тайтл уже есть локально, поэтому одна и та же
 * запись не выглядит как два разных результата.
 */
class SiteSearchAdapter(
    /** База сайта: локальные постеры хранятся относительными путями. */
    private val imageBaseUrl: String = "",
    private val onItemClick: (SearchHit) -> Unit,
) : ListAdapter<SearchHit, SiteSearchAdapter.SiteSearchViewHolder>(
    object : DiffUtil.ItemCallback<SearchHit>() {
        override fun areItemsTheSame(oldItem: SearchHit, newItem: SearchHit) =
            oldItem.stableId != null && oldItem.stableId == newItem.stableId ||
                    oldItem.siteRef.isNotBlank() && oldItem.siteRef == newItem.siteRef

        override fun areContentsTheSame(oldItem: SearchHit, newItem: SearchHit) =
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
            tvSiteSearchInfo.text = buildList {
                if (item.year > 0) add(item.year.toString())
                add(
                    when (item.kind) {
                        MovieType.SERIAL -> root.context.getString(R.string.serial)
                        MovieType.CINEMA -> root.context.getString(R.string.cinema)
                        else -> ""
                    }
                )
                if (item.rating > 0f) add(String.format("%.1f", item.rating))
                if (item.originalTitle.isNotBlank()) add(item.originalTitle)
            }.filter { it.isNotBlank() }.joinToString(" • ")
            tvSiteSearchBadge.isVisible = item.inCatalog
            tvSiteSearchBadge.setText(R.string.search_in_catalog)
            tvSiteSearchDescription.text = item.description
            tvSiteSearchDescription.isVisible = item.description.isNotBlank()
        }
        // Постер грузим последним: строка успевает показать текст, пока картинка едет.
        holder.binding.ivSiteSearchIcon.loadMoviePoster(item.posterUrl, imageBaseUrl)
    }

    class SiteSearchViewHolder(val binding: ISiteSearchBinding) :
        RecyclerView.ViewHolder(binding.root)
}
