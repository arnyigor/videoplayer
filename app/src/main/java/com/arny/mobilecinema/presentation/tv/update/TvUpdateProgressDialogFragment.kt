package com.arny.mobilecinema.presentation.tv.update

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.DialogTvUpdateProgressBinding
import timber.log.Timber

class TvUpdateProgressDialogFragment : DialogFragment() {

    interface Callback {
        fun onCancelUpdateRequested()
    }

    companion object {
        const val TAG = "TvUpdateProgressDialog"
        private const val ARG_PROGRESS = "arg_progress"
        private const val ARG_STAGE = "arg_stage"

        fun newInstance(
            progress: Int = -1,
            stage: String? = null,
        ): TvUpdateProgressDialogFragment {
            return TvUpdateProgressDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_PROGRESS, progress)
                    putString(ARG_STAGE, stage)
                }
            }
        }
    }

    private var _binding: DialogTvUpdateProgressBinding? = null
    private val binding get() = _binding!!

    private var cancelRequested = false
    private var isCancelled = false
    private var lastStage: String? = null
    private var lastProgress: Int = Int.MIN_VALUE
    private var lastKnownValidProgress: Int = -1

    @SuppressLint("UseGetLayoutInflater")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogTvUpdateProgressBinding.inflate(
            LayoutInflater.from(requireContext()),
            null,
            false
        )

        val initialProgress = arguments?.getInt(ARG_PROGRESS, -1) ?: -1
        val initialStage = arguments?.getString(ARG_STAGE)

        render(initialProgress, initialStage)

        binding.tvUpdateCancelButton.setOnClickListener {
            if (cancelRequested) return@setOnClickListener

            cancelRequested = true
            binding.tvUpdateCancelButton.isEnabled = false
            binding.tvUpdateCancelButton.isFocusable = false
            binding.tvUpdateStage.text = getString(R.string.cancelling)
            binding.tvUpdateProgressTitle.text = getString(R.string.cancel_update)
            (parentFragment as? Callback)?.onCancelUpdateRequested()
        }

        isCancelable = false

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
            .apply {
                setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                        if (!cancelRequested) {
                            cancelRequested = true
                            binding.tvUpdateCancelButton.isEnabled = false
                            binding.tvUpdateCancelButton.isFocusable = false
                            binding.tvUpdateStage.text = getString(R.string.cancelling)
                            (parentFragment as? Callback)?.onCancelUpdateRequested()
                        }
                        true
                    } else {
                        false
                    }
                }
            }
    }

    fun updateProgress(progress: Int, stage: String? = null) {
        render(progress, stage)
    }

    private fun render(progress: Int, stage: String?) {
        if (isCancelled) return

        Timber.d("render called: progress=$progress, stage=$stage")

        // Разрешаем прогресс: если пришло -1, но был валидный — используем последний валидный
        val resolvedProgress = when {
            progress in 0..100 -> {
                lastKnownValidProgress = progress
                progress
            }

            lastKnownValidProgress in 0..100 -> lastKnownValidProgress
            else -> -1
        }

        // Обновляем Stage только если изменился
        if (stage != lastStage) {
            if (!stage.isNullOrBlank()) {
                binding.tvUpdateStage.text = getString(R.string.updated_format,stage)
                binding.tvUpdateStage.isVisible = true
            } else {
                binding.tvUpdateStage.isVisible = false
            }
            lastStage = stage
        }

        // Обновляем Progress-индикаторы только если изменился прогресс
        if (resolvedProgress != lastProgress) {
            binding.tvUpdateSpinner.isIndeterminate = resolvedProgress !in 0..100
            binding.tvUpdateSpinner.isVisible = true
            lastProgress = resolvedProgress
            Timber.d("render: updated progress UI to $resolvedProgress")
        }
    }

    fun markAsCancelled() {
        isCancelled = true
        binding.tvUpdateSpinner.visibility = android.view.View.GONE
        binding.tvUpdateStage.text = getString(R.string.cancelled)
        binding.tvUpdateCancelButton.isEnabled = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}