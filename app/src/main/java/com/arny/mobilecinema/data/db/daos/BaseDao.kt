package com.arny.mobilecinema.data.db.daos

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.arny.mobilecinema.domain.models.ViewMovie

interface BaseDao<T> {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(vararg obj: T)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(vararg obj: T): Int

    @Insert
    fun insertAll(objs: List<T>)

    @RawQuery
    suspend fun getMovies(query: SupportSQLiteQuery): List<ViewMovie>
}