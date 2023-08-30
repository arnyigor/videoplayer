package com.arny.mobilecinema.presentation.extendedsearch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arny.mobilecinema.databinding.ICustomChipChoiseBinding
import com.arny.mobilecinema.presentation.utils.diffItemCallback

class GenresChoiceAdapter(
    private val onItemClick: (position: Int, isChecked: Boolean) -> Unit
) : ListAdapter<GenreUIModel, GenresChoiceAdapter.GenresChoiceViewHolder>(
    diffItemCallback(
        itemsTheSame = { m1, m2 -> m1.id == m2.id },
        contentsTheSame = { m1, m2 -> m1 == m2 }
    )
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GenresChoiceViewHolder =
        GenresChoiceViewHolder(
            ICustomChipChoiseBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun onBindViewHolder(holder: GenresChoiceViewHolder, position: Int) {
        holder.bind()
    }

    inner class GenresChoiceViewHolder(private val binding: ICustomChipChoiseBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            val model: GenreUIModel = getItem(bindingAdapterPosition)
            binding.chipChoise.text = model.title
            binding.chipChoise.isChecked = model.selected
            binding.chipChoise.setOnCheckedChangeListener { _, isChecked ->
                onItemClick(absoluteAdapterPosition, isChecked)
            }
        }
    }
}
