package com.arny.mobilecinema.data.repository.resources

import androidx.annotation.ArrayRes

interface AppResourcesProvider {
    fun getStringArray(@ArrayRes res: Int): List<String>
}
