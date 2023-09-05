package com.arny.mobilecinema.presentation.uimodels

import com.arny.mobilecinema.presentation.utils.strings.IWrappedString

sealed class DialogType {
    data class MultiChoose(
        val items: List<IWrappedString>,
    ) : DialogType()
}

data class Dialog(
    val title: IWrappedString,
    val content: IWrappedString? = null,
    val btnOk: IWrappedString? = null,
    val btnCancel: IWrappedString? = null,
    val cancelable: Boolean = false,
    val icon: Int? = null,
    val type: DialogType,
)