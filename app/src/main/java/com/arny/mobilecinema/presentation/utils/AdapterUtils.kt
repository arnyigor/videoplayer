package com.arny.mobilecinema.presentation.utils

import androidx.recyclerview.widget.DiffUtil

fun <T : Any> diffItemCallback(
    itemsTheSame: (T, T) -> Boolean = { first, second -> first == second },
    contentsTheSame: (T, T) -> Boolean = { first, second -> first == second }
): DiffUtil.ItemCallback<T> =
    object : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T) = itemsTheSame(oldItem, newItem)

        override fun areContentsTheSame(oldItem: T, newItem: T) =
            contentsTheSame(oldItem, newItem)
    }