package com.arny.mobilecinema.presentation.tv.home

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.arny.mobilecinema.R

class TvConfirmDialogFragment : DialogFragment() {

    private var title: String = ""
    private var message: String = ""
    private var btnOkText: String = ""
    private var btnCancelText: String = ""
    private var requestKey: String = REQUEST_KEY
    private var resultKey: String = KEY_CONFIRMED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = arguments?.getString(ARG_TITLE) ?: ""
        message = arguments?.getString(ARG_MESSAGE) ?: ""
        btnOkText = arguments?.getString(ARG_BTN_OK) ?: getString(android.R.string.ok)
        btnCancelText = arguments?.getString(ARG_BTN_CANCEL) ?: getString(android.R.string.cancel)
        requestKey = arguments?.getString(ARG_REQUEST_KEY) ?: REQUEST_KEY
        resultKey = arguments?.getString(ARG_RESULT_KEY) ?: KEY_CONFIRMED
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(btnOkText) { _, _ ->
                setFragmentResult(requestKey, bundleOf(resultKey to true))
                dismissAllowingStateLoss()
            }
            .setNegativeButton(btnCancelText) { _, _ ->
                dismissAllowingStateLoss()
            }
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
                }
            }
    }

    companion object {
        const val TAG = "TvConfirmDialog"
        const val REQUEST_KEY = "CONFIRM_REQUEST"
        const val KEY_CONFIRMED = "CONFIRMED"

        private const val ARG_TITLE = "TITLE"
        private const val ARG_MESSAGE = "MESSAGE"
        private const val ARG_BTN_OK = "BTN_OK"
        private const val ARG_BTN_CANCEL = "BTN_CANCEL"
        private const val ARG_REQUEST_KEY = "REQUEST_KEY"
        private const val ARG_RESULT_KEY = "RESULT_KEY"

        fun newInstance(
            title: String,
            message: String,
            btnOkText: String,
            btnCancelText: String,
            requestKey: String = REQUEST_KEY,
            resultKey: String = KEY_CONFIRMED
        ): TvConfirmDialogFragment {
            return TvConfirmDialogFragment().apply {
                arguments = bundleOf(
                    ARG_TITLE to title,
                    ARG_MESSAGE to message,
                    ARG_BTN_OK to btnOkText,
                    ARG_BTN_CANCEL to btnCancelText,
                    ARG_REQUEST_KEY to requestKey,
                    ARG_RESULT_KEY to resultKey
                )
            }
        }
    }
}