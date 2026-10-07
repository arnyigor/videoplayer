package com.arny.mobilecinema.presentation.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arny.mobilecinema.databinding.IHomeHighlightsBinding
import com.arny.mobilecinema.domain.models.HomeHighlights
import com.arny.mobilecinema.domain.models.ViewMovie

class HomeHighlightsAdapter(
    private val baseUrl: String,
    private val onItemClick: (item: ViewMovie) -> Unit
) : RecyclerView.Adapter<HomeHighlightsAdapter.HomeHighlightsViewHolder>() {

    private var highlights = HomeHighlights()
    private var showHighlights = true

    fun submitHighlights(highlights: HomeHighlights) {
        val hadItem = itemCount > 0
        this.highlights = highlights
        notifyChanged(hadItem)
    }

    fun setShowHighlights(show: Boolean) {
        val hadItem = itemCount > 0
        showHighlights = show
        notifyChanged(hadItem)
    }

    fun hasHighlights(): Boolean =
        highlights.recent.isNotEmpty() ||
                highlights.bestNow.isNotEmpty() ||
                highlights.forYou.isNotEmpty()

    override fun getItemCount(): Int =
        if (showHighlights && hasHighlights()) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomeHighlightsViewHolder =
        HomeHighlightsViewHolder(
            IHomeHighlightsBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ),
            baseUrl,
            onItemClick
        )

    override fun onBindViewHolder(holder: HomeHighlightsViewHolder, position: Int) {
        holder.bind(highlights)
    }

    private fun notifyChanged(hadItem: Boolean) {
        val hasItem = itemCount > 0
        when {
            hadItem && hasItem -> notifyItemChanged(0)
            hadItem -> notifyItemRemoved(0)
            hasItem -> notifyItemInserted(0)
        }
    }

    class HomeHighlightsViewHolder(
        private val binding: IHomeHighlightsBinding,
        baseUrl: String,
        onItemClick: (item: ViewMovie) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private class HighlightRow(
            val group: android.view.View,
            val adapter: HomeHighlightMoviesAdapter
        )

        private val rows = listOf(
            binding.groupRecent to binding.rvRecent,
            binding.groupBestNow to binding.rvBestNow,
            binding.groupForYou to binding.rvForYou,
        ).map { (group, recyclerView) ->
            HighlightRow(
                group = group,
                adapter = HomeHighlightMoviesAdapter(baseUrl, onItemClick).also {
                    recyclerView.layoutManager =
                        LinearLayoutManager(recyclerView.context, RecyclerView.HORIZONTAL, false)
                    recyclerView.adapter = it
                    recyclerView.setHasFixedSize(true)
                }
            )
        }

        fun bind(highlights: HomeHighlights) {
            listOf(highlights.recent, highlights.bestNow, highlights.forYou)
                .forEachIndexed { index, movies ->
                    rows[index].group.isVisible = movies.isNotEmpty()
                    rows[index].adapter.submitList(movies)
                }
        }
    }
}
