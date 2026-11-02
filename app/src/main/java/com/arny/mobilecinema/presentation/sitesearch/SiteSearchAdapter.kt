package com.arny.mobilecinema.presentation.sitesearch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.ISearchSectionHeaderBinding
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
 *
 * S-013: в режиме «Везде» список может содержать заголовки секций — каталог отфильтрован,
 * сайт нет. Без этого экран создавал ложное впечатление одинаковой фильтрации.
 */
class SiteSearchAdapter(
    /** База сайта: локальные постеры хранятся относительными путями. */
    private val imageBaseUrl: String = "",
    private val onItemClick: (SearchHit) -> Unit,
) : ListAdapter<SearchListItem, RecyclerView.ViewHolder>(
    object : DiffUtil.ItemCallback<SearchListItem>() {
        override fun areItemsTheSame(oldItem: SearchListItem, newItem: SearchListItem): Boolean =
            when {
                oldItem is SearchListItem.Header && newItem is SearchListItem.Header ->
                    oldItem.type == newItem.type

                oldItem is SearchListItem.Hit && newItem is SearchListItem.Hit ->
                    oldItem.hit.stableId != null && oldItem.hit.stableId == newItem.hit.stableId ||
                            oldItem.hit.siteRef.isNotBlank() &&
                            oldItem.hit.siteRef == newItem.hit.siteRef

                else -> false
            }

        override fun areContentsTheSame(oldItem: SearchListItem, newItem: SearchListItem): Boolean =
            oldItem == newItem
    }
) {

    companion object {
        private const val TYPE_HIT = 0
        private const val TYPE_HEADER = 1
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is SearchListItem.Header -> TYPE_HEADER
        else -> TYPE_HIT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_HEADER) {
            HeaderViewHolder(
                ISearchSectionHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        } else {
            SiteSearchViewHolder(
                ISiteSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SearchListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is SearchListItem.Hit -> (holder as SiteSearchViewHolder).bind(item.hit)
            null -> Unit
        }
    }

    private inner class HeaderViewHolder(val binding: ISearchSectionHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SearchListItem.Header) {
            binding.tvSectionTitle.setText(
                when (item.type) {
                    SearchListItem.Header.Type.CATALOG_FILTERED -> R.string.search_section_catalog
                    SearchListItem.Header.Type.SITE_UNFILTERED -> R.string.search_section_site
                }
            )
        }
    }

    inner class SiteSearchViewHolder(val binding: ISiteSearchBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SearchHit) {
            binding.apply {
                root.setOnClickListener { onItemClick(item) }
                root.contentDescription = item.title
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
            binding.ivSiteSearchIcon.loadMoviePoster(item.posterUrl, imageBaseUrl)
        }
    }
}
