package com.arny.mobilecinema.presentation.tv.update

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.arny.mobilecinema.R

class TvUpdateDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext(), R.style.TvDialogTheme)
            .setTitle(R.string.update_available_title)
            .setMessage(R.string.update_available_message)
            .setPositiveButton(R.string.yes) { _, _ ->

            }
            .setNegativeButton(R.string.no) { _, _ ->

            }
            .create().apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
                }
            }
    }

    companion object {
        const val TAG = "TvUpdateDialog"
        fun newInstance() = TvUpdateDialogFragment()
    }
}
