package com.arny.mobilecinema.domain.interactors

import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.network.responses.MoviesData
import com.arny.mobilecinema.domain.repository.GistsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MainInteractorImpl @Inject constructor(
    private val gistsRepository: GistsRepository
) : MainInteractor {
    override fun loadData(): Flow<DataResult<MoviesData>> = gistsRepository.loadData()
}