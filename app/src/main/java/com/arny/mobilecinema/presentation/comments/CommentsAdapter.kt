package com.arny.mobilecinema.presentation.comments

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.ICommentBinding
import com.arny.mobilecinema.domain.models.MovieComment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter

class CommentsAdapter : ListAdapter<MovieComment, CommentsAdapter.CommentViewHolder>(
    CommentDiffCallback
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder =
        CommentViewHolder(
            ICommentBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun submitItems(comments: List<MovieComment>) {
        submitList(comments)
    }

    class CommentViewHolder(
        private val binding: ICommentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MovieComment) {
            with(binding) {
                val context = root.context
                tvCommentAuthor.text = item.author.ifBlank {
                    context.getString(R.string.movie_comments_unknown_author)
                }
                tvCommentDate.text = item.dateText
                tvCommentDate.isVisible = item.dateText.isNotBlank()
                tvCommentText.text = item.text
            }
        }
    }

    private object CommentDiffCallback : DiffUtil.ItemCallback<MovieComment>() {
        override fun areItemsTheSame(oldItem: MovieComment, newItem: MovieComment): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: MovieComment, newItem: MovieComment): Boolean =
            oldItem == newItem
    }
}