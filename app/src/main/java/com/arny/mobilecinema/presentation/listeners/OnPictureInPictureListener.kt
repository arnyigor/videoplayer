package com.arny.mobilecinema.presentation.listeners

interface OnPictureInPictureListener {
    fun isPiPAvailable(): Boolean
    fun enterPiPMode()
    fun onPiPMode(isInPipMode: Boolean)
}