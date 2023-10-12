package com.arny.mobilecinema.presentation.uimodels

import com.arny.mobilecinema.presentation.utils.strings.IWrappedString

sealed class AlertType {
    data class Update(val force: Boolean) : AlertType()
    data class Download(
        val complete: Boolean = false,
        val empty: Boolean = false,
        val equalsLinks: Boolean = false,
        val equalsTitle: Boolean = false
    ) : AlertType()
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