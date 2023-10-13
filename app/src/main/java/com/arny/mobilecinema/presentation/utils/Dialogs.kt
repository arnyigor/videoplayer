package com.arny.mobilecinema.presentation.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.text.InputType
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.list.listItems
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import com.afollestad.materialdialogs.list.listItemsSingleChoice

fun Fragment.createCustomLayoutDialog(
    title: String,
    @LayoutRes layout: Int,
    cancelable: Boolean = true,
    btnOkText: String? = null,
    btnCancelText: String? = null,
    btnNeutralText: String? = null,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
    onNeutral: () -> Unit = {},
    initView: View.() -> Unit,
): MaterialDialog {
    val dialog = materialDialog(
        context = requireContext(),
        title = title,
        cancelable = cancelable,
        btnOkText = btnOkText,
        autoDismiss = true,
        onConfirm = onConfirm,
        btnCancelText = btnCancelText,
        onCancel = onCancel,
        content = null,
        drawable = null,
        btnNeutralText = btnNeutralText,
        onNeutral = onNeutral,
    )
    dialog.show { customView(layout, scrollable = true) }
    initView(dialog.getCustomView())
    return dialog
}

fun Fragment.listDialog(
    title: String,
    items: List<String>,
    cancelable: Boolean? = false,
    onSelect: (index: Int, text: String) -> Unit
): MaterialDialog {
    val dlg = MaterialDialog(requireContext())
        .title(text = title)
        .cancelable(cancelable ?: false)
        .listItems(items = items) { _, index, text ->
            onSelect(index, text.toString())
        }
    dlg.show()
    return dlg
}

fun Fragment.singleChoiceDialog(
    title: String,
    items: List<String>,
    selectedPosition: Int,
    cancelable: Boolean = false,
    autoDismiss: Boolean = true,
    btnOk: String,
    btnCancel: String,
    onSelect: (index: Int, dlg: MaterialDialog) -> Unit
): MaterialDialog {
    var id = 0
    val dlg = MaterialDialog(requireContext())
        .title(text = title)
        .cancelable(cancelable ?: false)
        .listItemsSingleChoice(
            items = items,
            initialSelection = selectedPosition
        ) { _, index, _ ->
            id = index
        }
    dlg.positiveButton(text = btnOk) {
        if (autoDismiss) {
            it.dismiss()
        }
        onSelect(id, dlg)
    }
    dlg.negativeButton(text = btnCancel) {
        if (autoDismiss) {
            it.dismiss()
        }
        onSelect(id, dlg)
    }
    dlg.show()
    return dlg
}

fun Fragment.multiChoiceDialog(
    title: String,
    btnOk: String,
    btnCancel: String,
    initItems: List<String>,
    selected: IntArray = intArrayOf(),
    cancelable: Boolean? = false,
    onSelect: (indices: IntArray, dlg: MaterialDialog) -> Unit
): MaterialDialog {
    val dlg = MaterialDialog(requireContext())
        .title(text = title)
        .cancelable(cancelable ?: false)
        .listItemsMultiChoice(
            items = initItems,
            initialSelection = selected
        ) { dlg, indices: IntArray, _ ->
            onSelect(indices, dlg)
        }
    dlg.positiveButton(text = btnOk)
    dlg.negativeButton(text = btnCancel)
    dlg.show()
    return dlg
}

fun Fragment.checkDialog(
    title: String? = null,
    items: Array<String>,
    cancelable: Boolean = false,
    dialogListener: (index: Int, text: String) -> Unit?
): MaterialDialog {
    val dlg = MaterialDialog(requireContext())
        .title(text = title.toString())
        .cancelable(cancelable)
        .listItems(items = items.asList()) { _, index, text ->
            dialogListener(index, text.toString())
        }
        .positiveButton(res = android.R.string.ok)
    dlg.show()
    return dlg
}

fun Fragment.alertDialog(
    title: String,
    content: String? = null,
    btnOkText: String? = requireContext().getString(android.R.string.ok),
    btnCancelText: String? = null,
    btnNeutralText: String? = null,
    cancelable: Boolean = false,
    onConfirm: () -> Unit = {},
    onNeutral: () -> Unit = {},
    onCancel: () -> Unit = {},
    autoDismiss: Boolean = true,
    icon: Drawable? = null,
): MaterialDialog = materialDialog(
    context = requireContext(),
    title = title,
    cancelable = cancelable,
    btnOkText = btnOkText,
    autoDismiss = autoDismiss,
    onConfirm = onConfirm,
    btnCancelText = btnCancelText,
    onCancel = onCancel,
    btnNeutralText = btnNeutralText,
    content = content,
    drawable = icon,
    onNeutral = onNeutral
)

private fun materialDialog(
    context: Context,
    title: String,
    cancelable: Boolean,
    btnOkText: String?,
    autoDismiss: Boolean,
    onConfirm: () -> Unit,
    btnCancelText: String?,
    onCancel: () -> Unit,
    btnNeutralText: String?,
    onNeutral: () -> Unit,
    content: String?,
    drawable: Drawable?
): MaterialDialog {
    val materialDialog = MaterialDialog(context)
    materialDialog.title(text = title)
    materialDialog.cancelable(cancelable)
    if (btnOkText != null) {
        materialDialog.positiveButton(text = btnOkText) {
            if (autoDismiss) {
                it.dismiss()
            }
            onConfirm.invoke()
        }
    }
    if (btnCancelText != null) {
        materialDialog.negativeButton(text = btnCancelText) {
            if (autoDismiss) {
                it.dismiss()
            }
            onCancel.invoke()
        }
    }
    if (btnNeutralText != null) {
        materialDialog.neutralButton(text = btnNeutralText) {
            if (autoDismiss) {
                it.dismiss()
            }
            onNeutral.invoke()
        }
    }
    if (!content.isNullOrBlank()) {
        materialDialog.message(text = content)
    }
    if (drawable != null) {
        materialDialog.show {
            icon(drawable = drawable)
        }
    } else {
        materialDialog.show()
    }
    return materialDialog
}

fun Activity.alertDialog(
    title: String,
    content: String? = null,
    btnOkText: String? = getString(android.R.string.ok),
    btnCancelText: String? = null,
    btnNeutralText: String? = null,
    cancelable: Boolean = false,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
    onNeutral: () -> Unit = {},
    autoDismiss: Boolean = true,
    icon: Drawable? = null,
): MaterialDialog = materialDialog(
    context = this,
    title = title,
    cancelable = cancelable,
    btnOkText = btnOkText,
    autoDismiss = autoDismiss,
    onConfirm = onConfirm,
    btnCancelText = btnCancelText,
    onCancel = onCancel,
    content = content,
    drawable = icon,
    btnNeutralText = btnNeutralText,
    onNeutral = onNeutral
)

@SuppressLint("CheckResult")
fun Fragment.inputDialog(
    title: String,
    content: String? = null,
    hint: String? = null,
    prefill: String? = null,
    btnOkText: String = "OK",
    btnCancelText: String? = "Cancel",
    cancelable: Boolean = true,
    type: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS,
    dialogListener: (result: String) -> Unit? = {}
): MaterialDialog {
    return MaterialDialog(requireContext()).show {
        title(text = title)
        if (!content.isNullOrBlank()) {
            message(text = content)
        }
        cancelable(cancelable)
        input(
            hint = hint,
            prefill = prefill,
            inputType = type
        ) { dlg, text ->
            dialogListener(text.toString())
            dlg.dismiss()
        }
        positiveButton(text = btnOkText)
        negativeButton(text = btnCancelText)
    }
}