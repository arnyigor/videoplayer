package com.arny.mobilecinema.presentation.tv.update

import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.presentation.utils.registerLocalReceiver
import com.arny.mobilecinema.presentation.utils.unregisterLocalReceiver
import timber.log.Timber

class TvUpdateDialogFragment : DialogFragment() {

    private var progressDialog: AlertDialog? = null

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.getStringExtra(AppConstants.ACTION_UPDATE_STATUS)
            when (action) {
                AppConstants.ACTION_UPDATE_STATUS_STARTED -> {
                    progressDialog?.setTitle(getString(R.string.updating_all))
                    progressDialog?.setMessage(getString(R.string.update_started))
                }
                AppConstants.ACTION_UPDATE_STATUS_PROGRESS -> {
                    val percent = intent?.getIntExtra("progress_percent", 0) ?: 0
                    progressDialog?.setMessage("Обновление: $percent%")
                }
                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS -> {
                    progressDialog?.dismiss()
                    dismissAllowingStateLoss()
                    setFragmentResult("UPDATE_REQUEST", bundleOf("UPDATE_COMPLETE" to true))
                }
                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR -> {
                    val errorMsg = intent?.getStringExtra("error_message") ?: ""
                    progressDialog?.setMessage(getString(R.string.update_finished_error, errorMsg))
                    progressDialog?.setButton(AlertDialog.BUTTON_POSITIVE, "OK") { _, _ ->
                        progressDialog?.dismiss()
                        dismissAllowingStateLoss()
                    }
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext(), R.style.TvDialogTheme)
            .setTitle(R.string.update_available_title)
            .setMessage(R.string.update_available_message)
            .setPositiveButton(R.string.yes) { _, _ ->
                Timber.d("onCreateDialog: Pressed YES")
                setFragmentResult("UPDATE_REQUEST", bundleOf("START_UPDATE" to true))
                // Показываем прогресс-диалог после запуска обновления
                showProgressDialog()
            }
            .setNegativeButton(R.string.no) { _, _ ->
                Timber.d("onCreateDialog: Pressed NO")
            }
            .create().apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
                }
            }
    }

    private fun showProgressDialog() {
        val builder = AlertDialog.Builder(requireContext(), R.style.TvDialogTheme)
            .setTitle(R.string.updating_all)
            .setMessage(getString(R.string.update_started))
            .setPositiveButton(R.string.stop) { _, _ ->
                setFragmentResult("UPDATE_REQUEST", bundleOf("STOP_UPDATE" to true))
            }
            .setCancelable(false)
        progressDialog = builder.create()
        progressDialog?.show()
    }

    override fun onResume() {
        super.onResume()
        registerLocalReceiver(AppConstants.ACTION_UPDATE_STATUS, updateReceiver)
    }

    override fun onPause() {
        super.onPause()
        unregisterLocalReceiver(updateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        progressDialog?.dismiss()
        progressDialog = null
    }

    companion object {
        const val TAG = "TvUpdateDialog"
        fun newInstance() = TvUpdateDialogFragment()
    }
}
