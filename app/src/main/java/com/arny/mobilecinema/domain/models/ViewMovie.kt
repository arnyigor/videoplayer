package com.arny.mobilecinema.domain.models

data class ViewMovie(
    val dbId: Long = 0,
    val title: String = "",
    val type: Int = 0,        // ← тип как Int (если это enum в Movie)
    val img: String = "",
    val year: Int = 0,
    val likes: Int = 0,
    val dislikes: Int = 0,
) {
    companion object {
        fun fromMovie(movie: Movie): ViewMovie {
            return ViewMovie(
                dbId = movie.dbId,
                title = movie.title,
                type = when (movie.type) {
                    MovieType.CINEMA -> 1
                    MovieType.SERIAL -> 2
                    else -> 0
                },
                img = movie.img,
                year = movie.info.year ?: 0,
                likes = movie.info.likes,
                dislikes = movie.info.dislikes
            )
        }
    }
}
