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

    private var updateTime: String = ""
    private var hasPartUpdate: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateTime = arguments?.getString(ARG_UPDATE_TIME) ?: ""
        hasPartUpdate = arguments?.getBoolean(ARG_HAS_PART_UPDATE) ?: false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val message = if (updateTime.isNotBlank()) {
            getString(R.string.question_update_format, updateTime)
        } else {
            getString(R.string.update_available_message)
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.update_available_title)
            .setMessage(message)
            .setPositiveButton(R.string.yes) { _, _ ->
                Timber.d("User confirmed update")
                setFragmentResult(REQUEST_KEY, bundleOf(KEY_START_UPDATE to true))
                dismissAllowingStateLoss()
            }
            .setNegativeButton(R.string.no) { _, _ ->
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
        const val ARG_UPDATE_TIME = "UPDATE_TIME"
        const val ARG_HAS_PART_UPDATE = "HAS_PART_UPDATE"

        fun newInstance(updateTime: String = "", hasPartUpdate: Boolean = false): TvUpdateDialogFragment {
            return TvUpdateDialogFragment().apply {
                arguments = bundleOf(
                    ARG_UPDATE_TIME to updateTime,
                    ARG_HAS_PART_UPDATE to hasPartUpdate
                )
            }
        }
    }
}