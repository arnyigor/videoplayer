package com.arny.mobilecinema.presentation.uimodels

import com.arny.mobilecinema.presentation.utils.strings.IWrappedString

sealed class AlertType {
    object UpdateAll : AlertType()
    data class Update(val force: Boolean, val hasPartUpdate: Boolean) : AlertType()
    data class ClearCache(
        val url: String,
        val seasonPosition: Int,
        val episodePosition: Int,
        val total: Boolean
    ) : AlertType()

    data class Download(
        val complete: Boolean = false,
        val empty: Boolean = false,
        val equalsLinks: Boolean = false,
        val equalsTitle: Boolean = false,
        val link: String = ""
    ) : AlertType()

    data class DownloadFile(
        val link: String = ""
    ) : AlertType()

    object SimpleAlert: AlertType()
    object UpdateDirect: AlertType()
}

data class Alert(
    val title: IWrappedString,
    val content: IWrappedString? = null,
    val btnOk: IWrappedString? = null,
    val btnCancel: IWrappedString? = null,
    val btnNeutral: IWrappedString? = null,
    val cancelable: Boolean = false,
    val icon: Int? = null,
    val type: AlertType,
)