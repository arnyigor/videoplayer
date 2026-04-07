package com.arny.mobilecinema.presentation.tv.update

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.arny.mobilecinema.R
import timber.log.Timber

class TvUpdateDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext(), R.style.TvDialogTheme)
            .setTitle(R.string.update_available_title)
            .setMessage(R.string.update_available_message)
            .setPositiveButton(R.string.yes) { _, _ ->
                Timber.d("User confirmed update")
                setFragmentResult(REQUEST_KEY, bundleOf(KEY_START_UPDATE to true))
                dismissAllowingStateLoss()
            }
            .setNegativeButton(R.string.no) { _, _ ->
                Timber.d("User declined update")
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
        const val TAG = "TvUpdateDialog"
        const val REQUEST_KEY = "UPDATE_REQUEST"
        const val KEY_START_UPDATE = "START_UPDATE"
        const val KEY_STOP_UPDATE = "STOP_UPDATE"
        fun newInstance() = TvUpdateDialogFragment()
    }
}