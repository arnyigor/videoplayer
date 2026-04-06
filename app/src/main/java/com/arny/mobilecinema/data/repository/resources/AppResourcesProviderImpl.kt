package com.arny.mobilecinema.data.repository.resources

import android.content.Context

class AppResourcesProviderImpl constructor(
    private val context: Context
) : AppResourcesProvider {
    override fun getStringArray(res: Int): List<String> {
        return context.resources.getStringArray(res).toList()
    }
}